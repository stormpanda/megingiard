#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <linux/input.h>

static void write_event(int fd, __u16 type, __u16 code, __s32 value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    write(fd, &ev, sizeof(ev));
}

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
        char action[8];
        int slot, x, y;
        
        int parsed = sscanf(line, "%7s %d %d %d", action, &slot, &x, &y);
        if (parsed == 4) {
            // Multi-touch protocol format: D/M <slot> <x> <y>
            if (strcmp(action, "D") == 0) {
                write_event(fd, 3, 47, slot);       // ABS_MT_SLOT
                write_event(fd, 3, 57, slot + 1);   // ABS_MT_TRACKING_ID
                write_event(fd, 3, 53, x);          // ABS_MT_POSITION_X
                write_event(fd, 3, 54, y);          // ABS_MT_POSITION_Y
                active_slots_mask |= (1 << slot);
                write_event(fd, 1, 330, 1);         // BTN_TOUCH = 1
                write_event(fd, 0, 0, 0);           // SYN_REPORT
            } else if (strcmp(action, "M") == 0) {
                write_event(fd, 3, 47, slot);       // ABS_MT_SLOT
                write_event(fd, 3, 53, x);          // ABS_MT_POSITION_X
                write_event(fd, 3, 54, y);          // ABS_MT_POSITION_Y
                write_event(fd, 0, 0, 0);           // SYN_REPORT
            }
        } else if (parsed == 3) {
            // Legacy single-touch format: D/M <x> <y> (assumed slot 0)
            int px = slot; // slot variable parsed the second token (x)
            int py = x;    // x variable parsed the third token (y)
            slot = 0;
            if (strcmp(action, "D") == 0) {
                write_event(fd, 3, 47, slot);       // ABS_MT_SLOT
                write_event(fd, 3, 57, slot + 1);   // ABS_MT_TRACKING_ID
                write_event(fd, 3, 53, px);         // ABS_MT_POSITION_X
                write_event(fd, 3, 54, py);         // ABS_MT_POSITION_Y
                active_slots_mask |= (1 << slot);
                write_event(fd, 1, 330, 1);         // BTN_TOUCH = 1
                write_event(fd, 0, 0, 0);           // SYN_REPORT
            } else if (strcmp(action, "M") == 0) {
                write_event(fd, 3, 47, slot);       // ABS_MT_SLOT
                write_event(fd, 3, 53, px);         // ABS_MT_POSITION_X
                write_event(fd, 3, 54, py);         // ABS_MT_POSITION_Y
                write_event(fd, 0, 0, 0);           // SYN_REPORT
            } else if (strcmp(action, "U") == 0) {
                write_event(fd, 3, 47, slot);       // ABS_MT_SLOT
                write_event(fd, 3, 57, -1);         // ABS_MT_TRACKING_ID = -1
                active_slots_mask &= ~(1 << slot);
                write_event(fd, 1, 330, active_slots_mask != 0 ? 1 : 0); // BTN_TOUCH
                write_event(fd, 0, 0, 0);           // SYN_REPORT
            }
        } else if (parsed == 2) {
            // Multi-touch up format: U <slot>
            if (strcmp(action, "U") == 0) {
                write_event(fd, 3, 47, slot);       // ABS_MT_SLOT
                write_event(fd, 3, 57, -1);         // ABS_MT_TRACKING_ID = -1
                active_slots_mask &= ~(1 << slot);
                write_event(fd, 1, 330, active_slots_mask != 0 ? 1 : 0); // BTN_TOUCH
                write_event(fd, 0, 0, 0);           // SYN_REPORT
            }
        }
    }
    close(fd);
    return 0;
}
