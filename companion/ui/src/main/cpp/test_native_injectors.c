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

int main(void) {
    printf("=== Megingiard Native C Unit Tests ===\n");
    test_input_event_struct_size();
    test_key_protocol_parsing();
    test_mouse_protocol_parsing();
    test_touch_protocol_parsing();
    test_gamepad_protocol_parsing();
    printf("All Native C unit tests PASSED successfully!\n");
    return 0;
}
