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
    int cur_dev_excluded = 0;
    int cur_dev_has_touchscreen_source = 0;
    int cur_dev_matched_primary_touch = 0;
    int cur_mapper_is_direct_touch = 0;
    int cur_mapper_primary_vp = 0;

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
            cur_dev_excluded = 0;
            cur_dev_has_touchscreen_source = 0;
            cur_dev_matched_primary_touch = 0;
            cur_mapper_is_direct_touch = 0;
            cur_mapper_primary_vp = 0;
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
                    cur_dev_has_touchscreen_source = 0;
                    cur_dev_matched_primary_touch = 0;
                    cur_mapper_is_direct_touch = 0;
                    cur_mapper_primary_vp = 0;
                    const char* devname = endptr + 1;
                    while (*devname == ' ') devname++;
                    cur_dev_excluded = is_excluded_touch_device_name(devname);
                    continue;
                }
            }

            size_t trimmed_len = strlen(trimmed);
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
            } else if (strncmp(trimmed, "Sources:", 8) == 0) {
                cur_dev_has_touchscreen_source = (strstr(trimmed, "TOUCHSCREEN") != NULL) ? 1 : 0;
            } else if (trimmed_len > 0 && trimmed[trimmed_len - 1] == ':' &&
                       strstr(trimmed, "Input Mapper") != NULL) {
                cur_mapper_primary_vp = 0;
                if (strncmp(trimmed, "Touch Input Mapper (mode - DIRECT):", 35) == 0) {
                    cur_mapper_is_direct_touch = 1;
                } else if (strncmp(trimmed, "Touch Input Mapper:", 19) == 0) {
                    cur_mapper_is_direct_touch = cur_dev_has_touchscreen_source ? 1 : 0;
                } else {
                    cur_mapper_is_direct_touch = 0;
                }
            } else if (strcmp(trimmed, "DeviceType: touchScreen") == 0) {
                cur_mapper_is_direct_touch = 1;
            } else if (strncmp(trimmed, "DeviceType:", 11) == 0) {
                cur_mapper_is_direct_touch = 0;
            } else if (strncmp(trimmed, "Viewport INTERNAL: displayId=0", 30) == 0) {
                char boundary = trimmed[30];
                if (boundary == ',' || boundary == ' ' || boundary == '\0') {
                    cur_mapper_primary_vp = 1;
                }
            }

            if (cur_mapper_is_direct_touch && cur_mapper_primary_vp) {
                cur_dev_matched_primary_touch = 1;
            }

            if (!cur_dev_excluded && cur_dev_matched_primary_touch &&
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
            struct input_event evs[6];
            set_input_event(&evs[0], EV_ABS, ABS_MT_SLOT, slot);
            set_input_event(&evs[1], EV_ABS, ABS_MT_TRACKING_ID, slot + 1);
            set_input_event(&evs[2], EV_ABS, ABS_MT_POSITION_X, x);
            set_input_event(&evs[3], EV_ABS, ABS_MT_POSITION_Y, y);
            if (active_slots_mask) *active_slots_mask |= (1 << slot);
            set_input_event(&evs[4], EV_KEY, BTN_TOUCH, 1);
            set_input_event(&evs[5], EV_SYN, SYN_REPORT, 0);
            write_events_batch(fd, evs, 6);
            return 1;
        } else if (strcmp(action, "M") == 0) {
            struct input_event evs[4];
            set_input_event(&evs[0], EV_ABS, ABS_MT_SLOT, slot);
            set_input_event(&evs[1], EV_ABS, ABS_MT_POSITION_X, x);
            set_input_event(&evs[2], EV_ABS, ABS_MT_POSITION_Y, y);
            set_input_event(&evs[3], EV_SYN, SYN_REPORT, 0);
            write_events_batch(fd, evs, 4);
            return 1;
        }
    } else if (parsed == 2) {
        if (slot < 0 || slot >= MAX_TOUCH_SLOTS) return 0;
        // Multi-touch UP format: U <slot>
        if (strcmp(action, "U") == 0) {
            if (active_slots_mask) *active_slots_mask &= ~(1 << slot);
            int is_active = (active_slots_mask && *active_slots_mask != 0) ? 1 : 0;
            struct input_event evs[4];
            set_input_event(&evs[0], EV_ABS, ABS_MT_SLOT, slot);
            set_input_event(&evs[1], EV_ABS, ABS_MT_TRACKING_ID, -1);
            set_input_event(&evs[2], EV_KEY, BTN_TOUCH, is_active);
            set_input_event(&evs[3], EV_SYN, SYN_REPORT, 0);
            write_events_batch(fd, evs, 4);
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
            struct input_event evs[2];
            set_input_event(&evs[0], EV_KEY, btn, val);
            set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
            write_events_batch(fd, evs, 2);
            return 1;
        }
    } else if (line[0] == 'M' && line[1] == 'M') {
        int dx, dy;
        if (sscanf(line, "MM %d %d", &dx, &dy) == 2) {
            struct input_event evs[3];
            int count = 0;
            if (dx != 0) set_input_event(&evs[count++], EV_REL, REL_X, dx);
            if (dy != 0) set_input_event(&evs[count++], EV_REL, REL_Y, dy);
            if (count > 0) {
                set_input_event(&evs[count++], EV_SYN, SYN_REPORT, 0);
                write_events_batch(fd, evs, count);
            }
            return 1;
        }
    } else if (line[0] == 'M' && line[1] == 'W') {
        int delta;
        if (sscanf(line, "MW %d", &delta) == 1) {
            struct input_event evs[2];
            set_input_event(&evs[0], EV_REL, REL_WHEEL, delta);
            set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
            write_events_batch(fd, evs, 2);
            return 1;
        }
    } else if (line[0] == 'M' && line[1] == 'H') {
        int delta;
        if (sscanf(line, "MH %d", &delta) == 1) {
            struct input_event evs[2];
            set_input_event(&evs[0], EV_REL, REL_HWHEEL, delta);
            set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
            write_events_batch(fd, evs, 2);
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
                    struct input_event evs[2];
                    set_input_event(&evs[0], EV_KEY, (uint16_t)code, 1);
                    set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
                    write_events_batch(fd, evs, 2);
                    return 1;
                } else if (strcmp(action, "KU") == 0) {
                    struct input_event evs[2];
                    set_input_event(&evs[0], EV_KEY, (uint16_t)code, 0);
                    set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
                    write_events_batch(fd, evs, 2);
                    return 1;
                }
            }
        }
    }
    return 0;
}

