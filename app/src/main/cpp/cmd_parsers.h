#ifndef CMD_PARSERS_H
#define CMD_PARSERS_H

#include "uinput_common.h"

/**
 * Parses and dispatches multi-touch / single-touch commands:
 *   D <slot> <x> <y>  or  D <x> <y>
 *   M <slot> <x> <y>  or  M <x> <y>
 *   U <slot>          or  U
 * Returns 1 if handled, 0 otherwise.
 */
static inline int parse_touch_command(const char* line, int fd, int* active_slots_mask) {
    if (!line || fd < 0) return 0;
    char action[8];
    int slot, x, y;

    int parsed = sscanf(line, "%7s %d %d %d", action, &slot, &x, &y);
    if (parsed == 4) {
        // Multi-touch protocol format: D/M <slot> <x> <y>
        if (strcmp(action, "D") == 0) {
            write_event(fd, EV_ABS, ABS_MT_SLOT, slot);
            write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, slot + 1);
            write_event(fd, EV_ABS, ABS_MT_POSITION_X, x);
            write_event(fd, EV_ABS, ABS_MT_POSITION_Y, y);
            if (active_slots_mask) *active_slots_mask |= (1 << slot);
            write_event(fd, EV_KEY, BTN_TOUCH, 1);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        } else if (strcmp(action, "M") == 0) {
            write_event(fd, EV_ABS, ABS_MT_SLOT, slot);
            write_event(fd, EV_ABS, ABS_MT_POSITION_X, x);
            write_event(fd, EV_ABS, ABS_MT_POSITION_Y, y);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        }
    } else if (parsed == 3) {
        // Single-touch format: D/M <x> <y> (or legacy U with trailing args)
        int px = slot;
        int py = x;
        slot = 0;
        if (strcmp(action, "D") == 0) {
            write_event(fd, EV_ABS, ABS_MT_SLOT, slot);
            write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, slot + 1);
            write_event(fd, EV_ABS, ABS_MT_POSITION_X, px);
            write_event(fd, EV_ABS, ABS_MT_POSITION_Y, py);
            if (active_slots_mask) *active_slots_mask |= (1 << slot);
            write_event(fd, EV_KEY, BTN_TOUCH, 1);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        } else if (strcmp(action, "M") == 0) {
            write_event(fd, EV_ABS, ABS_MT_SLOT, slot);
            write_event(fd, EV_ABS, ABS_MT_POSITION_X, px);
            write_event(fd, EV_ABS, ABS_MT_POSITION_Y, py);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        } else if (strcmp(action, "U") == 0) {
            write_event(fd, EV_ABS, ABS_MT_SLOT, slot);
            write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
            if (active_slots_mask) *active_slots_mask &= ~(1 << slot);
            int is_active = (active_slots_mask && *active_slots_mask != 0) ? 1 : 0;
            write_event(fd, EV_KEY, BTN_TOUCH, is_active);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        }
    } else if (parsed == 2) {
        // Multi-touch UP format: U <slot>
        if (strcmp(action, "U") == 0) {
            write_event(fd, EV_ABS, ABS_MT_SLOT, slot);
            write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
            if (active_slots_mask) *active_slots_mask &= ~(1 << slot);
            int is_active = (active_slots_mask && *active_slots_mask != 0) ? 1 : 0;
            write_event(fd, EV_KEY, BTN_TOUCH, is_active);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        }
    }
    return 0;
}

/**
 * Parses and dispatches virtual mouse commands:
 *   MB <L|R|M|4|5> <D|U>
 *   MM <dx> <dy>
 *   MW <delta>
 * Returns 1 if handled, 0 otherwise.
 */
