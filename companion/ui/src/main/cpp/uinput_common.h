#ifndef UINPUT_COMMON_H
#define UINPUT_COMMON_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <errno.h>

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
#define REL_HWHEEL 0x06
#define REL_WHEEL 0x08

#define ABS_X 0x00
#define ABS_Y 0x01
#define ABS_Z 0x02
#define ABS_RX 0x03
#define ABS_RY 0x04
#define ABS_RZ 0x05
#define ABS_GAS 0x09
#define ABS_BRAKE 0x0a
#define ABS_HAT0X 0x10
#define ABS_HAT0Y 0x11
#define ABS_HAT2X 0x16
#define ABS_HAT2Y 0x17
#define ABS_MAX 0x3f

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
extern int mock_write_event(uint16_t type, uint16_t code, int32_t value);
#endif

/**
 * Writes a Linux struct input_event to the specified file descriptor.
 * Returns 0 on success, or -1 on write failure (preserving errno).
 */
static inline int write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
#ifdef TEST_MOCK_WRITE_EVENT
    (void)fd;
    return mock_write_event(type, code, value);
#else
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    return (write(fd, &ev, sizeof(ev)) == (ssize_t)sizeof(ev)) ? 0 : -1;
#endif
}

/**
 * Writes a batch of Linux struct input_event records in a single atomic write() syscall.
 * Returns 0 on success, or -1 on write failure (preserving errno).
 */
static inline int write_events_batch(int fd, const struct input_event* evs, int count) {
    if (count <= 0) return 0;
#ifdef TEST_MOCK_WRITE_EVENT
    (void)fd;
    for (int i = 0; i < count; i++) {
        if (mock_write_event(evs[i].type, evs[i].code, evs[i].value) < 0) {
            return -1;
        }
    }
    return 0;
#else
    size_t byte_len = sizeof(struct input_event) * (size_t)count;
    return (write(fd, evs, byte_len) == (ssize_t)byte_len) ? 0 : -1;
#endif
}

static inline void set_input_event(struct input_event* ev, uint16_t type, uint16_t code, int32_t value) {
    memset(ev, 0, sizeof(*ev));
    ev->type  = type;
    ev->code  = code;
    ev->value = value;
}

#define PRIVD_LINE_READER_BUF_SIZE 4096

typedef struct {
    char buf[PRIVD_LINE_READER_BUF_SIZE];
    size_t pos;
    size_t len;
} PrivdLineReader;

static inline void privd_line_reader_init(PrivdLineReader* lr) {
    if (!lr) return;
    lr->pos = 0;
    lr->len = 0;
}

static inline ssize_t privd_line_reader_fill(PrivdLineReader* lr, int fd) {
    lr->pos = 0;
    while (1) {
        ssize_t r = read(fd, lr->buf, sizeof(lr->buf));
        if (r < 0 && errno == EINTR) continue;
        if (r <= 0) {
            lr->len = 0;
            return r;
        }
        lr->len = (size_t)r;
        return r;
    }
}

/**
 * Buffered replacement for 1-byte read_line(): reads newline-terminated ASCII commands
 * into `out` (capacity `max_out`, >= 2) using 4 KB chunked read() syscalls.
 * Returns line length (>= 0) on a newline-terminated line, 0 on clean EOF with no bytes,
 * or -1 on read error. Wait: to preserve exact read_line() return convention where empty
 * lines ("\n") vs EOF are distinguished: returns >0 for non-empty line, 0 on EOF (when no
 * bytes precede EOF), or 0 for empty "\n"? Note: original read_line returned 0 on BOTH EOF
 * and bare "\n". To avoid dropping connection on accidental bare "\n", skip bare "\n" or
 * return -2? No: callers check `n <= 0` for EOF/error and never send empty lines, so
 * returning `n` when `n > 0`, continuing on bare `\n` (ignoring empty keepalive lines), and
 * returning `0` ONLY on true EOF is strictly safer and cleaner!
 */
static inline int privd_line_reader_next(PrivdLineReader* lr, int fd, char* out, int max_out) {
    if (!lr || !out || max_out < 2) return -1;
    while (1) {
        int n = 0;
        int saw_any = 0;
        while (n < max_out - 1) {
            if (lr->pos >= lr->len) {
                ssize_t r = privd_line_reader_fill(lr, fd);
                if (r == 0) {
                    if (!saw_any) return 0;
                    out[n] = '\0';
                    return n;
                }
                if (r < 0) return -1;
            }
            char ch = lr->buf[lr->pos++];
            saw_any = 1;
            if (ch == '\n') {
                out[n] = '\0';
                if (n == 0) {
                    /* Ignore empty blank line rather than mistaking it for EOF. */
                    break;
                }
                return n;
            }
            out[n++] = ch;
        }
        if (n == max_out - 1) {
            /* Drain remainder of overlong line up to '\n' or EOF. */
            while (1) {
                if (lr->pos >= lr->len) {
                    ssize_t r = privd_line_reader_fill(lr, fd);
                    if (r <= 0) break;
                }
                if (lr->buf[lr->pos++] == '\n') break;
            }
            out[n] = '\0';
            return n;
        }
    }
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