#define CANONICAL_TRIGGER_MAX 32767
#define CANONICAL_STICK_MIN (-32768)
#define CANONICAL_STICK_MAX 32767

typedef struct {
    int supported;
    uint16_t raw_axis;
    int32_t raw_min;
    int32_t raw_max;
} GamepadAxisBinding;

typedef struct {
    GamepadAxisBinding left_x;
    GamepadAxisBinding left_y;
    GamepadAxisBinding right_x;
    GamepadAxisBinding right_y;
    GamepadAxisBinding left_trigger;
    GamepadAxisBinding left_trigger_alt;
    GamepadAxisBinding right_trigger;
    GamepadAxisBinding right_trigger_alt;
} GamepadAxisMap;

static inline void init_default_gamepad_axis_map(GamepadAxisMap* map) {
    if (!map) return;
    memset(map, 0, sizeof(*map));
    map->left_x.supported = 1;
    map->left_x.raw_axis = ABS_X;
    map->left_x.raw_min = CANONICAL_STICK_MIN;
    map->left_x.raw_max = CANONICAL_STICK_MAX;

    map->left_y.supported = 1;
    map->left_y.raw_axis = ABS_Y;
    map->left_y.raw_min = CANONICAL_STICK_MIN;
    map->left_y.raw_max = CANONICAL_STICK_MAX;

    map->right_x.supported = 1;
    map->right_x.raw_axis = ABS_Z;
    map->right_x.raw_min = CANONICAL_STICK_MIN;
    map->right_x.raw_max = CANONICAL_STICK_MAX;

    map->right_y.supported = 1;
    map->right_y.raw_axis = ABS_RZ;
    map->right_y.raw_min = CANONICAL_STICK_MIN;
    map->right_y.raw_max = CANONICAL_STICK_MAX;

    map->left_trigger.supported = 1;
    map->left_trigger.raw_axis = ABS_BRAKE;
    map->left_trigger.raw_min = 0;
    map->left_trigger.raw_max = CANONICAL_TRIGGER_MAX;

    map->right_trigger.supported = 1;
    map->right_trigger.raw_axis = ABS_GAS;
    map->right_trigger.raw_min = 0;
    map->right_trigger.raw_max = CANONICAL_TRIGGER_MAX;
}

