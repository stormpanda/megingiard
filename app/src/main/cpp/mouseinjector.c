/*
 * mouseinjector.c — Megingiard virtual mouse via /dev/uinput
 */

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include "cmd_parsers.h"

int main(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("open /dev/uinput"); return 1; }

    /* Register EV_KEY for mouse buttons */
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);
    ioctl(fd, UI_SET_KEYBIT, BTN_SIDE);    /* mouse button 4 */
    ioctl(fd, UI_SET_KEYBIT, BTN_EXTRA);   /* mouse button 5 */

    /* Register EV_REL for relative movement */
    ioctl(fd, UI_SET_EVBIT, EV_REL);
    ioctl(fd, UI_SET_RELBIT, REL_X);
    ioctl(fd, UI_SET_RELBIT, REL_Y);
    ioctl(fd, UI_SET_RELBIT, REL_WHEEL);

    ioctl(fd, UI_SET_EVBIT, EV_SYN);

    if (setup_uinput_device(fd, BUS_USB, 0x1234, 0x9002, "Megingiard Virtual Mouse") < 0) {
        perror("setup_uinput_device");
        return 1;
    }

    /* Signal readiness */
    write(STDOUT_FILENO, "R\n", 2);
    fflush(stdout);

    char line[48];
    while (fgets(line, sizeof(line), stdin)) {
        parse_mouse_command(line, fd);
    }

    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
