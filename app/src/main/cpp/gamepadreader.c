/*
 * gamepadreader.c — reads physical gamepad events from /dev/input/event*
 *
 * Protocol (stdout):
 *   AR <axis_code> <min> <max>\n  axis range announcement
 *   R <node_path>\n               ready signal with selected device node
 *   E KB <code> <value>\n          gamepad button event (value 1=down, 0=up)
 *   E AX <code> <value>\n          absolute axis event
 *   NODEV\n                      no suitable physical gamepad found
 *
 * Usage:
 *   gamepadreader_arm64 [device_path]
 *
 * If device_path is supplied (e.g. /dev/input/event9), the binary opens that
 * node directly without scanning.  If omitted, it falls back to scanning
 * /dev/input/event0..31 looking for the first device that exposes gamepad keys.
 */

#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define EVENT_NODE_COUNT 32
#define PATH_BUFFER_SIZE 64
#define NAME_BUFFER_SIZE 256
#define DISCOVERY_RETRY_COUNT 25
#define DISCOVERY_RETRY_DELAY_US 120000

static void emit_no_permission(const char* path, int err) {
    printf("NOPERM %s %d\n", path, err);
    fflush(stdout);
}

static const __u16 GAMEPAD_KEYS[] = {
    BTN_SOUTH,
    BTN_C,
    BTN_EAST,
    BTN_NORTH,
    BTN_WEST,
    BTN_TL,
    BTN_TR,
    BTN_TL2,
    BTN_TR2,
    BTN_SELECT,
    BTN_START,
    BTN_MODE,
    BTN_THUMBL,
    BTN_THUMBR,
};

static const __u16 GAMEPAD_AXES[] = {
    ABS_X,
    ABS_Y,
    ABS_Z,
    ABS_RZ,
    ABS_HAT0X,
    ABS_HAT0Y,
};

static bool bit_is_set(const unsigned long* bits, int bit) {
    return (bits[bit / (8 * sizeof(unsigned long))] >>
        (bit % (8 * sizeof(unsigned long)))) & 1UL;
}

static bool contains_virtual(const char* name) {
    return strstr(name, "Virtual") != NULL;
}

static bool device_has_gamepad_keys(int fd) {
    unsigned long key_bits[(KEY_MAX / (8 * sizeof(unsigned long))) + 1];
    memset(key_bits, 0, sizeof(key_bits));
    if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_bits)), key_bits) < 0) {
        return false;
    }

    /*
     * Some controllers expose BTN_C/BTN_Z style face buttons instead of
     * BTN_SOUTH, so we accept a broader gamepad capability signature.
     */
    return bit_is_set(key_bits, BTN_GAMEPAD) ||
        bit_is_set(key_bits, BTN_SOUTH) ||
        bit_is_set(key_bits, BTN_C) ||
        (bit_is_set(key_bits, BTN_EAST) &&
            bit_is_set(key_bits, BTN_NORTH) &&
            bit_is_set(key_bits, BTN_WEST));
}

static void emit_axis_ranges(int fd) {
    for (size_t i = 0; i < sizeof(GAMEPAD_AXES) / sizeof(GAMEPAD_AXES[0]); i++) {
        struct input_absinfo abs_info;
        memset(&abs_info, 0, sizeof(abs_info));
        if (ioctl(fd, EVIOCGABS(GAMEPAD_AXES[i]), &abs_info) == 0) {
            printf("AR %u %d %d\n", GAMEPAD_AXES[i], abs_info.minimum, abs_info.maximum);
            fflush(stdout);
        }
    }
}

static bool is_supported_key(__u16 code) {
    for (size_t i = 0; i < sizeof(GAMEPAD_KEYS) / sizeof(GAMEPAD_KEYS[0]); i++) {
        if (GAMEPAD_KEYS[i] == code) {
            return true;
        }
    }
    return false;
}

static bool is_supported_axis(__u16 code) {
    for (size_t i = 0; i < sizeof(GAMEPAD_AXES) / sizeof(GAMEPAD_AXES[0]); i++) {
        if (GAMEPAD_AXES[i] == code) {
            return true;
        }
    }
    return false;
}

static int find_gamepad_once(char* selected_path, size_t selected_path_size) {
    for (int index = 0; index < EVENT_NODE_COUNT; index++) {
        char path[PATH_BUFFER_SIZE];
        snprintf(path, sizeof(path), "/dev/input/event%d", index);
        int fd = open(path, O_RDONLY);
        if (fd < 0) {
            continue;
        }

        char name[NAME_BUFFER_SIZE];
        memset(name, 0, sizeof(name));
        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) {
            close(fd);
            continue;
        }

        if (contains_virtual(name) || !device_has_gamepad_keys(fd)) {
            close(fd);
            continue;
        }

        snprintf(selected_path, selected_path_size, "%s", path);
        return fd;
    }
    return -1;
}

static int find_gamepad(char* selected_path, size_t selected_path_size) {
    for (int attempt = 0; attempt < DISCOVERY_RETRY_COUNT; attempt++) {
        int fd = find_gamepad_once(selected_path, selected_path_size);
        if (fd >= 0) {
            return fd;
        }
        usleep(DISCOVERY_RETRY_DELAY_US);
    }
    return -1;
}

int main(int argc, char* argv[]) {
    char selected_path[PATH_BUFFER_SIZE];
    memset(selected_path, 0, sizeof(selected_path));

    int fd;
    if (argc >= 2 && argv[1] != NULL && argv[1][0] != '\0') {
        /* Device path provided by caller — open directly, skip scan */
        snprintf(selected_path, sizeof(selected_path), "%s", argv[1]);
        fd = open(selected_path, O_RDONLY);
        if (fd < 0) {
            if (errno == EACCES || errno == EPERM) {
                emit_no_permission(selected_path, errno);
            }
            write(STDOUT_FILENO, "NODEV\n", 6);
            return 1;
        }
    } else {
        fd = find_gamepad(selected_path, sizeof(selected_path));
        if (fd < 0) {
            write(STDOUT_FILENO, "NODEV\n", 6);
            return 1;
        }
    }

    emit_axis_ranges(fd);
    printf("R %s\n", selected_path);
    fflush(stdout);

    struct input_event event;
    while (read(fd, &event, sizeof(event)) == (ssize_t)sizeof(event)) {
        if (event.type == EV_KEY && is_supported_key(event.code)) {
            int value = event.value != 0 ? 1 : 0;
            printf("E KB %u %d\n", event.code, value);
            fflush(stdout);
            continue;
        }

        if (event.type == EV_ABS && is_supported_axis(event.code)) {
            printf("E AX %u %d\n", event.code, event.value);
            fflush(stdout);
        }
    }

    close(fd);
    return 0;
}