//
// Created by ckaanf on 1/26/26.
//

#ifndef SIMPLE_CPP_STREAM_LOGGER_HPP
#define SIMPLE_CPP_STREAM_LOGGER_HPP

#include <iostream>
#include <mutex>
#include <string>
#include <chrono>
#include <ctime>
#include <sstream>
#include <iomanip>

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
};

class Logger {
public:
    // cpp singleton
    static Logger& getInstance() {
        static Logger instance;
        return instance;
    }

    Logger(const Logger&) = delete;
    void operator=(const Logger&) = delete;

    template<typename... Args>
    void log(LogLevel level, const char* file, int line, Args... args) {
        // STUDY [RAII 패턴] Lock Guard
        // 이 함수가 시작될 때 lock을 걸고, 함수가 끝나면(}를 만나면) 자동으로 unlock 됨.
        // 예외가 터져도 무조건 unlock 됨 (Deadlock 방지).
        std::lock_guard<std::mutex> lock(mutex_);

        printTimestamp();

        printLevel(level);

        std::cout << "[" << getFileName(file) << ":" << line << "] ";
        ((std::cout << args), ...);

        std::cout << "\n";
    }

private:
    Logger() = default; // 생성자를 숨김
    std::mutex mutex_;  // 스레드 동기화를 위한 자물쇠

    void printTimestamp() {
        auto now = std::chrono::system_clock::now();
        auto time_t_now = std::chrono::system_clock::to_time_t(now);
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()) % 1000;

        std::cout << std::put_time(std::localtime(&time_t_now), "%Y-%m-%d %H:%M:%S");
        std::cout << "." << std::setfill('0') << std::setw(3) << ms.count() << " ";
    }

    void printLevel(LogLevel level) {
        switch (level) {
            case LogLevel::DEBUG: std::cout << "\033[36m[DEBUG]\033[0m "; break; // Cyan
            case LogLevel::INFO:  std::cout << "\033[32m[INFO] \033[0m "; break; // Green
            case LogLevel::WARN:  std::cout << "\033[33m[WARN] \033[0m "; break; // Yellow
            case LogLevel::ERROR: std::cout << "\033[31m[ERROR]\033[0m "; break; // Red
        }
    }

    // /home/user/project/src/main.cpp -> main.cpp 만 추출
    std::string getFileName(const char* path) {
        std::string p = path;
        size_t pos = p.find_last_of("/\\");
        return (pos == std::string::npos) ? p : p.substr(pos + 1);
    }
};

#define LOG_DEBUG(...) Logger::getInstance().log(LogLevel::DEBUG, __FILE__, __LINE__, __VA_ARGS__)
#define LOG_INFO(...)  Logger::getInstance().log(LogLevel::INFO,  __FILE__, __LINE__, __VA_ARGS__)
#define LOG_WARN(...)  Logger::getInstance().log(LogLevel::WARN,  __FILE__, __LINE__, __VA_ARGS__)
#define LOG_ERROR(...) Logger::getInstance().log(LogLevel::ERROR, __FILE__, __LINE__, __VA_ARGS__)

#endif //SIMPLE_CPP_STREAM_LOGGER_HPP