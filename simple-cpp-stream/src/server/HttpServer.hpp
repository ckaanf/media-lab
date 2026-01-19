#pragma once
#include <map>
#include <string>
#include "../core/MediaController.hpp"
#include "../core/StreamContext.hpp"

class HttpServer {
public:
    HttpServer(int port);
    void start();

private:
    int port;
    int server_fd = -1;
    int epoll_fd = -1;
    MediaController controller;
    std::map<int, std::string> request_buffers;
    std::map<int, StreamContext> stream_contexts;

    void setupSocket();
    void setupEpoll();
    void eventLoop();
    void handleConnect();
    void handleRequest(int fd);
    void cleanUp(int fd);
};