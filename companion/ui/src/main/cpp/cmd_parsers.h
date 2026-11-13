#ifndef CMD_PARSERS_H
#define CMD_PARSERS_H

#include "uinput_common.h"

#define MAX_TOUCH_SLOTS 10
#define MAX_DUMPSYS_EVENTHUB_ENTRIES 64
#define MAX_DUMPSYS_DEVICE_HUB_IDS 4

/**
 * Returns 1 if an evdev device name is explicitly excluded from primary
 * touchscreen binding (NULL, empty, virtual "Megingiard*" nodes, or the
 * hardware-verified Retroid Pocket Duo secondary panel "fts_ts_sec").
 *
 * Zero-Heuristics (AGENTS.md §7.5): never guess via speculative substrings
 * (such as "_sec_" which falsely rejects Samsung "sec_ts" touch controllers).
 */
static inline int is_excluded_touch_device_name(const char* devname) {
    if (!devname || devname[0] == '\0') return 1;
    if (strncmp(devname, "Megingiard", 10) == 0 || strncmp(devname, "megingiard", 10) == 0) {
        return 1;
    }
    if (strcmp(devname, "fts_ts_sec") == 0) {
        return 1;
    }
    return 0;
}

/**
 * Returns 1 if an evdev device name matches an exact hardware-verified primary
 * touchscreen driver name ("fts_ts" on AYN Thor and Retroid Pocket Duo), used
 * solely as a deterministic fallback when `dumpsys input` is unavailable.
 */
static inline int is_verified_primary_touch_device_name(const char* devname) {
    if (is_excluded_touch_device_name(devname)) return 0;
    return (strcmp(devname, "fts_ts") == 0) ? 1 : 0;
}

/**
 * Safely computes the hardware axis span (maximum - minimum + 1) in 64-bit
 * signed arithmetic. Returns 0 on invalid, zero/negative, or overflowing bounds.
 */
static inline int compute_evdev_axis_span(int32_t minimum, int32_t maximum) {
    int64_t span = (int64_t)maximum - (int64_t)minimum + 1;
    if (span <= 0 || span > INT32_MAX) {
        return 0;
    }
    return (int)span;
}

/**
 * Parses `dumpsys input` diagnostic output to deterministically resolve the
 * `/dev/input/eventN` path bound by Android's InputReader to Display 0
 * (`Viewport INTERNAL: displayId=0`).
 *
 * Returns 1 and writes the resolved path to `out_path` on success, or 0 if not found.
 */
