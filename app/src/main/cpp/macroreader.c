/*
 * macroreader.c — Megingiard hardware gamepad event reader
 *
 * Reads raw events from a Linux evdev input device (physical gamepad) and
 * forwards relevant EV_KEY and EV_ABS events to stdout so that the Kotlin
 * host can record them as a MacroPad macro.
 *
 * Usage:
 *   macroreader_arm64 <device_path>
 *   e.g. macroreader_arm64 /dev/input/event9
 *
 * If no device path is given, defaults to /dev/input/event9 (AYN Thor built-in
 * gamepad, "Xbox Wireless Controller", Vendor=0x2020).
 *
 * Output protocol (binary → stdout):
 *   R\n                          — ready (device opened)
 *   K <code> <value> <ts_ms>\n  — EV_KEY event (value 1=down, 0=up,  2=repeat)
 *   A <code> <value> <ts_ms>\n  — EV_ABS event (analogue axes and hat)
 *
 * Filtered codes:
 *   EV_KEY: BTN_GAMEPAD range (304–318) and BTN_DPAD range (544–547)
 *   EV_ABS: ABS_X(0), ABS_Y(1), ABS_Z(2), ABS_RZ(5), ABS_GAS(6),
 *           ABS_BRAKE(7), ABS_HAT0X(16), ABS_HAT0Y(17)
 *
 * The process exits when the device read fails (e.g. device closed) or when
 * the Kotlin host destroys the process. On arm64 Android/Linux, struct
 * input_event is 24 bytes: tv_sec(8) + tv_usec(8) + type(2) + code(2) + value(4).
 */

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <linux/input.h>

/* Returns 1 if a EV_KEY code should be forwarded to the host. */
static int is_gamepad_key(unsigned int code) {
    return (code >= 304 && code <= 318) ||   /* BTN_GAMEPAD … BTN_THUMBR */
           (code >= 544 && code <= 547);      /* BTN_DPAD_UP … BTN_DPAD_RIGHT */
}

/* Returns 1 if an EV_ABS code should be forwarded to the host. */
static int is_gamepad_axis(unsigned int code) {
    return code == 0  ||   /* ABS_X      – left  stick X  */
           code == 1  ||   /* ABS_Y      – left  stick Y  */
           code == 2  ||   /* ABS_Z      – right stick X  */
           code == 5  ||   /* ABS_RZ     – right stick Y  */
           code == 6  ||   /* ABS_GAS    – right trigger  */
           code == 7  ||   /* ABS_BRAKE  – left  trigger  */
           code == 16 ||   /* ABS_HAT0X  – D-pad X        */
           code == 17;     /* ABS_HAT0Y  – D-pad Y        */
}

int main(int argc, char *argv[]) {
    const char *device = (argc >= 2) ? argv[1] : "/dev/input/event9";

    int fd = open(device, O_RDONLY);
    if (fd < 0) {
        perror("macroreader: open device");
        return 1;
    }

    /* Signal readiness to the Kotlin host */
    write(STDOUT_FILENO, "R\n", 2);
    fflush(stdout);

    struct input_event ev;
    while (read(fd, &ev, sizeof(ev)) == (ssize_t)sizeof(ev)) {
        long long ts_ms = (long long)ev.time.tv_sec * 1000LL
                        + (long long)ev.time.tv_usec / 1000LL;

        if (ev.type == EV_KEY && is_gamepad_key(ev.code)) {
            printf("K %u %d %lld\n", (unsigned)ev.code, ev.value, ts_ms);
            fflush(stdout);
        } else if (ev.type == EV_ABS && is_gamepad_axis(ev.code)) {
            printf("A %u %d %lld\n", (unsigned)ev.code, ev.value, ts_ms);
            fflush(stdout);
        }
    }

    close(fd);
    return 0;
}
