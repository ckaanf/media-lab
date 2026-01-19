#pragma once
#include <sys/types.h>

struct StreamContext {
    int file_fd;
    off_t offset;
    size_t remaining;
    bool active;

    StreamContext() : file_fd(-1), offset(0), remaining(0), active(false) {}
};