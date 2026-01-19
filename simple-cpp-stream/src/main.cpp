#include <iostream>
#include <vector>
#include <unistd.h>
#include <sys/wait.h>
#include <thread>
#include <csignal>
#include "server/HttpServer.hpp"

std::vector<pid_t> worker_pids;

void signalHandler(int signum) {
    std::cout << "\n🛑 [Master] Shutting down... Killing workers..." << std::endl;
    for (pid_t pid : worker_pids) {
        kill(pid, SIGTERM);
    }
    while (wait(nullptr) > 0);
    std::cout << "✅ [Master] All workers stopped. Bye!" << std::endl;
    exit(0);
}

int main() {
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);

    int total_cores = std::thread::hardware_concurrency();
    if (total_cores == 0) total_cores = 4;

    int num_workers = (total_cores > 2) ? (total_cores - 2) : 1;

    std::cout << "🔥 [Master] Forking " << num_workers << " workers..." << std::endl;

    for (int i = 0; i < num_workers - 1; i++) {
        int pid = fork();
        if (pid == 0) {
            HttpServer app(8081);
            app.start();
            return 0;
        } else {
            worker_pids.push_back(pid);
        }
    }

    HttpServer app(8081);
    app.start();

    return 0;
}