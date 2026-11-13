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
static const int MOCK_FD = 42;

void mock_write_event(uint16_t type, uint16_t code, int32_t value) {
    if (g_event_count < 16) {
        g_events[g_event_count].type = type;
        g_events[g_event_count].code = code;
        g_events[g_event_count].value = value;
        g_event_count++;
    }
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

    printf("PASS\n");
}

int main(void) {
    printf("=== Megingiard Native C Unit Tests ===\n");
    test_input_event_struct_size();
    test_key_protocol_parsing();
    test_mouse_protocol_parsing();
    test_touch_protocol_parsing();
    test_gamepad_protocol_parsing();
    test_primary_touch_device_name_filtering();
    test_dumpsys_input_primary_touch_resolution();
    printf("All Native C unit tests PASSED successfully!\n");
    return 0;
}
