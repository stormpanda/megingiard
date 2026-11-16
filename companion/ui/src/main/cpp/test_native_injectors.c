#define TEST_MOCK_WRITE_EVENT
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdint.h>

#include "cmd_parsers.h"

typedef struct {
    uint16_t type;
    uint16_t code;
    int32_t value;
} MockEvent;

static MockEvent g_events[16];
static int g_event_count = 0;
static int g_mock_write_errno = 0;
static const int MOCK_FD = 42;

int mock_write_event(uint16_t type, uint16_t code, int32_t value) {
    if (g_mock_write_errno != 0) {
        errno = g_mock_write_errno;
        return -1;
    }
    if (g_event_count < 16) {
        g_events[g_event_count].type = type;
        g_events[g_event_count].code = code;
        g_events[g_event_count].value = value;
        g_event_count++;
    }
    return 0;
}

// ── Unit Tests ──
static void test_input_event_struct_size(void) {
    printf("[TEST] Input event struct layout... ");
    assert(sizeof(uint16_t) == 2);
    assert(sizeof(int32_t) == 4);
    printf("PASS\n");
}

static void test_key_protocol_parsing(void) {
    printf("[TEST] Keyboard protocol parsing... ");
    g_event_count = 0;
    int res = parse_key_command("KD 30\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == 30);
    assert(g_events[0].value == 1);
    assert(g_events[1].type == EV_SYN);
    assert(g_events[1].code == SYN_REPORT);

    g_event_count = 0;
    res = parse_key_command("KU 30\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == 30);
    assert(g_events[0].value == 0);
    assert(g_events[1].type == EV_SYN);

    // Out of bounds keycode check
    res = parse_key_command("KD 9999\n", MOCK_FD);
    assert(res == 0);

    // Invalid format check
    res = parse_key_command("INVALID\n", MOCK_FD);
    assert(res == 0);

    // Negative fd check
    res = parse_key_command("KD 30\n", -1);
    assert(res == 0);

    printf("PASS\n");
}

static void test_mouse_protocol_parsing(void) {
    printf("[TEST] Mouse protocol parsing... ");

    // Relative movement
    g_event_count = 0;
    int res = parse_mouse_command("MM 10 -5\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 3);
    assert(g_events[0].type == EV_REL);
    assert(g_events[0].code == REL_X);
    assert(g_events[0].value == 10);
    assert(g_events[1].type == EV_REL);
    assert(g_events[1].code == REL_Y);
    assert(g_events[1].value == -5);
    assert(g_events[2].type == EV_SYN);

    // Scroll wheel
    g_event_count = 0;
    res = parse_mouse_command("MW 3\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_REL);
    assert(g_events[0].code == REL_WHEEL);
    assert(g_events[0].value == 3);
    assert(g_events[1].type == EV_SYN);

    // Horizontal scroll wheel
    g_event_count = 0;
    res = parse_mouse_command("MH -2\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_REL);
    assert(g_events[0].code == REL_HWHEEL);
    assert(g_events[0].value == -2);
    assert(g_events[1].type == EV_SYN);

    // Mouse button left down / up
    g_event_count = 0;
    res = parse_mouse_command("MB L D\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == BTN_LEFT);
    assert(g_events[0].value == 1);
    assert(g_events[1].type == EV_SYN);

    g_event_count = 0;
    res = parse_mouse_command("MB L U\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == BTN_LEFT);
    assert(g_events[0].value == 0);
    assert(g_events[1].type == EV_SYN);

    // Mouse button right down / up
    g_event_count = 0;
    res = parse_mouse_command("MB R D\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == BTN_RIGHT);
    assert(g_events[0].value == 1);

    // Mouse button middle down
    g_event_count = 0;
    res = parse_mouse_command("MB M D\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == BTN_MIDDLE);
    assert(g_events[0].value == 1);

    // Mouse button 4 (BTN_SIDE) down
    g_event_count = 0;
    res = parse_mouse_command("MB 4 D\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == BTN_SIDE);
    assert(g_events[0].value == 1);

    // Mouse button 5 (BTN_EXTRA) down
    g_event_count = 0;
    res = parse_mouse_command("MB 5 D\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == BTN_EXTRA);
    assert(g_events[0].value == 1);

    // Invalid button code
    res = parse_mouse_command("MB X D\n", MOCK_FD);
    assert(res == 0);

    // Invalid format
    res = parse_mouse_command("MB\n", MOCK_FD);
    assert(res == 0);

    // Negative fd check
    res = parse_mouse_command("MB L D\n", -1);
    assert(res == 0);

    printf("PASS\n");
}

static void test_touch_protocol_parsing(void) {
    printf("[TEST] Touch protocol parsing... ");
    int active_slots = 0;

    // Multi-touch Down
    g_event_count = 0;
    int res = parse_touch_command("D 0 500 1000\n", MOCK_FD, &active_slots);
    assert(res == 1);
    assert(active_slots == 1);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_MT_SLOT);
    assert(g_events[0].value == 0);
    assert(g_events[1].type == EV_ABS);
    assert(g_events[1].code == ABS_MT_TRACKING_ID);
    assert(g_events[1].value == 1);
    assert(g_events[2].type == EV_ABS);
    assert(g_events[2].code == ABS_MT_POSITION_X);
    assert(g_events[2].value == 500);
    assert(g_events[3].type == EV_ABS);
    assert(g_events[3].code == ABS_MT_POSITION_Y);
    assert(g_events[3].value == 1000);
    assert(g_events[4].type == EV_KEY);
    assert(g_events[4].code == BTN_TOUCH);
    assert(g_events[4].value == 1);

    // Multi-touch Move
    g_event_count = 0;
    res = parse_touch_command("M 0 520 1010\n", MOCK_FD, &active_slots);
    assert(res == 1);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_MT_SLOT);
    assert(g_events[0].value == 0);
    assert(g_events[1].type == EV_ABS);
    assert(g_events[1].code == ABS_MT_POSITION_X);
    assert(g_events[1].value == 520);
    assert(g_events[2].type == EV_ABS);
    assert(g_events[2].code == ABS_MT_POSITION_Y);
    assert(g_events[2].value == 1010);

    // Multi-touch Up
    g_event_count = 0;
    res = parse_touch_command("U 0\n", MOCK_FD, &active_slots);
    assert(res == 1);
    assert(active_slots == 0);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_MT_SLOT);
    assert(g_events[0].value == 0);
    assert(g_events[1].type == EV_ABS);
    assert(g_events[1].code == ABS_MT_TRACKING_ID);
    assert(g_events[1].value == -1);
    assert(g_events[2].type == EV_KEY);
    assert(g_events[2].code == BTN_TOUCH);
    assert(g_events[2].value == 0);

    // Invalid format
    res = parse_touch_command("INVALID\n", MOCK_FD, &active_slots);
    assert(res == 0);

    // Out of range slots
    active_slots = 0;
    res = parse_touch_command("D -1 500 500\n", MOCK_FD, &active_slots);
    assert(res == 0);
    assert(active_slots == 0);

    res = parse_touch_command("D 10 500 500\n", MOCK_FD, &active_slots);
    assert(res == 0);
    assert(active_slots == 0);

    res = parse_touch_command("U 32\n", MOCK_FD, &active_slots);
    assert(res == 0);
    assert(active_slots == 0);

    res = parse_touch_command("U -1\n", MOCK_FD, &active_slots);
    assert(res == 0);
    assert(active_slots == 0);

    // Test release_all_active_touch_slots
    res = parse_touch_command("D 0 100 100\n", MOCK_FD, &active_slots);
    assert(res == 1);
    res = parse_touch_command("D 2 200 200\n", MOCK_FD, &active_slots);
    assert(res == 1);
    assert(active_slots == ((1 << 0) | (1 << 2)));

    g_event_count = 0;
    release_all_active_touch_slots(MOCK_FD, &active_slots);
    assert(active_slots == 0);
    assert(g_event_count == 6);
    assert(g_events[0].type == EV_ABS && g_events[0].code == ABS_MT_SLOT && g_events[0].value == 0);
    assert(g_events[1].type == EV_ABS && g_events[1].code == ABS_MT_TRACKING_ID && g_events[1].value == -1);
    assert(g_events[2].type == EV_ABS && g_events[2].code == ABS_MT_SLOT && g_events[2].value == 2);
    assert(g_events[3].type == EV_ABS && g_events[3].code == ABS_MT_TRACKING_ID && g_events[3].value == -1);
    assert(g_events[4].type == EV_KEY && g_events[4].code == BTN_TOUCH && g_events[4].value == 0);
    assert(g_events[5].type == EV_SYN && g_events[5].code == SYN_REPORT && g_events[5].value == 0);

    printf("PASS\n");
}

static void test_gamepad_protocol_parsing(void) {
    printf("[TEST] Gamepad protocol parsing... ");

    // Gamepad button down / up
    g_event_count = 0;
    int res = parse_gamepad_command("GD 304\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == 304);
    assert(g_events[0].value == 1);
    assert(g_events[1].type == EV_SYN);

    g_event_count = 0;
    res = parse_gamepad_command("GU 304\n", MOCK_FD);
    assert(res == 1);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == 304);
    assert(g_events[0].value == 0);

    // D-Pad Hat
    g_event_count = 0;
    res = parse_gamepad_command("HD 0 1\n", MOCK_FD);
    assert(res == 1);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_HAT0X);
    assert(g_events[0].value == 1);

    // Joystick Axis
    g_event_count = 0;
    res = parse_gamepad_command("JS 0 15000\n", MOCK_FD);
    assert(res == 1);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_X);
    assert(g_events[0].value == 15000);

    // Trigger Axes (L2 = ABS_BRAKE=10, R2 = ABS_GAS=9)
    g_event_count = 0;
    res = parse_gamepad_command("JS 10 32767\n", MOCK_FD);
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_BRAKE);
    assert(g_events[0].value == 32767);
    assert(g_events[1].type == EV_SYN);

    g_event_count = 0;
    res = parse_gamepad_command("JS 9 0\n", MOCK_FD);
    assert(res == 1);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_GAS);
    assert(g_events[0].value == 0);

    // Negative trigger value rejected
    assert(parse_gamepad_command("JS 10 -1\n", MOCK_FD) == 0);
    assert(parse_gamepad_command("JS 9 -100\n", MOCK_FD) == 0);

    // Invalid format
    res = parse_gamepad_command("INVALID\n", MOCK_FD);
    assert(res == 0);

    printf("PASS\n");
}

static FILE* open_string_stream(const char* text) {
    FILE* tmp = tmpfile();
    assert(tmp != NULL);
    if (text && text[0] != '\0') {
        size_t len = strlen(text);
        assert(fwrite(text, 1, len, tmp) == len);
    }
    rewind(tmp);
    return tmp;
}

static void test_gamepad_axis_map_and_trigger_scaling(void) {
    printf("[TEST] Gamepad axis mapping & L2/R2 trigger scaling... ");

    // Case 1: Odin / Thor / Xbox One style (0..255 triggers on ABS_BRAKE=10 & ABS_GAS=9)
    const char* odin_dump =
        "Event Hub State:\n"
        "  Devices:\n"
        "    7: Odin Controller\n"
        "      Path: /dev/input/event4\n"
        "Input Reader State:\n"
        "  Device 5: Odin Controller\n"
        "    EventHub Devices: [ 7 ]\n"
        "    Joystick Input Mapper:\n"
        "      Axes:\n"
        "        X: min=-1.00000, max=1.00000\n"
        "          rawAxis=0, rawMin=-32768, rawMax=32767, rawFlat=4096\n"
        "        Y: min=-1.00000, max=1.00000\n"
        "          rawAxis=1, rawMin=-32768, rawMax=32767, rawFlat=4096\n"
        "        Z: min=-1.00000, max=1.00000\n"
        "          rawAxis=2, rawMin=-32768, rawMax=32767, rawFlat=4096\n"
        "        RZ: min=-1.00000, max=1.00000\n"
        "          rawAxis=5, rawMin=-32768, rawMax=32767, rawFlat=4096\n"
        "        BRAKE: min=0.00000, max=1.00000\n"
        "          rawAxis=10, rawMin=0, rawMax=255, rawFlat=0\n"
        "        GAS: min=0.00000, max=1.00000\n"
        "          rawAxis=9, rawMin=0, rawMax=255, rawFlat=0\n";
    GamepadAxisMap map1;
    init_default_gamepad_axis_map(&map1);
    FILE* s1 = open_string_stream(odin_dump);
    assert(parse_dumpsys_input_gamepad_axes(s1, "/dev/input/event4", &map1) == 1);
    fclose(s1);
    assert(map1.left_trigger.raw_axis == 10 && map1.left_trigger.raw_max == 255);
    assert(map1.right_trigger.raw_axis == 9 && map1.right_trigger.raw_max == 255);

    g_event_count = 0;
    assert(parse_gamepad_command_mapped("JS 10 32767\n", MOCK_FD, &map1) == 1);
    assert(g_events[0].code == 10 && g_events[0].value == 255);

    g_event_count = 0;
    assert(parse_gamepad_command_mapped("JS 9 16384\n", MOCK_FD, &map1) == 1);
    assert(g_events[0].code == 9 && (g_events[0].value == 127 || g_events[0].value == 128));

    uint16_t out_code = 0;
    int32_t out_val = 0;
    assert(translate_gamepad_evdev_abs(&map1, 10, 255, &out_code, &out_val) == 1);
    assert(out_code == ABS_BRAKE && out_val == 32767);
    assert(translate_gamepad_evdev_abs(&map1, 9, 0, &out_code, &out_val) == 1);
    assert(out_code == ABS_GAS && out_val == 0);

    // Case 2: Xbox 360 Vendor_045e_Product_028e.kl (Right stick on RX=3/RY=4, Triggers on Z=2/RZ=5 0..1023)
    const char* xbox_dump =
        "Event Hub State:\n"
        "  Devices:\n"
        "    8: Xbox 360 Pad\n"
        "      Path: /dev/input/event8\n"
        "Input Reader State:\n"
        "  Device 6: Xbox 360 Pad\n"
        "    EventHub Devices: [ 8 ]\n"
        "    Joystick Input Mapper:\n"
        "      Axes:\n"
        "        X: min=-1.00000, max=1.00000\n"
        "          rawAxis=0, rawMin=-32768, rawMax=32767\n"
        "        Y: min=-1.00000, max=1.00000\n"
        "          rawAxis=1, rawMin=-32768, rawMax=32767\n"
        "        Z: min=-1.00000, max=1.00000\n"
        "          rawAxis=3, rawMin=-32768, rawMax=32767\n"
        "        RZ: min=-1.00000, max=1.00000\n"
        "          rawAxis=4, rawMin=-32768, rawMax=32767\n"
        "        LTRIGGER: min=0.00000, max=1.00000\n"
        "          rawAxis=2, rawMin=0, rawMax=1023\n"
        "        RTRIGGER: min=0.00000, max=1.00000\n"
        "          rawAxis=5, rawMin=0, rawMax=1023\n";
    GamepadAxisMap map2;
    init_default_gamepad_axis_map(&map2);
    FILE* s2 = open_string_stream(xbox_dump);
    assert(parse_dumpsys_input_gamepad_axes(s2, "/dev/input/event8", &map2) == 1);
    fclose(s2);
    assert(map2.right_x.raw_axis == ABS_RX && map2.right_y.raw_axis == ABS_RY);
    assert(map2.left_trigger.raw_axis == ABS_Z && map2.left_trigger.raw_max == 1023);
    assert(map2.right_trigger.raw_axis == ABS_RZ && map2.right_trigger.raw_max == 1023);

    // Canonical JS 2 (Right Stick X) writes to raw ABS_RX (3), NOT trigger ABS_Z (2)!
    g_event_count = 0;
    assert(parse_gamepad_command_mapped("JS 2 -32768\n", MOCK_FD, &map2) == 1);
    assert(g_events[0].code == ABS_RX && g_events[0].value == -32768);

    // Canonical JS 10 (L2) writes 1023 to raw ABS_Z (2)!
    g_event_count = 0;
    assert(parse_gamepad_command_mapped("JS 10 32767\n", MOCK_FD, &map2) == 1);
    assert(g_events[0].code == ABS_Z && g_events[0].value == 1023);

    // Incoming raw ABS_Z=1023 translates to canonical ABS_BRAKE=32767 (not right stick!)
    assert(translate_gamepad_evdev_abs(&map2, ABS_Z, 1023, &out_code, &out_val) == 1);
    assert(out_code == ABS_BRAKE && out_val == 32767);
    assert(translate_gamepad_evdev_abs(&map2, ABS_RX, 20000, &out_code, &out_val) == 1);
    assert(out_code == ABS_Z && out_val == 20000);

    // Case 3: Clean-map replacement clears stale fallback / alt bindings when dumpsys succeeds
    const char* digital_only_dump =
        "Event Hub State:\n"
        "  Devices:\n"
        "    9: Digital Trigger Pad\n"
        "      Path: /dev/input/event9\n"
        "Input Reader State:\n"
        "  Device 7: Digital Trigger Pad\n"
        "    EventHub Devices: [ 9 ]\n"
        "    Joystick Input Mapper:\n"
        "      Axes:\n"
        "        X: min=-1.00000, max=1.00000\n"
        "          rawAxis=0, rawMin=-32768, rawMax=32767\n"
        "        Y: min=-1.00000, max=1.00000\n"
        "          rawAxis=1, rawMin=-32768, rawMax=32767\n";
    GamepadAxisMap map3;
    init_default_gamepad_axis_map(&map3);
    map3.left_trigger_alt.supported = 1;
    map3.left_trigger_alt.raw_axis = ABS_HAT2Y;
    map3.left_trigger_alt.raw_min = 0;
    map3.left_trigger_alt.raw_max = 255;
    FILE* s3 = open_string_stream(digital_only_dump);
    assert(parse_dumpsys_input_gamepad_axes(s3, "/dev/input/event9", &map3) == 1);
    fclose(s3);
    assert(map3.left_x.supported == 1 && map3.left_y.supported == 1);
    assert(map3.right_x.supported == 0 && map3.right_y.supported == 0);
    assert(map3.left_trigger.supported == 0 && map3.left_trigger_alt.supported == 0);
    assert(map3.right_trigger.supported == 0 && map3.right_trigger_alt.supported == 0);

    // When dumpsys does not contain the target device, fallback map stays untouched
    GamepadAxisMap map_fallback;
    init_default_gamepad_axis_map(&map_fallback);
    FILE* s_miss = open_string_stream(digital_only_dump);
    assert(parse_dumpsys_input_gamepad_axes(s_miss, "/dev/input/event99", &map_fallback) == 0);
    fclose(s_miss);
    assert(map_fallback.left_trigger.supported == 1 && map_fallback.left_trigger.raw_axis == ABS_BRAKE);

    // Hot-switch command detection & ENODEV write-error propagation
    assert(is_gamepad_command_line("GD 304\n") == 1);
    assert(is_gamepad_command_line("GU 304\n") == 1);
    assert(is_gamepad_command_line("HD 0 1\n") == 1);
    assert(is_gamepad_command_line("JS 10 32767\n") == 1);
    assert(is_gamepad_command_line("KD 30\n") == 0);
    assert(is_gamepad_command_line("PING\n") == 0);
    assert(is_evdev_disconnect_errno(ENODEV) == 1);
    assert(is_evdev_disconnect_errno(EINVAL) == 0);

    int write_err = 0;
    g_mock_write_errno = ENODEV;
    assert(parse_gamepad_command_mapped_ex("GD 304\n", MOCK_FD, &map1, &write_err) == 0);
    assert(write_err == ENODEV);
    g_mock_write_errno = 0;
    assert(parse_gamepad_command_mapped_ex("GD 304\n", MOCK_FD, &map1, &write_err) == 1);
    assert(write_err == 0);

    printf("PASS\n");
}

static void test_primary_touch_device_name_filtering(void) {
    printf("[TEST] Primary touchscreen device name & axis span guards... ");
    assert(is_verified_primary_touch_device_name("fts_ts") == 1);
    assert(is_verified_primary_touch_device_name("fts_ts_sec") == 0);
    assert(is_verified_primary_touch_device_name("goodix_ts") == 0);
    assert(is_verified_primary_touch_device_name("Megingiard Virtual Mouse") == 0);
    assert(is_verified_primary_touch_device_name("") == 0);
    assert(is_verified_primary_touch_device_name(NULL) == 0);

    // Zero-Heuristics: Samsung "panel0_sec_ts" is NOT excluded when bound via dumpsys input
    assert(is_excluded_touch_device_name("panel0_sec_ts") == 0);
    assert(is_excluded_touch_device_name("goodix_ts") == 0);
    assert(is_excluded_touch_device_name("fts_ts") == 0);
    assert(is_excluded_touch_device_name("fts_ts_sec") == 1);
    assert(is_excluded_touch_device_name("Megingiard Virtual Keyboard") == 1);
    assert(is_excluded_touch_device_name("megingiard_touch") == 1);
    assert(is_excluded_touch_device_name("") == 1);
    assert(is_excluded_touch_device_name(NULL) == 1);

    // Axis span overflow and degenerate bounds checks
    assert(compute_evdev_axis_span(0, 1079) == 1080);
    assert(compute_evdev_axis_span(0, 1919) == 1920);
    assert(compute_evdev_axis_span(0, -1) == 0);
    assert(compute_evdev_axis_span(100, 50) == 0);
    assert(compute_evdev_axis_span(INT32_MIN, INT32_MAX) == 0);
    assert(compute_evdev_axis_span(0, INT32_MAX) == 0);
    printf("PASS\n");
}

static void test_dumpsys_input_primary_touch_resolution(void) {
    printf("[TEST] dumpsys input primary touchscreen resolution... ");
    char path[64];

    // Case 1: Retroid Pocket Duo where secondary touchscreen appears BEFORE primary in EventHub
    const char* rp_duo_dump =
        "INPUT MANAGER (dumpsys input)\n"
        "\n"
        "Event Hub State:\n"
        "  BuiltInKeyboardId: -2\n"
        "  Devices:\n"
        "    -1: Virtual\n"
        "      Classes: 0x40000023\n"
        "      Path: \n"
        "    4: fts_ts_sec\n"
        "      Classes: TOUCH | TOUCH_MT\n"
        "      Path: /dev/input/event6\n"
        "    5: fts_ts\n"
        "      Classes: TOUCH | TOUCH_MT\n"
        "      Path: /dev/input/event5\n"
        "\n"
        "Input Reader State:\n"
        "  Device 3: fts_ts_sec\n"
        "    EventHub Devices: [ 4 ]\n"
        "    Sources: TOUCHSCREEN\n"
        "    Touch Input Mapper (mode - DIRECT):\n"
        "      Parameters:\n"
        "        DeviceType: touchScreen\n"
        "      Viewport EXTERNAL: displayId=2, uniqueId=local:4630946671160541331\n"
        "  Device 4: fts_ts\n"
        "    EventHub Devices: [ 5 ]\n"
        "    Sources: TOUCHSCREEN\n"
        "    Touch Input Mapper (mode - DIRECT):\n"
        "      Parameters:\n"
        "        DeviceType: touchScreen\n"
        "      Viewport INTERNAL: displayId=0, uniqueId=local:4630947039571902866\n"
        "\n"
        "Input Dispatcher State:\n";
    FILE* f1 = open_string_stream(rp_duo_dump);
    assert(parse_dumpsys_input_primary_touch_path(f1, path, sizeof(path)) == 1);
    assert(strcmp(path, "/dev/input/event5") == 0);
    fclose(f1);

    // Case 2: Non-fts_ts Samsung sec_ts controller ("panel0_sec_ts") on higher event7
    const char* sec_ts_dump =
        "Event Hub State:\n"
        "  Devices:\n"
        "    2: panel1_sec_ts\n"
        "      Path: /dev/input/event3\n"
        "    9: panel0_sec_ts\n"
        "      Path: /dev/input/event7\n"
        "Input Reader State:\n"
        "  Device 10: Megingiard Virtual Mouse\n"
        "    EventHub Devices: [ 2 ]\n"
        "    Viewport INTERNAL: displayId=0, uniqueId=local:0\n"
        "  Device 11: panel1_sec_ts\n"
        "    EventHub Devices: [ 2 ]\n"
        "    Touch Input Mapper (mode - DIRECT):\n"
        "      Viewport INTERNAL: displayId=4, uniqueId=local:1\n"
        "  Device 12: panel0_sec_ts\n"
        "    EventHub Devices: [ 9 ]\n"
        "    Touch Input Mapper (mode - DIRECT):\n"
        "      Viewport INTERNAL: displayId=0, uniqueId=local:0\n";
    FILE* f2 = open_string_stream(sec_ts_dump);
    assert(parse_dumpsys_input_primary_touch_path(f2, path, sizeof(path)) == 1);
    assert(strcmp(path, "/dev/input/event7") == 0);
    fclose(f2);

    // Case 3: displayId=01 must NOT match displayId=0
    const char* mismatch_dump =
        "Event Hub State:\n"
        "  Devices:\n"
        "    5: goodix_ts\n"
        "      Path: /dev/input/event4\n"
        "Input Reader State:\n"
        "  Device 2: goodix_ts\n"
        "    EventHub Devices: [ 5 ]\n"
        "    Touch Input Mapper (mode - DIRECT):\n"
        "      Viewport INTERNAL: displayId=01, uniqueId=local:9\n";
    FILE* f3 = open_string_stream(mismatch_dump);
    assert(parse_dumpsys_input_primary_touch_path(f3, path, sizeof(path)) == 0);
    fclose(f3);

    // Case 4: External Bluetooth controller touchpad (mode - POINTER, DeviceType: pointer)
    // and multi-mapper composite device (secondary Touch + primary Keyboard) must NOT match
    // before the real primary touchscreen.
    const char* pointer_and_composite_dump =
        "Event Hub State:\n"
        "  Devices:\n"
        "    1: Wireless Controller Touchpad\n"
        "      Path: /dev/input/event11\n"
        "    2: composite_dock_hid\n"
        "      Path: /dev/input/event12\n"
        "    9: panel0_sec_ts\n"
        "      Path: /dev/input/event7\n"
        "Input Reader State:\n"
        "  Device 1: Wireless Controller Touchpad\n"
        "    EventHub Devices: [ 1 ]\n"
        "    Sources: MOUSE | TOUCHPAD\n"
        "    Touch Input Mapper (mode - POINTER):\n"
        "      Parameters:\n"
        "        DeviceType: pointer\n"
        "      Viewport INTERNAL: displayId=0, uniqueId=local:0\n"
        "  Device 2: composite_dock_hid\n"
        "    EventHub Devices: [ 2 ]\n"
        "    Sources: KEYBOARD | TOUCHSCREEN\n"
        "    Touch Input Mapper (mode - DIRECT):\n"
        "      Parameters:\n"
        "        DeviceType: touchScreen\n"
        "      Viewport EXTERNAL: displayId=2, uniqueId=local:2\n"
        "    Keyboard Input Mapper:\n"
        "      Viewport INTERNAL: displayId=0, uniqueId=local:0\n"
        "  Device 3: panel0_sec_ts\n"
        "    EventHub Devices: [ 9 ]\n"
        "    Sources: TOUCHSCREEN\n"
        "    Touch Input Mapper (mode - DIRECT):\n"
        "      Parameters:\n"
        "        DeviceType: touchScreen\n"
        "      Viewport INTERNAL: displayId=0, uniqueId=local:0\n";
    FILE* f4 = open_string_stream(pointer_and_composite_dump);
    assert(parse_dumpsys_input_primary_touch_path(f4, path, sizeof(path)) == 1);
    assert(strcmp(path, "/dev/input/event7") == 0);
    fclose(f4);

    printf("PASS\n");
}

int main(void) {
    printf("=== Megingiard Native C Unit Tests ===\n");
    test_input_event_struct_size();
    test_key_protocol_parsing();
    test_mouse_protocol_parsing();
    test_touch_protocol_parsing();
    test_gamepad_protocol_parsing();
    test_gamepad_axis_map_and_trigger_scaling();
    test_primary_touch_device_name_filtering();
    test_dumpsys_input_primary_touch_resolution();
    printf("All Native C unit tests PASSED successfully!\n");
    return 0;
}
