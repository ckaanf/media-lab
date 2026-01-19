#include "HttpServer.hpp"
#include <iostream>
#include <vector>
#include <unistd.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/prctl.h>
#include <signal.h>

#define MAX_EVENTS 2000

// 유틸리티 함수 (내부 사용)
void setNonBlocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

HttpServer::HttpServer(int port) : port(port) {}

void HttpServer::start() {
    setupSocket();
    setupEpoll();
    // 부모 죽으면 자식도 죽게 설정
    prctl(PR_SET_PDEATHSIG, SIGTERM);
    std::cout << "🚀 [Worker " << getpid() << "] Ready" << std::endl;
    eventLoop();
}

void HttpServer::setupSocket() {
    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    int opt = 1;
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEPORT, &opt, sizeof(opt));
    sockaddr_in address{};
    address.sin_family = AF_INET; address.sin_addr.s_addr = INADDR_ANY; address.sin_port = htons(port);
    if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) < 0) exit(1);
    setNonBlocking(server_fd);
    listen(server_fd, SOMAXCONN);
}

void HttpServer::setupEpoll() {
    epoll_fd = epoll_create1(0);
    struct epoll_event event{}; event.events = EPOLLIN | EPOLLET; event.data.fd = server_fd;
    epoll_ctl(epoll_fd, EPOLL_CTL_ADD, server_fd, &event);
}

void HttpServer::eventLoop() {
    std::vector<struct epoll_event> events(MAX_EVENTS);
    while (true) {
        int n = epoll_wait(epoll_fd, events.data(), MAX_EVENTS, -1);
        for (int i = 0; i < n; i++) {
            int fd = events[i].data.fd;
            uint32_t ev = events[i].events;
            if (fd == server_fd) handleConnect();
            else {
                if (ev & EPOLLIN) handleRequest(fd);
                if (ev & EPOLLOUT) {
                    if (stream_contexts.count(fd) && stream_contexts[fd].active) {
                        bool ok = controller.continueStream(fd, stream_contexts[fd], epoll_fd);
                        if (!stream_contexts[fd].active) stream_contexts.erase(fd);
                        if (!ok) cleanUp(fd);
                    }
                }
            }
        }
    }
}

void HttpServer::handleConnect() {
    while (true) {
        sockaddr_in client_addr; socklen_t len = sizeof(client_addr);
        int client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &len);
        if (client_fd < 0) break;
        setNonBlocking(client_fd);
        struct epoll_event event{}; event.events = EPOLLIN | EPOLLET; event.data.fd = client_fd;
        epoll_ctl(epoll_fd, EPOLL_CTL_ADD, client_fd, &event);
        request_buffers[client_fd] = "";
    }
}

void HttpServer::handleRequest(int fd) {
    char buffer[4096]; bool closed = false;
    while (true) {
        int valread = read(fd, buffer, sizeof(buffer));
        if (valread < 0) { if (errno == EAGAIN) break; closed = true; break; }
        else if (valread == 0) { closed = true; break; }
        request_buffers[fd].append(buffer, valread);
    }
    if (closed) { cleanUp(fd); return; }

    std::string& req_data = request_buffers[fd];
    size_t header_end;
    while ((header_end = req_data.find("\r\n\r\n")) != std::string::npos) {
        std::string raw_req = req_data.substr(0, header_end + 4);
        req_data.erase(0, header_end + 4);
        HttpRequest req(raw_req);
        std::string prefix = "/api/v2/stream/v/";
        if (req.path.find(prefix) == 0) {
            std::string fileName = req.path.substr(prefix.length());
            size_t pos = 0; while ((pos = fileName.find("%20", pos)) != std::string::npos) { fileName.replace(pos, 3, " "); pos += 1; }
            if (!fileName.empty()) {
                StreamContext ctx = controller.startStream(fd, req, fileName, epoll_fd);
                if (ctx.active) { stream_contexts[fd] = ctx; break; }
            } else cleanUp(fd);
        } else cleanUp(fd);
    }
}

void HttpServer::cleanUp(int fd) {
    if (stream_contexts.count(fd) && stream_contexts[fd].active) close(stream_contexts[fd].file_fd);
    stream_contexts.erase(fd); request_buffers.erase(fd);
    epoll_ctl(epoll_fd, EPOLL_CTL_DEL, fd, nullptr); close(fd);
}