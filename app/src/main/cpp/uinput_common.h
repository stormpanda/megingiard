#ifndef UINPUT_COMMON_H
#define UINPUT_COMMON_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <linux/uinput.h>
#include <linux/input.h>

/**
 * Writes a Linux struct input_event to the specified file descriptor.
 */
static inline void write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    (void)write(fd, &ev, sizeof(ev));
}

/**
 * Configures and creates a virtual input device on an open /dev/uinput file descriptor.
 * Returns 0 on success, or -1 on ioctl failure.
 */
static inline int setup_uinput_device(int fd, uint16_t bustype, uint16_t vendor, uint16_t product, const char* name) {
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = bustype;
    usetup.id.vendor  = vendor;
    usetup.id.product = product;
    if (name) {
        strncpy(usetup.name, name, UINPUT_MAX_NAME_SIZE - 1);
    }
    if (ioctl(fd, UI_DEV_SETUP, &usetup) < 0) return -1;
    if (ioctl(fd, UI_DEV_CREATE) < 0) return -1;
    return 0;
}

#endif /* UINPUT_COMMON_H */