static inline int32_t scale_canonical_stick_to_raw(int32_t canonical_val, int32_t raw_min, int32_t raw_max) {
    if (canonical_val < CANONICAL_STICK_MIN) canonical_val = CANONICAL_STICK_MIN;
    if (canonical_val > CANONICAL_STICK_MAX) canonical_val = CANONICAL_STICK_MAX;
    if (raw_max <= raw_min) return canonical_val;
    if (raw_min == CANONICAL_STICK_MIN && raw_max == CANONICAL_STICK_MAX) {
        return canonical_val;
    }
    if (canonical_val == 0) {
        if (raw_min < 0 && raw_max > 0 && (-raw_min == raw_max || -raw_min == raw_max + 1)) {
            return 0;
        }
        return (int32_t)(((int64_t)raw_min + (int64_t)raw_max) / 2);
    }
    if (canonical_val <= CANONICAL_STICK_MIN) return raw_min;
    if (canonical_val >= CANONICAL_STICK_MAX) return raw_max;
    int64_t span = (int64_t)raw_max - (int64_t)raw_min;
    int64_t shifted = (int64_t)canonical_val - (int64_t)CANONICAL_STICK_MIN; /* 0..65535 */
    return (int32_t)((int64_t)raw_min + (shifted * span + 32767) / 65535);
}

static inline int32_t scale_canonical_trigger_to_raw(int32_t canonical_val, int32_t raw_min, int32_t raw_max) {
    if (canonical_val <= 0) return raw_min;
    if (canonical_val >= CANONICAL_TRIGGER_MAX) return raw_max;
    if (raw_max <= raw_min) return raw_min;
    int64_t span = (int64_t)raw_max - (int64_t)raw_min;
    return (int32_t)((int64_t)raw_min + ((int64_t)canonical_val * span + 16383) / CANONICAL_TRIGGER_MAX);
}

static inline int32_t scale_raw_stick_to_canonical(int32_t raw_val, int32_t raw_min, int32_t raw_max) {
    if (raw_max <= raw_min) return 0;
    if (raw_min == CANONICAL_STICK_MIN && raw_max == CANONICAL_STICK_MAX) {
        if (raw_val < CANONICAL_STICK_MIN) return CANONICAL_STICK_MIN;
        if (raw_val > CANONICAL_STICK_MAX) return CANONICAL_STICK_MAX;
        return raw_val;
    }
    if (raw_val == 0 && raw_min < 0 && raw_max > 0 && (-raw_min == raw_max || -raw_min == raw_max + 1)) {
        return 0;
    }
    if (raw_val <= raw_min) return CANONICAL_STICK_MIN;
    if (raw_val >= raw_max) return CANONICAL_STICK_MAX;
    int64_t span = (int64_t)raw_max - (int64_t)raw_min;
    int64_t shifted = (int64_t)raw_val - (int64_t)raw_min;
    int64_t mapped = (int64_t)CANONICAL_STICK_MIN + (shifted * 65535 + span / 2) / span;
    if (mapped < CANONICAL_STICK_MIN) return CANONICAL_STICK_MIN;
    if (mapped > CANONICAL_STICK_MAX) return CANONICAL_STICK_MAX;
    return (int32_t)mapped;
}