static inline int parse_mouse_command(const char* line, int fd) {
    if (!line || fd < 0) return 0;
    if (line[0] == 'M' && line[1] == 'B') {
        char side, du;
        if (sscanf(line, "MB %c %c", &side, &du) == 2) {
            uint16_t btn;
            if      (side == 'L') btn = BTN_LEFT;
            else if (side == 'R') btn = BTN_RIGHT;
            else if (side == 'M') btn = BTN_MIDDLE;
            else if (side == '4') btn = BTN_SIDE;
            else if (side == '5') btn = BTN_EXTRA;
            else return 0;

            int32_t val = (du == 'D') ? 1 : 0;
            write_event(fd, EV_KEY, btn, val);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        }
    } else if (line[0] == 'M' && line[1] == 'M') {
        int dx, dy;
        if (sscanf(line, "MM %d %d", &dx, &dy) == 2) {
            if (dx != 0) write_event(fd, EV_REL, REL_X, dx);
            if (dy != 0) write_event(fd, EV_REL, REL_Y, dy);
            if (dx != 0 || dy != 0) write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        }
    } else if (line[0] == 'M' && line[1] == 'W') {
        int delta;
        if (sscanf(line, "MW %d", &delta) == 1) {
            write_event(fd, EV_REL, REL_WHEEL, delta);
            write_event(fd, EV_SYN, SYN_REPORT, 0);
            return 1;
        }
    }
    return 0;
}

/**
 * Parses and dispatches virtual keyboard commands:
 *   KD <keycode>
 *   KU <keycode>
 * Returns 1 if handled, 0 otherwise.
 */
static inline int parse_key_command(const char* line, int fd) {
    if (!line || fd < 0) return 0;
    if (line[0] == 'K') {
        char action[4];
        int code;
        if (sscanf(line, "%3s %d", action, &code) == 2) {
            if (code >= 1 && code <= 464) {
                if (strcmp(action, "KD") == 0) {
                    write_event(fd, EV_KEY, (uint16_t)code, 1);
                    write_event(fd, EV_SYN, SYN_REPORT, 0);
                    return 1;
                } else if (strcmp(action, "KU") == 0) {
                    write_event(fd, EV_KEY, (uint16_t)code, 0);
                    write_event(fd, EV_SYN, SYN_REPORT, 0);
                    return 1;
                }
            }
        }
    }
    return 0;
}

/**
 * Parses and dispatches virtual gamepad commands:
 *   GD <code> / GU <code>
 *   HD <axis> <value>
 *   JS <axis_code> <value>
 * Returns 1 if handled, 0 otherwise.
 */
static inline int parse_gamepad_command(const char* line, int fd) {
    if (!line || fd < 0) return 0;
    char action[4];
    if (line[0] == 'G') {
        int code;
        if (sscanf(line, "%3s %d", action, &code) == 2) {
            if (code >= BTN_MISC && code <= KEY_MAX) {
                if (strcmp(action, "GD") == 0) {
                    write_event(fd, EV_KEY, (uint16_t)code, 1);
                    write_event(fd, EV_SYN, SYN_REPORT, 0);
                    return 1;
                } else if (strcmp(action, "GU") == 0) {
                    write_event(fd, EV_KEY, (uint16_t)code, 0);
                    write_event(fd, EV_SYN, SYN_REPORT, 0);
                    return 1;
                }
            }
        }
    } else if (line[0] == 'H') {
        int axis, value;
        if (sscanf(line, "%3s %d %d", action, &axis, &value) == 3) {
            if (axis >= 0 && axis <= 1 && value >= -1 && value <= 1) {
                uint16_t hat_code = (axis == 0) ? ABS_HAT0X : ABS_HAT0Y;
                write_event(fd, EV_ABS, hat_code, value);
                write_event(fd, EV_SYN, SYN_REPORT, 0);
                return 1;
            }
        }
    } else if (line[0] == 'J') {
        int axis_code, value;
        if (sscanf(line, "%3s %d %d", action, &axis_code, &value) == 3) {
            if ((axis_code == ABS_X || axis_code == ABS_Y || axis_code == ABS_Z || axis_code == ABS_RZ) &&
                value >= -32768 && value <= 32767) {
                write_event(fd, EV_ABS, (uint16_t)axis_code, value);
                write_event(fd, EV_SYN, SYN_REPORT, 0);
                return 1;
            }
        }
    }
    return 0;
}

#endif /* CMD_PARSERS_H */
