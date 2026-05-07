/*
 * megingiard_privd.c — Megingiard privileged helper daemon
 *
 * Spawned by the app over ADB (TLS shell) once the user has paired the device
 * via the Privileged Mode setup wizard. Runs as UID 2000 (shell) in the
 * u:r:shell:s0 SELinux domain — which has the `input` group and can write
 * directly into /dev/input/event* nodes that are owned by group 1004.
 *
 * Communication path (after bootstrap):
 *   App (untrusted_app) ──LocalSocket──▶ Daemon (shell)
 *
 * Listening endpoint:
 *   Abstract Unix socket "@megingiard.privd" (SOCK_STREAM).
 *   Single-client semantics — second connect() blocks until the first
 *   client disconnects.
 *
 * Bootstrap / readiness:
 *   On stdout exactly one line:
 *     R\n   — listening socket bound + physical gamepad node opened
 *     N\n   — no writable gamepad node was discovered (daemon exits 1)
 *     E\n   — generic startup failure (daemon exits 1)
 *
 *   After "R\n" the daemon detaches: closes stdin/stdout/stderr, calls
 *   setsid(), and enters the accept loop. The ADB shell that spawned it can
 *   exit without killing the daemon.
 *
 * Wire protocol (ASCII, newline-terminated, on the LocalSocket):
 *   Feature prefix "GP" (gamepad merge):
 *     GD <btn>\n         button DOWN   (Linux BTN_* code, 0x100..0x1FF)
 *     GU <btn>\n         button UP
 *     HD <axis> <val>\n  D-Pad hat     (axis 0=X 1=Y, val -1/0/+1)
 *     JS <axis> <val>\n  joystick      (axis ABS_X=0 ABS_Y=1 ABS_Z=2 ABS_RZ=5,
 *                                       val -32768..+32767)
 *   Management:
 *     PING\n             → PONG\n
 *     QUIT\n             → daemon exits cleanly with 0
 *
 *  Future feature modules can add new prefixes (e.g. "KB", "MS") without
 *  breaking the existing protocol.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <sys/socket.h>
#include <stddef.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <linux/input.h>

#define ABSTRACT_SOCKET_NAME "megingiard.privd"
#define SCAN_MAX 32
#define INPUT_PATH_PREFIX "/dev/input/event"
#define MAX_LINE 64

/* test_bit for evdev capability bitmaps */
#define BITS_PER_LONG    (sizeof(long) * 8)
#define NBITS(x)         (((x) + BITS_PER_LONG - 1) / BITS_PER_LONG)
#define test_bit(bit, array) (((array)[(bit) / BITS_PER_LONG] >> ((bit) % BITS_PER_LONG)) & 1)

static volatile sig_atomic_t g_should_exit = 0;

static void signal_handler(int sig) {
    (void)sig;
    g_should_exit = 1;
}

/*
 * Walks /dev/input/event0..eventN, finds the first node that:
 *   - has BTN_SOUTH (gamepad face button) AND ABS_X (analog stick) capabilities
 *   - is openable for read+write
 *   - whose EVIOCGNAME does NOT start with "Megingiard" (skip our own
 *     virtual uinput device if it happens to be running concurrently)
 *
 * Returns the open O_RDWR fd on success, -1 on failure.
 */
static int discover_gamepad_fd(void) {
    char path[64];
    unsigned long key_bits[NBITS(KEY_MAX + 1)];
    unsigned long abs_bits[NBITS(ABS_MAX + 1)];
    char devname[256];

    for (int i = 0; i < SCAN_MAX; i++) {
        snprintf(path, sizeof(path), "%s%d", INPUT_PATH_PREFIX, i);

        int probe_fd = open(path, O_RDONLY);
        if (probe_fd < 0) continue;

        memset(key_bits, 0, sizeof(key_bits));
        memset(abs_bits, 0, sizeof(abs_bits));
        memset(devname, 0, sizeof(devname));

        if (ioctl(probe_fd, EVIOCGBIT(EV_KEY, sizeof(key_bits)), key_bits) < 0 ||
            ioctl(probe_fd, EVIOCGBIT(EV_ABS, sizeof(abs_bits)), abs_bits) < 0 ||
            ioctl(probe_fd, EVIOCGNAME(sizeof(devname)), devname) < 0) {
            close(probe_fd);
            continue;
        }
        close(probe_fd);

        /* Skip our own virtual gamepad. */
        if (strncmp(devname, "Megingiard", 10) == 0) continue;

        /* Must be a real gamepad: BTN_SOUTH + ABS_X. */
        if (!test_bit(BTN_SOUTH, key_bits)) continue;
        if (!test_bit(ABS_X, abs_bits))     continue;

        int rw_fd = open(path, O_RDWR);
        if (rw_fd >= 0) {
            fprintf(stderr, "privd: gamepad=%s name=\"%s\"\n", path, devname);
            return rw_fd;
        }
    }
    return -1;
}

static void write_event(int fd, __u16 type, __u16 code, __s32 value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    if (write(fd, &ev, sizeof(ev)) != (ssize_t)sizeof(ev)) {
        /* Non-fatal — log to stderr (only visible during bootstrap). */
        fprintf(stderr, "privd: write_event failed errno=%d\n", errno);
    }
}

/*
 * Binds an abstract-namespace Unix socket "@megingiard.privd".
 * Returns a listening fd on success, -1 on failure.
 */