static inline int32_t scale_raw_trigger_to_canonical(int32_t raw_val, int32_t raw_min, int32_t raw_max) {
    if (raw_max <= raw_min) return 0;
    if (raw_val <= raw_min) return 0;
    if (raw_val >= raw_max) return CANONICAL_TRIGGER_MAX;
    int64_t span = (int64_t)raw_max - (int64_t)raw_min;
    int64_t shifted = (int64_t)raw_val - (int64_t)raw_min;
    int64_t mapped = (shifted * CANONICAL_TRIGGER_MAX + span / 2) / span;
    if (mapped < 0) return 0;
    if (mapped > CANONICAL_TRIGGER_MAX) return CANONICAL_TRIGGER_MAX;
    return (int32_t)mapped;
}

/**
 * Translates a raw physical gamepad EV_ABS event into the canonical protocol axis & range:
 *   - ABS_X (0), ABS_Y (1), ABS_Z (2), ABS_RZ (5): -32768..32767
 *   - ABS_BRAKE (10, Left Trigger L2), ABS_GAS (9, Right Trigger R2): 0..32767
 *   - ABS_HAT0X (16), ABS_HAT0Y (17): -1..1
 * Returns 1 if the event maps to a canonical gamepad axis, 0 otherwise.
 */
static inline int translate_gamepad_evdev_abs(
    const GamepadAxisMap* map,
    uint16_t raw_code,
    int32_t raw_val,
    uint16_t* out_code,
    int32_t* out_val
) {
    if (!map || !out_code || !out_val) return 0;
    if (raw_code == ABS_HAT0X || raw_code == ABS_HAT0Y) {
        *out_code = raw_code;
        *out_val = (raw_val < 0) ? -1 : ((raw_val > 0) ? 1 : 0);
        return 1;
    }
    if (map->left_trigger.supported && raw_code == map->left_trigger.raw_axis) {
        *out_code = ABS_BRAKE;
        *out_val = scale_raw_trigger_to_canonical(raw_val, map->left_trigger.raw_min, map->left_trigger.raw_max);
        return 1;
    }
    if (map->left_trigger_alt.supported && raw_code == map->left_trigger_alt.raw_axis) {
        *out_code = ABS_BRAKE;
        *out_val = scale_raw_trigger_to_canonical(raw_val, map->left_trigger_alt.raw_min, map->left_trigger_alt.raw_max);
        return 1;
    }
    if (map->right_trigger.supported && raw_code == map->right_trigger.raw_axis) {
        *out_code = ABS_GAS;
        *out_val = scale_raw_trigger_to_canonical(raw_val, map->right_trigger.raw_min, map->right_trigger.raw_max);
        return 1;
    }
    if (map->right_trigger_alt.supported && raw_code == map->right_trigger_alt.raw_axis) {
        *out_code = ABS_GAS;
        *out_val = scale_raw_trigger_to_canonical(raw_val, map->right_trigger_alt.raw_min, map->right_trigger_alt.raw_max);
        return 1;
    }
    if (map->left_x.supported && raw_code == map->left_x.raw_axis) {
        *out_code = ABS_X;
        *out_val = scale_raw_stick_to_canonical(raw_val, map->left_x.raw_min, map->left_x.raw_max);
        return 1;
    }
    if (map->left_y.supported && raw_code == map->left_y.raw_axis) {
        *out_code = ABS_Y;
        *out_val = scale_raw_stick_to_canonical(raw_val, map->left_y.raw_min, map->left_y.raw_max);
        return 1;
    }
    if (map->right_x.supported && raw_code == map->right_x.raw_axis) {
        *out_code = ABS_Z;
        *out_val = scale_raw_stick_to_canonical(raw_val, map->right_x.raw_min, map->right_x.raw_max);
        return 1;
    }
    if (map->right_y.supported && raw_code == map->right_y.raw_axis) {
        *out_code = ABS_RZ;
        *out_val = scale_raw_stick_to_canonical(raw_val, map->right_y.raw_min, map->right_y.raw_max);
        return 1;
    }
    return 0;
}

