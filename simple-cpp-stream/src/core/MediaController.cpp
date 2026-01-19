#include "MediaController.hpp"
#include <iostream>
#include <unistd.h>
#include <fcntl.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/epoll.h>
#include <sys/socket.h>

#define VIDEO_DIR "videos/"

StreamContext MediaController::startStream(int client_fd, HttpRequest& req, std::string fileName, int epoll_fd) {
    StreamContext ctx;
    if (fileName.find("..") != std::string::npos) { sendError(client_fd, 403, "Forbidden"); return ctx; }

    std::string file_path = VIDEO_DIR + fileName;
    int file_fd = open(file_path.c_str(), O_RDONLY);
    if (file_fd < 0) { sendError(client_fd, 404, "File Not Found"); return ctx; }

    struct stat file_stat; fstat(file_fd, &file_stat);
    long file_size = file_stat.st_size;
    long range_start = 0, range_end = file_size - 1, content_length = file_size;
    int status_code = 200;

    std::string range_header = req.getHeader("range");
    if (!range_header.empty()) {
        size_t eq_pos = range_header.find('=');
        size_t dash_pos = range_header.find('-');
        if (eq_pos != std::string::npos && dash_pos != std::string::npos) {
            try {
                range_start = std::stol(range_header.substr(eq_pos + 1, dash_pos - (eq_pos + 1)));
                std::string end_str = range_header.substr(dash_pos + 1);
                if (!end_str.empty()) range_end = std::stol(end_str);

                if (range_end >= file_size) range_end = file_size - 1;
                if (range_start >= file_size) { close(file_fd); sendError(client_fd, 416, "Range Not Satisfiable"); return ctx; }

                status_code = 206; content_length = range_end - range_start + 1;
            } catch(...) { range_start = 0; range_end = file_size - 1; content_length = file_size; status_code = 200; }
        }
    }

    std::string header; header.reserve(512);
    header += "HTTP/1.1 " + std::to_string(status_code) + " " + (status_code == 206 ? "Partial Content" : "OK") + "\r\n";
    header += "Content-Type: video/mp4\r\n";
    header += "Content-Length: " + std::to_string(content_length) + "\r\n";
    header += "Accept-Ranges: bytes\r\n";
    if (status_code == 206) header += "Content-Range: bytes " + std::to_string(range_start) + "-" + std::to_string(range_end) + "/" + std::to_string(file_size) + "\r\n";
    header += "Connection: keep-alive\r\n\r\n";

    size_t total_sent = 0; bool header_failed = false;
    while (total_sent < header.size()) {
        ssize_t sent = send(client_fd, header.c_str() + total_sent, header.size() - total_sent, 0);
        if (sent < 0) { if (errno == EAGAIN || errno == EWOULDBLOCK) { usleep(10); continue; } header_failed = true; break; }
        total_sent += sent;
    }

    if (header_failed) { close(file_fd); return ctx; }
    ctx.file_fd = file_fd; ctx.offset = range_start; ctx.remaining = content_length; ctx.active = true;
    continueStream(client_fd, ctx, epoll_fd);
    return ctx;
}

bool MediaController::continueStream(int client_fd, StreamContext& ctx, int epoll_fd) {
    if (!ctx.active) return true;
    while (ctx.remaining > 0) {
        ssize_t sent = sendfile(client_fd, ctx.file_fd, &ctx.offset, ctx.remaining);
        if (sent > 0) { ctx.remaining -= sent; }
        else if (sent < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                struct epoll_event event{}; event.events = EPOLLIN | EPOLLOUT | EPOLLET;
                event.data.fd = client_fd; epoll_ctl(epoll_fd, EPOLL_CTL_MOD, client_fd, &event);
                return true;
            }
            ctx.active = false; close(ctx.file_fd); return false;
        } else break;
    }
    ctx.active = false; close(ctx.file_fd);
    struct epoll_event event{}; event.events = EPOLLIN | EPOLLET;
    event.data.fd = client_fd; epoll_ctl(epoll_fd, EPOLL_CTL_MOD, client_fd, &event);
    return true;
}

void MediaController::sendError(int client_fd, int code, std::string msg) {
    std::string res = "HTTP/1.1 " + std::to_string(code) + " Error\r\nConnection: close\r\n\r\n" + msg;
    send(client_fd, res.c_str(), res.size(), 0);
}