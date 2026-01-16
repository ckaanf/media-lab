#include <iostream>
#include <vector>
#include <string>
#include <cstring>
#include <sstream>
#include <map>
#include <algorithm>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <sys/epoll.h>
#include <fcntl.h>
#include <sys/sendfile.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <thread>
#include <signal.h>    // ⭐ 추가: 시그널 처리를 위해
#include <sys/prctl.h> // ⭐ 추가: 부모 죽으면 자식도 죽게 설정

#define MAX_EVENTS 2000
#define PORT 8081
#define VIDEO_DIR "videos/" 

// 전역 변수로 자식 프로세스 PID 저장 (종료 시 죽이기 위해)
std::vector<pid_t> worker_pids;

void setNonBlocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    fcntl(fd, F_SETFL, flags | O_NONBLOCK);
}

// ⭐ Ctrl+C (SIGINT) 핸들러
void signalHandler(int signum) {
    std::cout << "\n🛑 [Master] Shutting down... Killing workers..." << std::endl;
    for (pid_t pid : worker_pids) {
        kill(pid, SIGTERM); // 자식들에게 종료 신호 발송
    }
    // 자식들이 죽을 때까지 기다림
    while (wait(NULL) > 0);
    std::cout << "✅ [Master] All workers stopped. Bye!" << std::endl;
    exit(0);
}

struct StreamContext {
    int file_fd;
    off_t offset;
    size_t remaining;
    bool active;
    StreamContext() : file_fd(-1), offset(0), remaining(0), active(false) {}
};

class HttpRequest {
public:
    std::string method, path;
    std::map<std::string, std::string> headers;
    HttpRequest(const std::string& raw) { parse(raw); }
    std::string getHeader(const std::string& key) {
        if (headers.count(key)) return headers[key];
        return "";
    }
private:
    inline std::string trim(const std::string& str) {
        if(str.empty()) return "";
        size_t first = str.find_first_not_of(" \t\r\n");
        if (std::string::npos == first) return "";
        size_t last = str.find_last_not_of(" \t\r\n");
        return str.substr(first, (last - first + 1));
    }
    void parse(const std::string& raw) {
        size_t pos = 0;
        size_t line_end = raw.find('\n', pos);
        if (line_end != std::string::npos) {
            std::string line = raw.substr(pos, line_end - pos);
            if (!line.empty() && line.back() == '\r') line.pop_back();
            size_t method_end = line.find(' ');
            if (method_end != std::string::npos) {
                method = line.substr(0, method_end);
                size_t path_end = line.find(' ', method_end + 1);
                if (path_end != std::string::npos) {
                    path = line.substr(method_end + 1, path_end - (method_end + 1));
                }
            }
            pos = line_end + 1;
        }
        while ((line_end = raw.find('\n', pos)) != std::string::npos) {
            std::string line = raw.substr(pos, line_end - pos);
            pos = line_end + 1;
            if (!line.empty() && line.back() == '\r') line.pop_back();
            if (line.empty()) break;
            size_t colon = line.find(':');
            if (colon != std::string::npos) {
                std::string key = trim(line.substr(0, colon));
                std::transform(key.begin(), key.end(), key.begin(), [](unsigned char c){ return std::tolower(c); });
                std::string value = trim(line.substr(colon + 1));
                headers[key] = value;
            }
        }
    }
};

class MediaController {
public:
    StreamContext startStream(int client_fd, HttpRequest& req, std::string fileName, int epoll_fd) {
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

    bool continueStream(int client_fd, StreamContext& ctx, int epoll_fd) {
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
private:
    void sendError(int client_fd, int code, std::string msg) {
        std::string res = "HTTP/1.1 " + std::to_string(code) + " Error\r\nConnection: close\r\n\r\n" + msg;
        send(client_fd, res.c_str(), res.size(), 0);
    }
};

class HttpServer {
    int server_fd, epoll_fd, port;
    MediaController controller;
    std::map<int, std::string> request_buffers;
    std::map<int, StreamContext> stream_contexts;
public:
    HttpServer(int port) : port(port) {}
    void start() {
        setupSocket(); setupEpoll();
        // ⭐ 자식 프로세스라면, 부모가 죽을 때 같이 죽도록 설정 (Linux 전용)
        prctl(PR_SET_PDEATHSIG, SIGTERM);
        std::cout << "🚀 [Worker " << getpid() << "] Ready" << std::endl;
        eventLoop();
    }
private:
    void setupSocket() {
        server_fd = socket(AF_INET, SOCK_STREAM, 0);
        int opt = 1; setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
        setsockopt(server_fd, SOL_SOCKET, SO_REUSEPORT, &opt, sizeof(opt));
        sockaddr_in address{}; address.sin_family = AF_INET; address.sin_addr.s_addr = INADDR_ANY; address.sin_port = htons(port);
        if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) < 0) exit(1);
        setNonBlocking(server_fd); listen(server_fd, SOMAXCONN);
    }
    void setupEpoll() {
        epoll_fd = epoll_create1(0); struct epoll_event event{}; event.events = EPOLLIN | EPOLLET; event.data.fd = server_fd;
        epoll_ctl(epoll_fd, EPOLL_CTL_ADD, server_fd, &event);
    }
    void eventLoop() {
        std::vector<struct epoll_event> events(MAX_EVENTS);
        while (true) {
            int n = epoll_wait(epoll_fd, events.data(), MAX_EVENTS, -1);
            for (int i = 0; i < n; i++) {
                int fd = events[i].data.fd; uint32_t ev = events[i].events;
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
    void handleConnect() {
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
    void handleRequest(int fd) {
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
    void cleanUp(int fd) {
        if (stream_contexts.count(fd) && stream_contexts[fd].active) close(stream_contexts[fd].file_fd);
        stream_contexts.erase(fd); request_buffers.erase(fd);
        epoll_ctl(epoll_fd, EPOLL_CTL_DEL, fd, nullptr); close(fd);
    }
};

int main() {
    // ⭐ 시그널 핸들러 등록
    signal(SIGINT, signalHandler);  // Ctrl+C
    signal(SIGTERM, signalHandler); // kill

    int total_cores = std::thread::hardware_concurrency();
    if (total_cores == 0) total_cores = 4;
    int num_workers = (total_cores > 2) ? (total_cores - 2) : 1;
    std::cout << "🔥 [Master] Forking " << num_workers << " workers..." << std::endl;
    
    for (int i = 0; i < num_workers - 1; i++) {
        int pid = fork();
        if (pid == 0) { 
            // 자식 프로세스
            HttpServer app(PORT); 
            app.start(); 
            return 0; 
        } else {
            // 부모: 자식 PID 저장
            worker_pids.push_back(pid);
        }
    }
    
    // 부모도 일꾼으로 참여
    HttpServer app(PORT); 
    app.start();
    
    // 이 줄은 사실상 실행되지 않음 (signalHandler가 exit함)
    while (wait(NULL) > 0);
    return 0;
}
