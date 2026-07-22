#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include "cmd_parsers.h"

int main(int argc, char *argv[]) {
    if (argc < 2) { fprintf(stderr, "usage: touchinjector <device>\n"); return 1; }
    int fd = open(argv[1], O_WRONLY);
    if (fd < 0) { perror("open"); return 1; }

    // Signal ready
    write(STDOUT_FILENO, "R\n", 2);
    fflush(stdout);

    char line[64];
    int active_slots_mask = 0;
    while (fgets(line, sizeof(line), stdin)) {
        parse_touch_command(line, fd, &active_slots_mask);
    }
    close(fd);
    return 0;
}
