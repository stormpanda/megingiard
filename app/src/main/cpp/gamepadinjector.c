/*
 * gamepadinjector.c — Megingiard virtual gamepad via /dev/uinput
 *
 * Creates a virtual gamepad that exposes:
 *   - Face buttons  : BTN_SOUTH (A), BTN_EAST (B), BTN_NORTH (Y), BTN_WEST (X)
 *   - Shoulder      : BTN_TL (L1), BTN_TR (R1), BTN_TL2 (L2), BTN_TR2 (R2)
 *   - Thumbsticks   : BTN_THUMBL (L3), BTN_THUMBR (R3)
 *   - System        : BTN_START, BTN_SELECT, BTN_MODE (Guide)
 *   - D-Pad         : ABS_HAT0X (−1 left / +1 right), ABS_HAT0Y (−1 up / +1 down)
 *   - Left stick    : ABS_X, ABS_Y  (range −32768…+32767)
 *   - Right stick   : ABS_Z, ABS_RZ (range −32768…+32767)
 *
 * Binary protocol (stdin → binary):
 *   GD <code>\n            — button DOWN  (code = Linux BTN_* value)
 *   GU <code>\n            — button UP
 *   HD <axis> <value>\n    — hat/D-Pad, axis 0=X 1=Y, value -1/0/+1
 *   JS <axis> <value>\n    — joystick axis (ABS_X=0,ABS_Y=1,ABS_Z=2,ABS_RZ=5), value -32768…+32767
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

/* Button codes registered on this virtual device. */
static const __u16 GAMEPAD_BUTTONS[] = {
    BTN_SOUTH,   /* A  / Cross      */
    BTN_EAST,    /* B  / Circle     */
    BTN_NORTH,   /* Y  / Triangle   */
    BTN_WEST,    /* X  / Square     */
    BTN_TL,      /* L1 / Left shoulder  */
    BTN_TR,      /* R1 / Right shoulder */
    BTN_TL2,     /* L2 / Left trigger   */
    BTN_TR2,     /* R2 / Right trigger  */
    BTN_THUMBL,  /* L3 / Left stick click  */
    BTN_THUMBR,  /* R3 / Right stick click */
    BTN_START,
    BTN_SELECT,
    BTN_MODE,    /* Guide / Home button */
};
#define GAMEPAD_BUTTON_COUNT (sizeof(GAMEPAD_BUTTONS) / sizeof(GAMEPAD_BUTTONS[0]))

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

    /* Register EV_KEY for all gamepad buttons */
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_EVBIT, EV_SYN);
    for (size_t i = 0; i < GAMEPAD_BUTTON_COUNT; i++) {
        ioctl(fd, UI_SET_KEYBIT, GAMEPAD_BUTTONS[i]);
    }

    /* Register EV_ABS for HAT (D-Pad) and analog sticks */
    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    ioctl(fd, UI_SET_ABSBIT, ABS_HAT0X);
    ioctl(fd, UI_SET_ABSBIT, ABS_HAT0Y);
    ioctl(fd, UI_SET_ABSBIT, ABS_X);
    ioctl(fd, UI_SET_ABSBIT, ABS_Y);
    ioctl(fd, UI_SET_ABSBIT, ABS_Z);
    ioctl(fd, UI_SET_ABSBIT, ABS_RZ);

    /* Create the virtual device */
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor  = 0x1234;
    usetup.id.product = 0x9001;
    strncpy(usetup.name, "Megingiard Virtual Gamepad", UINPUT_MAX_NAME_SIZE - 1);

    /* Configure HAT axes: range −1…+1 */
    struct uinput_abs_setup hat_x;
    memset(&hat_x, 0, sizeof(hat_x));
    hat_x.code        = ABS_HAT0X;
    hat_x.absinfo.minimum = -1;
    hat_x.absinfo.maximum =  1;
    ioctl(fd, UI_ABS_SETUP, &hat_x);

    struct uinput_abs_setup hat_y;
    memset(&hat_y, 0, sizeof(hat_y));
    hat_y.code        = ABS_HAT0Y;
    hat_y.absinfo.minimum = -1;
    hat_y.absinfo.maximum =  1;
    ioctl(fd, UI_ABS_SETUP, &hat_y);

    /* Configure analog stick axes: range −32768…+32767 */
    struct uinput_abs_setup stick_x;
    memset(&stick_x, 0, sizeof(stick_x));
    stick_x.code = ABS_X;
    stick_x.absinfo.minimum = -32768;
    stick_x.absinfo.maximum =  32767;
    ioctl(fd, UI_ABS_SETUP, &stick_x);

    struct uinput_abs_setup stick_y;
    memset(&stick_y, 0, sizeof(stick_y));
    stick_y.code = ABS_Y;
    stick_y.absinfo.minimum = -32768;
    stick_y.absinfo.maximum =  32767;
    ioctl(fd, UI_ABS_SETUP, &stick_y);

    struct uinput_abs_setup stick_z;
    memset(&stick_z, 0, sizeof(stick_z));
    stick_z.code = ABS_Z;
    stick_z.absinfo.minimum = -32768;
    stick_z.absinfo.maximum =  32767;
    ioctl(fd, UI_ABS_SETUP, &stick_z);

    struct uinput_abs_setup stick_rz;
    memset(&stick_rz, 0, sizeof(stick_rz));
    stick_rz.code = ABS_RZ;
    stick_rz.absinfo.minimum = -32768;
    stick_rz.absinfo.maximum =  32767;
    ioctl(fd, UI_ABS_SETUP, &stick_rz);

    ioctl(fd, UI_DEV_SETUP, &usetup);
    ioctl(fd, UI_DEV_CREATE);

    /* Signal readiness */
    write(STDOUT_FILENO, "R\n", 2);
    fflush(stdout);

    char line[48];
    while (fgets(line, sizeof(line), stdin)) {
        char action[4];

        if (line[0] == 'G') {
            /* GD / GU <btn_code> */
            int code;
            if (sscanf(line, "%3s %d", action, &code) != 2) continue;
            if (code < BTN_MISC || code > KEY_MAX) continue;

            if (strcmp(action, "GD") == 0) {
                write_event(fd, EV_KEY, (__u16)code, 1);
                write_event(fd, EV_SYN, SYN_REPORT, 0);
            } else if (strcmp(action, "GU") == 0) {
                write_event(fd, EV_KEY, (__u16)code, 0);
                write_event(fd, EV_SYN, SYN_REPORT, 0);
            }
        } else if (line[0] == 'H') {
            /* HD <axis> <value>   — D-Pad hat event */
            int axis, value;
            if (sscanf(line, "%3s %d %d", action, &axis, &value) != 3) continue;
            if (axis < 0 || axis > 1) continue;
            if (value < -1 || value > 1) continue;

            __u16 hat_code = (axis == 0) ? ABS_HAT0X : ABS_HAT0Y;
            write_event(fd, EV_ABS, hat_code, value);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
        } else if (line[0] == 'J') {
            /* JS <axis_code> <value>  — analog joystick axis */
            /* axis_code: ABS_X=0, ABS_Y=1, ABS_Z=2, ABS_RZ=5 */
            int axis_code, value;
            if (sscanf(line, "%3s %d %d", action, &axis_code, &value) != 3) continue;
            /* Accept only the four registered analog stick axes */
            if (axis_code != ABS_X && axis_code != ABS_Y &&
                axis_code != ABS_Z && axis_code != ABS_RZ) continue;
            if (value < -32768 || value > 32767) continue;

            write_event(fd, EV_ABS, (__u16)axis_code, value);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
        }
    }

    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
