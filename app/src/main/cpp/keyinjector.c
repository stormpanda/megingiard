#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include "cmd_parsers.h"

int main(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("open /dev/uinput"); return 1; }

    // Register EV_KEY capability
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) { perror("UI_SET_EVBIT EV_KEY"); return 1; }
    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) { perror("UI_SET_EVBIT EV_SYN"); return 1; }

    // Register standard keyboard keycodes (1–255: KEY_* range).
    for (int i = 1; i <= 255; i++) {
        ioctl(fd, UI_SET_KEYBIT, i);
    }

    if (setup_uinput_device(fd, BUS_VIRTUAL, 0x1234, 0x5678, "Megingiard Virtual Keyboard") < 0) {
        perror("setup_uinput_device");
        return 1;
    }

    return run_uinput_injector_loop(fd, 32, parse_key_command);
}
