#pragma once
#include <string>
#include <map>

class HttpRequest {
public:
    std::string method;
    std::string path;
    std::map<std::string, std::string> headers;

    HttpRequest(const std::string& raw);
    std::string getHeader(const std::string& key);

private:
    void parse(const std::string& raw);
    std::string trim(const std::string& str);
};