/**
 * Parses `dumpsys input` Joystick Input Mapper axes for the EventHub device at `gamepad_path`,
 * populating `out_map` with exact Android-mapped rawAxis, rawMin, and rawMax bounds.
 * Returns 1 if the gamepad's Joystick Input Mapper was found and parsed, 0 otherwise.
 */
static inline int parse_dumpsys_input_gamepad_axes(
    FILE* stream,
    const char* gamepad_path,
    GamepadAxisMap* out_map
) {
    if (!stream || !gamepad_path || gamepad_path[0] == '\0' || !out_map) return 0;

    GamepadAxisMap parsed_map;
    memset(&parsed_map, 0, sizeof(parsed_map));

    int target_hub_id = -999;
    int section = 0; /* 0 = none, 1 = Event Hub State, 2 = Input Reader State */
    int cur_hub_id = -999;
    int in_matching_device = 0;
    int in_joystick_mapper = 0;
    int cur_axis_kind = 0;
    /* 1=X, 2=Y, 3=Z, 4=RZ, 5=LTRIGGER, 6=BRAKE, 7=RTRIGGER, 8=GAS */
    int parsed_any = 0;
    int has_ltrigger = 0;
    int has_brake = 0;
    int has_rtrigger = 0;
    int has_gas = 0;

    char line[512];
    while (fgets(line, (int)sizeof(line), stream) != NULL) {
        size_t raw_len = strlen(line);
        while (raw_len > 0 && (line[raw_len - 1] == '\n' || line[raw_len - 1] == '\r')) {
            line[--raw_len] = '\0';
        }
        size_t indent = 0;
        while (line[indent] == ' ' || line[indent] == '\t') indent++;
        const char* trimmed = line + indent;
        if (trimmed[0] == '\0') continue;

        if (strncmp(trimmed, "Event Hub State:", 16) == 0) {
            section = 1;
            cur_hub_id = -999;
            continue;
        }
        if (strncmp(trimmed, "Input Reader State:", 19) == 0) {
            if (target_hub_id < 0) return 0;
            section = 2;
            in_matching_device = 0;
            in_joystick_mapper = 0;
            cur_axis_kind = 0;
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
            if (cur_hub_id >= 0 && strncmp(trimmed, "Path: ", 6) == 0) {
                const char* p = trimmed + 6;
                size_t plen = strlen(gamepad_path);
                if (strncmp(p, gamepad_path, plen) == 0 &&
                    (p[plen] == '\0' || p[plen] == ' ')) {
                    target_hub_id = cur_hub_id;
                }
            }
        } else if (section == 2) {
            if (strncmp(trimmed, "Device ", 7) == 0) {
                char* endptr = NULL;
                (void)strtol(trimmed + 7, &endptr, 10);
                if (endptr > trimmed + 7 && endptr[0] == ':') {
                    if (in_matching_device && parsed_any) break;
                    in_matching_device = 0;
                    in_joystick_mapper = 0;
                    cur_axis_kind = 0;
                    continue;
                }
            }
            if (strncmp(trimmed, "EventHub Devices: [", 19) == 0) {
                const char* p = trimmed + 19;
                in_matching_device = 0;
                while (*p != '\0' && *p != ']') {
                    while (*p == ' ' || *p == ',') p++;
                    if (*p == ']' || *p == '\0') break;
                    char* endptr = NULL;
                    long hid = strtol(p, &endptr, 10);
                    if (endptr == p) break;
                    if ((int)hid == target_hub_id) {
                        in_matching_device = 1;
                    }
                    p = endptr;
                }
                continue;
            }
            if (!in_matching_device) continue;

            size_t tlen = strlen(trimmed);
            if (tlen > 0 && trimmed[tlen - 1] == ':' && strstr(trimmed, "Input Mapper") != NULL) {
                in_joystick_mapper = (strncmp(trimmed, "Joystick Input Mapper", 21) == 0) ? 1 : 0;
                cur_axis_kind = 0;
                continue;
            }
            if (!in_joystick_mapper) continue;

            if (strncmp(trimmed, "X: ", 3) == 0) { cur_axis_kind = 1; continue; }
            if (strncmp(trimmed, "Y: ", 3) == 0) { cur_axis_kind = 2; continue; }
            if (strncmp(trimmed, "Z: ", 3) == 0) { cur_axis_kind = 3; continue; }
            if (strncmp(trimmed, "RZ: ", 4) == 0) { cur_axis_kind = 4; continue; }
            if (strncmp(trimmed, "LTRIGGER: ", 10) == 0) { cur_axis_kind = 5; continue; }
            if (strncmp(trimmed, "BRAKE: ", 7) == 0) { cur_axis_kind = 6; continue; }
            if (strncmp(trimmed, "RTRIGGER: ", 10) == 0) { cur_axis_kind = 7; continue; }
            if (strncmp(trimmed, "GAS: ", 5) == 0) { cur_axis_kind = 8; continue; }
            if (strchr(trimmed, ':') != NULL && strncmp(trimmed, "Axes:", 5) != 0) {
                /* Other axis header such as HAT_X:, RX:, RY:, RUDDER:, etc. */
                cur_axis_kind = 0;
                continue;
            }

            if (cur_axis_kind > 0 && strncmp(trimmed, "rawAxis=", 8) == 0) {
                int raw_axis = -1;
                int raw_min = 0;
                int raw_max = 0;
                if (sscanf(trimmed, "rawAxis=%d, rawMin=%d, rawMax=%d", &raw_axis, &raw_min, &raw_max) == 3 &&
                    raw_axis >= 0 && raw_axis <= ABS_MAX && raw_max > raw_min) {
                    GamepadAxisBinding b;
                    b.supported = 1;
                    b.raw_axis = (uint16_t)raw_axis;
                    b.raw_min = raw_min;
                    b.raw_max = raw_max;
                    switch (cur_axis_kind) {
                        case 1: parsed_map.left_x = b; parsed_any = 1; break;
                        case 2: parsed_map.left_y = b; parsed_any = 1; break;
                        case 3: parsed_map.right_x = b; parsed_any = 1; break;
                        case 4: parsed_map.right_y = b; parsed_any = 1; break;
                        case 5: /* LTRIGGER */
                            if (has_brake && parsed_map.left_trigger.raw_axis != b.raw_axis) {
                                parsed_map.left_trigger_alt = parsed_map.left_trigger;
                            }
                            parsed_map.left_trigger = b;
                            has_ltrigger = 1;
                            parsed_any = 1;
                            break;
                        case 6: /* BRAKE */
                            if (has_ltrigger) {
                                if (parsed_map.left_trigger.raw_axis != b.raw_axis) {
                                    parsed_map.left_trigger_alt = b;
                                }
                            } else {
                                parsed_map.left_trigger = b;
                            }
                            has_brake = 1;
                            parsed_any = 1;
                            break;
                        case 7: /* RTRIGGER */
                            if (has_gas && parsed_map.right_trigger.raw_axis != b.raw_axis) {
                                parsed_map.right_trigger_alt = parsed_map.right_trigger;
                            }
                            parsed_map.right_trigger = b;
                            has_rtrigger = 1;
                            parsed_any = 1;
                            break;
                        case 8: /* GAS */
                            if (has_rtrigger) {
                                if (parsed_map.right_trigger.raw_axis != b.raw_axis) {
                                    parsed_map.right_trigger_alt = b;
                                }
                            } else {
                                parsed_map.right_trigger = b;
                            }
                            has_gas = 1;
                            parsed_any = 1;
                            break;
                    }
                    cur_axis_kind = 0;
                }
            }
        }
    }
    if (parsed_any) {
        *out_map = parsed_map;
    }
    return parsed_any;
}

