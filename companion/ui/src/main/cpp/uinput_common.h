#ifndef UINPUT_COMMON_H
#define UINPUT_COMMON_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>

#if defined(__linux__) || defined(__ANDROID__)
#include <linux/uinput.h>
#include <linux/input.h>
#else
// Compatibility declarations for non-Linux host test compilation (e.g. macOS clang host runner)
struct timeval_compat {
    long tv_sec;
    long tv_usec;
};

struct input_event {
    struct timeval_compat time;
    uint16_t type;
    uint16_t code;
    int32_t value;
};

#define EV_SYN 0x00
#define EV_KEY 0x01
#define EV_REL 0x02
#define EV_ABS 0x03
#define SYN_REPORT 0

#define KEY_MAX 0x2ff
#define BTN_MISC 0x100
#define BTN_MOUSE 0x110
#define BTN_LEFT 0x110
#define BTN_RIGHT 0x111
#define BTN_MIDDLE 0x112
#define BTN_SIDE 0x113
#define BTN_EXTRA 0x114

#define BTN_TOUCH 0x14a

#define REL_X 0x00
#define REL_Y 0x01
#define REL_WHEEL 0x08

#define ABS_X 0x00
#define ABS_Y 0x01
#define ABS_Z 0x02
#define ABS_RZ 0x05
#define ABS_HAT0X 0x10
#define ABS_HAT0Y 0x11

#define ABS_MT_SLOT 0x2f
#define ABS_MT_POSITION_X 0x35
#define ABS_MT_POSITION_Y 0x36
#define ABS_MT_TRACKING_ID 0x39

#define UI_DEV_CREATE 1
#define UI_DEV_DESTROY 2
#define UI_DEV_SETUP 3
#define UINPUT_MAX_NAME_SIZE 80
#define BUS_USB 0x03

struct uinput_setup {
    struct {
        uint16_t bustype;
        uint16_t vendor;
        uint16_t product;
        uint16_t version;
    } id;
    char name[UINPUT_MAX_NAME_SIZE];
    uint32_t ff_effects_max;
};
#endif

#ifdef TEST_MOCK_WRITE_EVENT
extern void mock_write_event(uint16_t type, uint16_t code, int32_t value);
#endif

/**
 * Writes a Linux struct input_event to the specified file descriptor.
 */
static inline void write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
#ifdef TEST_MOCK_WRITE_EVENT
    (void)fd;
    mock_write_event(type, code, value);
#else
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    (void)write(fd, &ev, sizeof(ev));
#endif
}

/**
 * Configures and creates a virtual input device on an open /dev/uinput file descriptor.
 * Returns 0 on success, or -1 on ioctl failure.
 */
static inline int setup_uinput_device(int fd, uint16_t bustype, uint16_t vendor, uint16_t product, const char* name) {
#if defined(__linux__) || defined(__ANDROID__)
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
#else
    (void)fd;
    (void)bustype;
    (void)vendor;
    (void)product;
    (void)name;
    return 0;
#endif
}

/**
 * Signals readiness to stdout ("R\n"), reads lines from stdin into a buffer,
 * passes each line to parser(line, fd), and destroys the uinput device on EOF.
 */
static inline int run_uinput_injector_loop(int fd, size_t buf_size, int (*parser)(const char*, int)) {
    if (write(STDOUT_FILENO, "R\n", 2) < 0) {
        // Ignored
    }
    fflush(stdout);

    char *line = (char *)malloc(buf_size);
    if (!line) {
#if defined(__linux__) || defined(__ANDROID__)
        ioctl(fd, UI_DEV_DESTROY);
#endif
        close(fd);
        return 1;
    }

    while (fgets(line, (int)buf_size, stdin)) {
        if (parser) {
            parser(line, fd);
        }
    }

    free(line);
#if defined(__linux__) || defined(__ANDROID__)
    ioctl(fd, UI_DEV_DESTROY);
#endif
    close(fd);
    return 0;
}

#endif /* UINPUT_COMMON_H */
