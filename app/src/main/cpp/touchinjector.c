#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include "cmd_parsers.h"

static int g_active_slots_mask = 0;
static int parse_touch_wrapper(const char *line, int fd) {
    return parse_touch_command(line, fd, &g_active_slots_mask);
}

int main(int argc, char *argv[]) {
    if (argc < 2) { fprintf(stderr, "usage: touchinjector <device>\n"); return 1; }
    int fd = open(argv[1], O_WRONLY);
    if (fd < 0) { perror("open"); return 1; }

    return run_uinput_injector_loop(fd, 64, parse_touch_wrapper);
}