/**
 * Returns 1 if `err` indicates that an open evdev node was unregistered or invalidated
 * (e.g. controller profile toggled between Xbox / Odin / Retro / Disconnect).
 */
static inline int is_evdev_disconnect_errno(int err) {
    return (err == ENODEV || err == ENXIO || err == EBADF || err == EIO) ? 1 : 0;
}

/**
 * Returns 1 if `line` begins with a gamepad protocol command prefix
 * (`GD `, `GU `, `HD `, or `JS `), allowing `serve_client` to trigger
 * lazy controller re-discovery even when `g_gamepad_fd` is currently `-1`.
 */
static inline int is_gamepad_command_line(const char* line) {
    if (!line) return 0;
    return (strncmp(line, "GD ", 3) == 0 ||
            strncmp(line, "GU ", 3) == 0 ||
            strncmp(line, "HD ", 3) == 0 ||
            strncmp(line, "JS ", 3) == 0 ||
            strncmp(line, "J2 ", 3) == 0) ? 1 : 0;
}

static inline const GamepadAxisBinding* resolve_stick_axis_binding(
    const GamepadAxisMap* axis_map,
    int axis_code
) {
    if (axis_code == ABS_X) return &axis_map->left_x;
    if (axis_code == ABS_Y) return &axis_map->left_y;
    if (axis_code == ABS_Z) return &axis_map->right_x;
    if (axis_code == ABS_RZ) return &axis_map->right_y;
    return NULL;
}

