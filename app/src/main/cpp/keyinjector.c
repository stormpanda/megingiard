#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <linux/uinput.h>

static void write_event(int fd, __u16 type, __u16 code, __s32 value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    ssize_t written = write(fd, &ev, sizeof(ev));
    if (written < 0) {
        perror("write uinput event");
        exit(EXIT_FAILURE);
    }
    if ((size_t)written != sizeof(ev)) {
        fprintf(stderr, "short write to uinput: wrote %zd of %zu bytes\n",
                written, sizeof(ev));
        exit(EXIT_FAILURE);
    }
}

int main(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("open /dev/uinput"); return 1; }

    // Register EV_KEY capability
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) { perror("UI_SET_EVBIT EV_KEY"); return 1; }
    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) { perror("UI_SET_EVBIT EV_SYN"); return 1; }

    // Register standard keyboard keycodes (1–255: KEY_* range).
    // Deliberately stopping at 255 to avoid the BTN_* range (256+).  The BTN_TOOL_PEN
    // code (0x140 = 320) and its neighbours cause Android's EventHub to classify this
    // device as EXTERNAL_STYLUS instead of KEYBOARD, which prevents EV_KEY events from
    // being delivered to apps as Android KeyEvent objects.  All keycodes actually used
    // by this app are ≤ 125 (KEY_META_RIGHT), so 255 is a safe upper bound.
    for (int i = 1; i <= 255; i++) {
        // Gaps in the keycode space are silently ignored by the kernel
        ioctl(fd, UI_SET_KEYBIT, i);
    }

    // Create the virtual device
    // BUS_VIRTUAL prevents the AYN Thor firmware (PkDeviceHelper) from treating
    // this uinput device as a physical keyboard, which would trigger its
    // "show pk devices" handler and cause the app to lose focus.
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_VIRTUAL;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x5678;
    strncpy(usetup.name, "Megingiard Virtual Keyboard", UINPUT_MAX_NAME_SIZE - 1);

    if (ioctl(fd, UI_DEV_SETUP, &usetup) < 0) { perror("UI_DEV_SETUP"); return 1; }
    if (ioctl(fd, UI_DEV_CREATE) < 0)         { perror("UI_DEV_CREATE"); return 1; }

    // Signal readiness
    write(STDOUT_FILENO, "R\n", 2);
    fflush(stdout);

    char line[32];
    while (fgets(line, sizeof(line), stdin)) {
        char action[4];
        int  code;
        if (sscanf(line, "%3s %d", action, &code) != 2) continue;
        if (code < 1 || code > 464) continue;

        if (strcmp(action, "KD") == 0) {
            write_event(fd, EV_KEY, (__u16)code, 1);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
        } else if (strcmp(action, "KU") == 0) {
            write_event(fd, EV_KEY, (__u16)code, 0);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
        }
    }

    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
