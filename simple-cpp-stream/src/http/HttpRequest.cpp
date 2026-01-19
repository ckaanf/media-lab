#include "HttpRequest.hpp"
#include <algorithm>

HttpRequest::HttpRequest(const std::string& raw) {
    parse(raw);
}

std::string HttpRequest::getHeader(const std::string& key) {
    if (headers.count(key)) return headers[key];
    return "";
}

std::string HttpRequest::trim(const std::string& str) {
    if(str.empty()) return "";
    size_t first = str.find_first_not_of(" \t\r\n");
    if (std::string::npos == first) return "";
    size_t last = str.find_last_not_of(" \t\r\n");
    return str.substr(first, (last - first + 1));
}

void HttpRequest::parse(const std::string& raw) {
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