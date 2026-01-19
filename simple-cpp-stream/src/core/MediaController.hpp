#pragma once
#include <string>
#include "../http/HttpRequest.hpp"
#include "StreamContext.hpp"

class MediaController {
public:
    StreamContext startStream(int client_fd, HttpRequest& req, std::string fileName, int epoll_fd);
    bool continueStream(int client_fd, StreamContext& ctx, int epoll_fd);

private:
    void sendError(int client_fd, int code, std::string msg);
};