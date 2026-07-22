/*
 * gamepadinjector.c — Megingiard virtual gamepad via /dev/uinput
 */

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include "cmd_parsers.h"

/* Button codes registered on this virtual device. */
static const uint16_t GAMEPAD_BUTTONS[] = {
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

    if (setup_uinput_device(fd, BUS_USB, 0x1234, 0x9001, "Megingiard Virtual Gamepad") < 0) {
        perror("setup_uinput_device");
        return 1;
    }

    return run_uinput_injector_loop(fd, 48, parse_gamepad_command);
}
