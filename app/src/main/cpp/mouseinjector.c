/*
 * mouseinjector.c — Megingiard virtual mouse via /dev/uinput
 *
 * Creates a virtual mouse that exposes:
 *   - Buttons : BTN_LEFT, BTN_RIGHT, BTN_MIDDLE
 *   - Relative axes: REL_X, REL_Y, REL_WHEEL
 *
 * Binary protocol (stdin → binary):
 *   MB L D\n   — left button DOWN
 *   MB L U\n   — left button UP
 *   MB R D\n   — right button DOWN
 *   MB R U\n   — right button UP
 *   MB M D\n   — middle button DOWN
 *   MB M U\n   — middle button UP
 *   MM <dx> <dy>\n — relative mouse MOVE (integer pixels, trackpoint)
 *   MW <delta>\n   — scroll WHEEL (positive = up)
 *
 * Readiness signal (binary → stdout):
 *   R\n  — emitted once the uinput device is created and ready
 *
 * On stdin EOF the virtual device is destroyed and the process exits.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <linux/uinput.h>
#include <linux/input.h>

static void write_event(int fd, __u16 type, __u16 code, __s32 value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    write(fd, &ev, sizeof(ev));
}

int main(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("open /dev/uinput"); return 1; }

    /* Register EV_KEY for mouse buttons */
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);

    /* Register EV_REL for relative movement */
    ioctl(fd, UI_SET_EVBIT, EV_REL);
    ioctl(fd, UI_SET_RELBIT, REL_X);
    ioctl(fd, UI_SET_RELBIT, REL_Y);
    ioctl(fd, UI_SET_RELBIT, REL_WHEEL);

    ioctl(fd, UI_SET_EVBIT, EV_SYN);

    /* Create the virtual device */
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x9002;
    strncpy(usetup.name, "Megingiard Virtual Mouse", UINPUT_MAX_NAME_SIZE - 1);

    ioctl(fd, UI_DEV_SETUP, &usetup);
    ioctl(fd, UI_DEV_CREATE);

    /* Signal readiness */
    write(STDOUT_FILENO, "R\n", 2);
    fflush(stdout);

    char line[48];
    while (fgets(line, sizeof(line), stdin)) {
        if (line[0] == 'M' && line[1] == 'B') {
            /* MB <side> <D|U>  — mouse button press/release */
            char side, du;
            if (sscanf(line, "MB %c %c", &side, &du) != 2) continue;

            __u16 btn;
            if      (side == 'L') btn = BTN_LEFT;
            else if (side == 'R') btn = BTN_RIGHT;
            else if (side == 'M') btn = BTN_MIDDLE;
            else continue;

            __s32 val = (du == 'D') ? 1 : 0;
            write_event(fd, EV_KEY, btn, val);
            write_event(fd, EV_SYN, SYN_REPORT, 0);

        } else if (line[0] == 'M' && line[1] == 'M') {
            /* MM <dx> <dy>  — relative pointer movement */
            int dx, dy;
            if (sscanf(line, "MM %d %d", &dx, &dy) != 2) continue;

            if (dx != 0) write_event(fd, EV_REL, REL_X, dx);
            if (dy != 0) write_event(fd, EV_REL, REL_Y, dy);
            if (dx != 0 || dy != 0) write_event(fd, EV_SYN, SYN_REPORT, 0);

        } else if (line[0] == 'M' && line[1] == 'W') {
            /* MW <delta>  — scroll wheel */
            int delta;
            if (sscanf(line, "MW %d", &delta) != 1) continue;

            write_event(fd, EV_REL, REL_WHEEL, delta);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
        }
    }

    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