static inline int parse_dumpsys_input_primary_touch_path(
    FILE* stream,
    char* out_path,
    size_t out_size
) {
    if (!stream || !out_path || out_size < 20) return 0;
    out_path[0] = '\0';

    struct {
        int hub_id;
        char path[64];
    } hub_entries[MAX_DUMPSYS_EVENTHUB_ENTRIES];
    int hub_count = 0;

    int section = 0; /* 0 = none, 1 = Event Hub State, 2 = Input Reader State */
    int cur_hub_id = -999;

    int cur_dev_hub_ids[MAX_DUMPSYS_DEVICE_HUB_IDS];
    int cur_dev_hub_count = 0;
    int cur_dev_is_touch = 0;
    int cur_dev_primary_vp = 0;
    int cur_dev_excluded = 0;

    char line[512];
    while (fgets(line, (int)sizeof(line), stream) != NULL) {
        size_t raw_len = strlen(line);
        while (raw_len > 0 && (line[raw_len - 1] == '\n' || line[raw_len - 1] == '\r')) {
            line[--raw_len] = '\0';
        }

        size_t indent = 0;
        while (line[indent] == ' ' || line[indent] == '\t') {
            indent++;
        }
        const char* trimmed = line + indent;
        if (trimmed[0] == '\0') continue;

        if (strncmp(trimmed, "Event Hub State:", 16) == 0) {
            section = 1;
            cur_hub_id = -999;
            continue;
        }
        if (strncmp(trimmed, "Input Reader State:", 19) == 0) {
            section = 2;
            cur_dev_hub_count = 0;
            cur_dev_is_touch = 0;
            cur_dev_primary_vp = 0;
            cur_dev_excluded = 0;
            continue;
        }
        if (strncmp(trimmed, "Input Dispatcher State:", 23) == 0) {
            break;
        }

        if (section == 1) {
            if (indent >= 2 && indent <= 8 &&
                ((trimmed[0] >= '0' && trimmed[0] <= '9') || trimmed[0] == '-')) {
                char* endptr = NULL;
                long parsed_id = strtol(trimmed, &endptr, 10);
                if (endptr > trimmed && endptr[0] == ':' &&
                    (endptr[1] == ' ' || endptr[1] == '\0')) {
                    cur_hub_id = (int)parsed_id;
                    continue;
                }
            }
            if (cur_hub_id >= 0 && strncmp(trimmed, "Path: /dev/input/event", 22) == 0) {
                const char* path_start = trimmed + 6; /* "/dev/input/event..." */
                const char* digits = trimmed + 22;
                size_t dlen = 0;
                while (digits[dlen] >= '0' && digits[dlen] <= '9') {
                    dlen++;
                }
                if (dlen > 0 && (digits[dlen] == '\0' || digits[dlen] == ' ') &&
                    hub_count < MAX_DUMPSYS_EVENTHUB_ENTRIES) {
                    size_t copy_len = 16 + dlen; /* strlen("/dev/input/event") == 16 */
                    if (copy_len < sizeof(hub_entries[0].path)) {
                        hub_entries[hub_count].hub_id = cur_hub_id;
                        memcpy(hub_entries[hub_count].path, path_start, copy_len);
                        hub_entries[hub_count].path[copy_len] = '\0';
                        hub_count++;
                    }
                }
            }
        } else if (section == 2) {
            if (strncmp(trimmed, "Device ", 7) == 0) {
                char* endptr = NULL;
                (void)strtol(trimmed + 7, &endptr, 10);
                if (endptr > trimmed + 7 && endptr[0] == ':') {
                    cur_dev_hub_count = 0;
                    cur_dev_is_touch = 0;
                    cur_dev_primary_vp = 0;
                    const char* devname = endptr + 1;
                    while (*devname == ' ') devname++;
                    cur_dev_excluded = is_excluded_touch_device_name(devname);
                    continue;
                }
            }

            if (strncmp(trimmed, "EventHub Devices: [", 19) == 0) {
                const char* p = trimmed + 19;
                cur_dev_hub_count = 0;
                while (*p != '\0' && *p != ']' &&
                       cur_dev_hub_count < MAX_DUMPSYS_DEVICE_HUB_IDS) {
                    while (*p == ' ' || *p == ',') p++;
                    if (*p == ']' || *p == '\0') break;
                    char* endptr = NULL;
                    long hid = strtol(p, &endptr, 10);
                    if (endptr == p) break;
                    if (hid >= 0) {
                        cur_dev_hub_ids[cur_dev_hub_count++] = (int)hid;
                    }
                    p = endptr;
                }
            } else if (strstr(trimmed, "Touch Input Mapper") != NULL ||
                       strcmp(trimmed, "DeviceType: touchScreen") == 0 ||
                       strstr(trimmed, "TOUCHSCREEN") != NULL) {
                cur_dev_is_touch = 1;
            } else if (strncmp(trimmed, "Viewport INTERNAL: displayId=0", 30) == 0) {
                char boundary = trimmed[30];
                if (boundary == ',' || boundary == ' ' || boundary == '\0') {
                    cur_dev_primary_vp = 1;
                }
            }

            if (!cur_dev_excluded && cur_dev_is_touch && cur_dev_primary_vp &&
                cur_dev_hub_count > 0) {
                for (int h = 0; h < cur_dev_hub_count; h++) {
                    int target_hub = cur_dev_hub_ids[h];
                    for (int e = 0; e < hub_count; e++) {
                        if (hub_entries[e].hub_id == target_hub) {
                            strncpy(out_path, hub_entries[e].path, out_size - 1);
                            out_path[out_size - 1] = '\0';
                            return 1;
                        }
                    }
                }
            }
        }
    }

    return 0;
}

/**
 * Parses and dispatches multi-touch commands:
 *   D <slot> <x> <y>
 *   M <slot> <x> <y>
 *   U <slot>
 * Returns 1 if handled, 0 otherwise.
 */
static inline int parse_touch_command(const char* line, int fd, int* active_slots_mask) {
    if (!line || fd < 0) return 0;
    char action[8];
    int slot, x, y;

    int parsed = sscanf(line, "%7s %d %d %d", action, &slot, &x, &y);
    if (parsed == 4) {
        if (slot < 0 || slot >= MAX_TOUCH_SLOTS) return 0;
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
    } else if (parsed == 2) {
        if (slot < 0 || slot >= MAX_TOUCH_SLOTS) return 0;
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

static inline void release_all_active_touch_slots(int fd, int* active_slots_mask) {
    if (!active_slots_mask) return;
    if (fd >= 0 && *active_slots_mask != 0) {
        for (int s = 0; s < MAX_TOUCH_SLOTS; s++) {
            if ((*active_slots_mask & (1 << s)) != 0) {
                write_event(fd, EV_ABS, ABS_MT_SLOT, s);
                write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
            }
        }
        write_event(fd, EV_KEY, BTN_TOUCH, 0);
        write_event(fd, EV_SYN, SYN_REPORT, 0);
    }
    *active_slots_mask = 0;
}

/**
 * Parses and dispatches virtual mouse commands:
 *   MB <L|R|M|4|5> <D|U>
 *   MM <dx> <dy>
 *   MW <delta>
 *   MH <delta>
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
    } else if (line[0] == 'M' && line[1] == 'H') {
        int delta;
        if (sscanf(line, "MH %d", &delta) == 1) {
            write_event(fd, EV_REL, REL_HWHEEL, delta);
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
