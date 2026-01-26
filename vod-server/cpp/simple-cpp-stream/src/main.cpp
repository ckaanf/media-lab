#include <iostream>
#include <vector>
#include <unistd.h>
#include <sys/wait.h>
#include <thread>
#include <csignal>
#include <atomic>
#include <cstring>
#include <cerrno>

#include "util/Logger.hpp"
#include "server/HttpServer.hpp"

std::atomic<bool> shutdown_requested(false);
std::vector<pid_t> worker_pids;

void signalHandler(int signum) {
    shutdown_requested.store(true);
}

void cleanupWorkers() {
    LOG_INFO("🛑 [Master] Shutting down... Killing workers...");

    for (pid_t pid : worker_pids) {
        kill(pid, SIGTERM);
    }

    for (pid_t pid : worker_pids) {
        int status;
        waitpid(pid, &status, 0);
    }
    LOG_INFO("✅ [Master] All workers stopped. Bye!");
}

int main() {
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);

    LOG_INFO("🚀 Media Server Master Process Started");

    int total_cores = std::thread::hardware_concurrency();
    if (total_cores == 0) total_cores = 4;

    int num_workers = (total_cores > 2) ? (total_cores - 2) : 1;

    // [변경] std::cout -> LOG_INFO (가변 인자 사용)
    LOG_INFO("🔥 [Master] Forking ", num_workers, " workers...");

    for (int i = 0; i < num_workers; i++) {
        pid_t pid = fork();
        if (pid < 0) {
            LOG_ERROR("fork() failed: ", strerror(errno));
            continue;
        }
        if (pid == 0) {
            HttpServer app(8080);
            app.start();
            return 0;
        } else {
            worker_pids.push_back(pid);
        }
    }

    while (!shutdown_requested.load()) {
        pause();
    }

    cleanupWorkers();

    return 0;
}