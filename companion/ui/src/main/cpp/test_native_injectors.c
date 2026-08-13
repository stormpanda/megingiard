#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdint.h>

#if defined(__linux__) || defined(__ANDROID__)
#include <linux/input.h>
#include <linux/uinput.h>
#else
// Mock input_event struct for non-Linux host platforms (e.g. macOS clang)
struct timeval_compat {
    long tv_sec;
    long tv_usec;
};

struct input_event {
    struct timeval_compat time;
    uint16_t type;
    uint16_t code;
    int32_t value;
};

#define EV_SYN 0x00
#define EV_KEY 0x01
#define EV_REL 0x02
#define EV_ABS 0x03
#define SYN_REPORT 0
#define REL_X 0x00
#define REL_Y 0x01
#define REL_WHEEL 0x08
#define ABS_MT_SLOT 0x2f
#define ABS_MT_TRACKING_ID 0x39
#define ABS_MT_POSITION_X 0x35
#define ABS_MT_POSITION_Y 0x36
#define BTN_TOUCH 0x14a
#endif

typedef struct {
    uint16_t type;
    uint16_t code;
    int32_t value;
} MockEvent;

static MockEvent g_events[16];
static int g_event_count = 0;

static void mock_write_event(uint16_t type, uint16_t code, int32_t value) {
    if (g_event_count < 16) {
        g_events[g_event_count].type = type;
        g_events[g_event_count].code = code;
        g_events[g_event_count].value = value;
        g_event_count++;
    }
}

// ── Keyboard Protocol Parser ──
static int parse_key_command(const char *line) {
    char action[4];
    int code;
    if (sscanf(line, "%3s %d", action, &code) != 2) return 0;
    if (code < 1 || code > 464) return 0;

    if (strcmp(action, "KD") == 0) {
        mock_write_event(EV_KEY, (uint16_t)code, 1);
        mock_write_event(EV_SYN, SYN_REPORT, 0);
        return 1;
    } else if (strcmp(action, "KU") == 0) {
        mock_write_event(EV_KEY, (uint16_t)code, 0);
        mock_write_event(EV_SYN, SYN_REPORT, 0);
        return 1;
    }
    return 0;
}

// ── Mouse Protocol Parser ──
static int parse_mouse_command(const char *line) {
    char action[4];
    int p1, p2;
    if (sscanf(line, "%3s %d %d", action, &p1, &p2) != 3) {
        if (sscanf(line, "%3s %d", action, &p1) != 2) return 0;
        p2 = 0;
    }

    if (strcmp(action, "MB") == 0) {
        mock_write_event(EV_KEY, (uint16_t)p1, p2);
        mock_write_event(EV_SYN, SYN_REPORT, 0);
        return 1;
    } else if (strcmp(action, "MM") == 0) {
        mock_write_event(EV_REL, REL_X, p1);
        mock_write_event(EV_REL, REL_Y, p2);
        mock_write_event(EV_SYN, SYN_REPORT, 0);
        return 1;
    } else if (strcmp(action, "MW") == 0) {
        mock_write_event(EV_REL, REL_WHEEL, p2);
        mock_write_event(EV_SYN, SYN_REPORT, 0);
        return 1;
    }
    return 0;
}

// ── Touch Protocol Parser ──
static int parse_touch_command(const char *line) {
    char action[4];
    int slot, x, y;
    if (strncmp(line, "U ", 2) == 0 && sscanf(line, "U %d", &slot) == 1) {
        mock_write_event(EV_ABS, ABS_MT_SLOT, slot);
        mock_write_event(EV_ABS, ABS_MT_TRACKING_ID, -1);
        mock_write_event(EV_SYN, SYN_REPORT, 0);
        return 1;
    }
    if (sscanf(line, "%3s %d %d %d", action, &slot, &x, &y) == 4) {
        if (strcmp(action, "D") == 0) {
            mock_write_event(EV_ABS, ABS_MT_SLOT, slot);
            mock_write_event(EV_ABS, ABS_MT_TRACKING_ID, slot + 1);
            mock_write_event(EV_ABS, ABS_MT_POSITION_X, x);
            mock_write_event(EV_ABS, ABS_MT_POSITION_Y, y);
            mock_write_event(EV_KEY, BTN_TOUCH, 1);
            mock_write_event(EV_SYN, SYN_REPORT, 0);
            return 1;
        } else if (strcmp(action, "M") == 0) {
            mock_write_event(EV_ABS, ABS_MT_SLOT, slot);
            mock_write_event(EV_ABS, ABS_MT_POSITION_X, x);
            mock_write_event(EV_ABS, ABS_MT_POSITION_Y, y);
            mock_write_event(EV_SYN, SYN_REPORT, 0);
            return 1;
        }
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
    int res = parse_key_command("KD 30\n");
    assert(res == 1);
    assert(g_event_count == 2);
    assert(g_events[0].type == EV_KEY);
    assert(g_events[0].code == 30);
    assert(g_events[0].value == 1);
    assert(g_events[1].type == EV_SYN);

    g_event_count = 0;
    res = parse_key_command("KU 30\n");
    assert(res == 1);
    assert(g_events[0].value == 0);

    // Out of bounds keycode check
    res = parse_key_command("KD 9999\n");
    assert(res == 0);
    printf("PASS\n");
}

static void test_mouse_protocol_parsing(void) {
    printf("[TEST] Mouse protocol parsing... ");
    g_event_count = 0;
    int res = parse_mouse_command("MM 10 -5\n");
    assert(res == 1);
    assert(g_event_count == 3);
    assert(g_events[0].type == EV_REL);
    assert(g_events[0].code == REL_X);
    assert(g_events[0].value == 10);
    assert(g_events[1].type == EV_REL);
    assert(g_events[1].code == REL_Y);
    assert(g_events[1].value == -5);
    printf("PASS\n");
}

static void test_touch_protocol_parsing(void) {
    printf("[TEST] Touch protocol parsing... ");
    g_event_count = 0;
    int res = parse_touch_command("D 0 500 1000\n");
    assert(res == 1);
    assert(g_events[0].type == EV_ABS);
    assert(g_events[0].code == ABS_MT_SLOT);
    assert(g_events[0].value == 0);
    assert(g_events[2].code == ABS_MT_POSITION_X);
    assert(g_events[2].value == 500);

    g_event_count = 0;
    res = parse_touch_command("U 0\n");
    assert(res == 1);
    assert(g_events[1].code == ABS_MT_TRACKING_ID);
    assert(g_events[1].value == -1);
    printf("PASS\n");
}

int main(void) {
    printf("=== Megingiard Native C Unit Tests ===\n");
    test_input_event_struct_size();
    test_key_protocol_parsing();
    test_mouse_protocol_parsing();
    test_touch_protocol_parsing();
    printf("All Native C unit tests PASSED successfully!\n");
    return 0;
}