static int bind_listening_socket(void) {
    int srv = socket(AF_UNIX, SOCK_STREAM, 0);
    if (srv < 0) return -1;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    /* Abstract namespace: leading NUL byte. */
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, ABSTRACT_SOCKET_NAME, sizeof(addr.sun_path) - 2);

    socklen_t len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(ABSTRACT_SOCKET_NAME));
    if (bind(srv, (struct sockaddr *)&addr, len) < 0) {
        close(srv);
        return -1;
    }
    if (listen(srv, 1) < 0) {
        close(srv);
        return -1;
    }
    return srv;
}

/*
 * Reads exactly one newline-terminated line from `fd` into `out` (size MAX_LINE).
 * Returns the number of bytes read excluding the newline, 0 on EOF, -1 on error.
 * Lines longer than MAX_LINE-1 are truncated (the rest is discarded up to newline).
 */
static int read_line(int fd, char *out) {
    int n = 0;
    char ch;
    while (n < MAX_LINE - 1) {
        ssize_t r = read(fd, &ch, 1);
        if (r == 0) return 0;
        if (r < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (ch == '\n') {
            out[n] = '\0';
            return n;
        }
        out[n++] = ch;
    }
    /* Drain rest of overlong line. */
    while (1) {
        ssize_t r = read(fd, &ch, 1);
        if (r <= 0 || ch == '\n') break;
    }
    out[n] = '\0';
    return n;
}

/*
 * Handles a single client connection.
 * Returns 0 on normal disconnect, 1 if the client requested QUIT.
 */
static int serve_client(int client_fd, int gamepad_fd) {
    char line[MAX_LINE];
    char action[5];
    int a, b;

    while (!g_should_exit) {
        int n = read_line(client_fd, line);
        if (n <= 0) return 0;  /* EOF or error */

        if (line[0] == 'P' && strcmp(line, "PING") == 0) {
            const char *resp = "PONG\n";
            (void)write(client_fd, resp, 5);
            continue;
        }
        if (line[0] == 'Q' && strcmp(line, "QUIT") == 0) {
            return 1;
        }

        if (line[0] == 'G') {
            /* GD/GU <btn> */
            if (sscanf(line, "%4s %d", action, &a) != 2) continue;
            if (a < BTN_MISC || a > KEY_MAX) continue;
            if (strcmp(action, "GD") == 0) {
                write_event(gamepad_fd, EV_KEY, (__u16)a, 1);
                write_event(gamepad_fd, EV_SYN, SYN_REPORT, 0);
            } else if (strcmp(action, "GU") == 0) {
                write_event(gamepad_fd, EV_KEY, (__u16)a, 0);
                write_event(gamepad_fd, EV_SYN, SYN_REPORT, 0);
            }
        } else if (line[0] == 'H') {
            /* HD <axis> <val>  D-Pad */
            if (sscanf(line, "%4s %d %d", action, &a, &b) != 3) continue;
            if (a < 0 || a > 1) continue;
            if (b < -1 || b > 1) continue;
            __u16 code = (a == 0) ? ABS_HAT0X : ABS_HAT0Y;
            write_event(gamepad_fd, EV_ABS, code, b);
            write_event(gamepad_fd, EV_SYN, SYN_REPORT, 0);
        } else if (line[0] == 'J') {
            /* JS <axis_code> <val>  analog stick */
            if (sscanf(line, "%4s %d %d", action, &a, &b) != 3) continue;
            if (a != ABS_X && a != ABS_Y && a != ABS_Z && a != ABS_RZ) continue;
            if (b < -32768 || b > 32767) continue;
            write_event(gamepad_fd, EV_ABS, (__u16)a, b);
            write_event(gamepad_fd, EV_SYN, SYN_REPORT, 0);
        }
        /* Unknown commands are silently ignored — forward-compat for future
         * feature prefixes. */
    }
    return 0;
}

static void detach_from_shell(void) {
    /* After signalling readiness on stdout, fully detach so the spawning ADB
     * shell can exit without sending SIGHUP. */
    int devnull = open("/dev/null", O_RDWR);
    if (devnull >= 0) {
        dup2(devnull, STDIN_FILENO);
        dup2(devnull, STDOUT_FILENO);
        dup2(devnull, STDERR_FILENO);
        if (devnull > STDERR_FILENO) close(devnull);
    }
    setsid();
    /* Ignore SIGHUP just in case. */
    signal(SIGHUP, SIG_IGN);
}

int main(void) {
    /* Graceful shutdown on SIGTERM/SIGINT. */
    signal(SIGTERM, signal_handler);
    signal(SIGINT,  signal_handler);
    signal(SIGPIPE, SIG_IGN);

    int gamepad_fd = discover_gamepad_fd();
    if (gamepad_fd < 0) {
        (void)write(STDOUT_FILENO, "N\n", 2);
        return 1;
    }

    int srv_fd = bind_listening_socket();
    if (srv_fd < 0) {
        fprintf(stderr, "privd: bind_listening_socket failed errno=%d\n", errno);
        (void)write(STDOUT_FILENO, "E\n", 2);
        close(gamepad_fd);
        return 1;
    }

    /* Signal readiness, then detach. */
    (void)write(STDOUT_FILENO, "R\n", 2);
    detach_from_shell();

    /* Accept loop — single client at a time. */
    while (!g_should_exit) {
        int client = accept(srv_fd, NULL, NULL);
        if (client < 0) {
            if (errno == EINTR) continue;
            break;
        }
        int quit = serve_client(client, gamepad_fd);
        close(client);
        if (quit) break;
    }

    close(srv_fd);
    close(gamepad_fd);
    return 0;
}