/**
 * Parses and dispatches virtual gamepad commands using hardware axis bindings:
 *   GD <code> / GU <code>
 *   HD <axis> <value>
 *   JS <canonical_axis_code> <value>
 *   J2 <axisX> <valX> <axisY> <valY>
 *     - Sticks (ABS_X=0, ABS_Y=1, ABS_Z=2, ABS_RZ=5): value in -32768..32767
 *     - Triggers (ABS_BRAKE=10 [L2], ABS_GAS=9 [R2]): value in 0..32767
 * If a kernel `write_events_batch` fails and `out_write_errno` is non-NULL, stores `errno`
 * in `*out_write_errno` and returns 0 so callers can recover from `ENODEV`.
 * Returns 1 if handled and written successfully, 0 otherwise.
 */
static inline int parse_gamepad_command_mapped_ex(
    const char* line,
    int fd,
    const GamepadAxisMap* axis_map,
    int* out_write_errno
) {
    if (out_write_errno) *out_write_errno = 0;
    if (!line || fd < 0) return 0;
    GamepadAxisMap fallback_map;
    if (!axis_map) {
        init_default_gamepad_axis_map(&fallback_map);
        axis_map = &fallback_map;
    }
    char action[4];
    if (line[0] == 'G') {
        int code;
        if (sscanf(line, "%3s %d", action, &code) == 2) {
            if (code >= BTN_MISC && code <= KEY_MAX) {
                if (strcmp(action, "GD") == 0 || strcmp(action, "GU") == 0) {
                    int32_t val = (action[1] == 'D') ? 1 : 0;
                    struct input_event evs[2];
                    set_input_event(&evs[0], EV_KEY, (uint16_t)code, val);
                    set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
                    if (write_events_batch(fd, evs, 2) < 0) {
                        if (out_write_errno) *out_write_errno = errno;
                        return 0;
                    }
                    return 1;
                }
            }
        }
    } else if (line[0] == 'H') {
        int axis, value;
        if (sscanf(line, "%3s %d %d", action, &axis, &value) == 3) {
            if (axis >= 0 && axis <= 1 && value >= -1 && value <= 1 && strcmp(action, "HD") == 0) {
                uint16_t hat_code = (axis == 0) ? ABS_HAT0X : ABS_HAT0Y;
                struct input_event evs[2];
                set_input_event(&evs[0], EV_ABS, hat_code, value);
                set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
                if (write_events_batch(fd, evs, 2) < 0) {
                    if (out_write_errno) *out_write_errno = errno;
                    return 0;
                }
                return 1;
            }
        }
    } else if (line[0] == 'J') {
        if (line[1] == '2' && line[2] == ' ') {
            int axis_x, val_x, axis_y, val_y;
            if (sscanf(line + 3, "%d %d %d %d", &axis_x, &val_x, &axis_y, &val_y) == 4) {
                if (val_x < CANONICAL_STICK_MIN || val_x > CANONICAL_STICK_MAX ||
                    val_y < CANONICAL_STICK_MIN || val_y > CANONICAL_STICK_MAX) {
                    return 0;
                }
                const GamepadAxisBinding* bx = resolve_stick_axis_binding(axis_map, axis_x);
                const GamepadAxisBinding* by = resolve_stick_axis_binding(axis_map, axis_y);
                if (!bx || !by || !bx->supported || !by->supported) return 0;
                int32_t raw_x = scale_canonical_stick_to_raw(val_x, bx->raw_min, bx->raw_max);
                int32_t raw_y = scale_canonical_stick_to_raw(val_y, by->raw_min, by->raw_max);
                struct input_event evs[3];
                set_input_event(&evs[0], EV_ABS, bx->raw_axis, raw_x);
                set_input_event(&evs[1], EV_ABS, by->raw_axis, raw_y);
                set_input_event(&evs[2], EV_SYN, SYN_REPORT, 0);
                if (write_events_batch(fd, evs, 3) < 0) {
                    if (out_write_errno) *out_write_errno = errno;
                    return 0;
                }
                return 1;
            }
            return 0;
        }
        int axis_code, value;
        if (sscanf(line, "%3s %d %d", action, &axis_code, &value) == 3 && strcmp(action, "JS") == 0) {
            const GamepadAxisBinding* b = resolve_stick_axis_binding(axis_map, axis_code);
            if (b != NULL) {
                if (value < CANONICAL_STICK_MIN || value > CANONICAL_STICK_MAX) return 0;
                if (!b->supported) return 0;
                int32_t raw_val = scale_canonical_stick_to_raw(value, b->raw_min, b->raw_max);
                struct input_event evs[2];
                set_input_event(&evs[0], EV_ABS, b->raw_axis, raw_val);
                set_input_event(&evs[1], EV_SYN, SYN_REPORT, 0);
                if (write_events_batch(fd, evs, 2) < 0) {
                    if (out_write_errno) *out_write_errno = errno;
                    return 0;
                }
                return 1;
            } else if (axis_code == ABS_BRAKE || axis_code == ABS_GAS) {
                if (value < 0 || value > CANONICAL_TRIGGER_MAX) return 0;
                const GamepadAxisBinding* primary =
                    (axis_code == ABS_BRAKE) ? &axis_map->left_trigger : &axis_map->right_trigger;
                const GamepadAxisBinding* alt =
                    (axis_code == ABS_BRAKE) ? &axis_map->left_trigger_alt : &axis_map->right_trigger_alt;
                struct input_event evs[3];
                int count = 0;
                if (primary->supported) {
                    int32_t raw_val = scale_canonical_trigger_to_raw(value, primary->raw_min, primary->raw_max);
                    set_input_event(&evs[count++], EV_ABS, primary->raw_axis, raw_val);
                }
                if (alt->supported && (!primary->supported || alt->raw_axis != primary->raw_axis)) {
                    int32_t alt_val = scale_canonical_trigger_to_raw(value, alt->raw_min, alt->raw_max);
                    set_input_event(&evs[count++], EV_ABS, alt->raw_axis, alt_val);
                }
                if (count > 0) {
                    set_input_event(&evs[count++], EV_SYN, SYN_REPORT, 0);
                    if (write_events_batch(fd, evs, count) < 0) {
                        if (out_write_errno) *out_write_errno = errno;
                        return 0;
                    }
                    return 1;
                }
            }
        }
    }
    return 0;
}

static inline int parse_gamepad_command_mapped(
    const char* line,
    int fd,
    const GamepadAxisMap* axis_map
) {
    return parse_gamepad_command_mapped_ex(line, fd, axis_map, NULL);
}

static inline int parse_gamepad_command(const char* line, int fd) {
    return parse_gamepad_command_mapped(line, fd, NULL);
}

#endif /* CMD_PARSERS_H */
