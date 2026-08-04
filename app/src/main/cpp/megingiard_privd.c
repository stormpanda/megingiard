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
 *   Gamepad commands:
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
#include <stdint.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/time.h>
#include <sys/random.h>
#include <stddef.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <pthread.h>
#include <poll.h>
#include <sys/wait.h>
#include <time.h>
#include <dirent.h>
#include "cmd_parsers.h"

#define PORT_START 51234
#define PORT_END 51238
#define SCAN_MAX 32
#define INPUT_PATH_PREFIX "/dev/input/event"
#define MAX_LINE 512

/* test_bit for evdev capability bitmaps */
#define BITS_PER_LONG    (sizeof(long) * 8)
#define NBITS(x)         (((x) + BITS_PER_LONG - 1) / BITS_PER_LONG)
#define test_bit(bit, array) (((array)[(bit) / BITS_PER_LONG] >> ((bit) % BITS_PER_LONG)) & 1)

static volatile sig_atomic_t g_should_exit = 0;

/*
 * Gamepad evdev fd — promoted to file scope so both serve_client() and the
 * reader thread can access it without passing it as a parameter.
 */
static int g_gamepad_fd = -1;
static int g_mouse_fd = -1;
static int g_keyboard_fd = -1;
static int g_touch_fd = -1;

/* ---------------------------------------------------------------------------
 * Per-install shared secret — loaded from STATE_FILE at daemon startup and
 * provisioned by the app over the trusted ADB TLS channel during bootstrap.
 * Never compiled into the binary; the binary has no hardcoded key.
 * --------------------------------------------------------------------------- */
#define STATE_FILE      "/data/local/tmp/megingiard_privd.key"
#define KEY_HEX_LEN     64     /* 32 bytes * 2 hex chars */
#define HMAC_KEY_BYTES  32

static uint8_t g_hmac_key[HMAC_KEY_BYTES];  /* loaded from STATE_FILE */
static uid_t   g_expected_app_uid;           /* UID of the authorized app process */

/* Evdev-streaming state (SUB GAMEPAD / UNSUB GAMEPAD). */
static volatile int g_reader_active = 0;
static pthread_t g_reader_thread;
static volatile int g_client_fd_for_reader = -1;
static pthread_mutex_t g_send_mutex = PTHREAD_MUTEX_INITIALIZER;

/* Direct mirror server child (MIRROR START_DIRECT/STOP). */
static volatile pid_t g_mirror_pid = -1;
static char g_mirror_socket[64] = {0};
#define MIRROR_DEX_PATH "/data/local/tmp/megingiard_mirror.dex"
#define DIRECT_MIRROR_MAIN_CLASS "com.stormpanda.megingiard.mirrorserver.DirectMirrorServer"

/*
 * Returns 1 if the evdev event (type, code) should be streamed to the client.
 * We forward:
 *   EV_KEY  codes >= BTN_MISC (0x100) — physical gamepad buttons
 *   EV_ABS  joystick axes (ABS_X/Y/Z/RZ) and D-Pad hat axes (ABS_HAT0X/Y)
 */
static int should_emit_evdev(__u16 type, __u16 code) {
    if (type == EV_KEY) return code >= BTN_MISC;
    if (type == EV_ABS) {
        switch (code) {
            case ABS_X:      /* 0  — left stick X */
            case ABS_Y:      /* 1  — left stick Y */
            case ABS_Z:      /* 2  — right stick X */
            case ABS_RZ:     /* 5  — right stick Y */
            case ABS_HAT0X:  /* 16 — D-Pad X */
            case ABS_HAT0Y:  /* 17 — D-Pad Y */
                return 1;
            default:
                return 0;
        }
    }
    return 0;
}

/*
 * Background thread: polls g_gamepad_fd for physical evdev events and
 * forwards filtered events to the connected client as:
 *   EVT <type> <code> <value>\n
 *
 * Read-only observation — the fd is NOT grabbed via EVIOCGRAB. Multiple
 * readers can share an evdev node; Android's EventHub continues to dispatch
 * the same events to the foreground app/game in parallel. We therefore
 * neither swallow nor replay anything; the kernel multicasts each event to
 * every open fd.
 *
 * The thread runs while g_reader_active == 1. All writes to the client
 * socket are protected by g_send_mutex to prevent interleaving with the
 * PONG response written from serve_client().
 */
static void *evdev_reader_thread(void *arg) {
    (void)arg;
    struct pollfd pfd;
    pfd.fd = g_gamepad_fd;
    pfd.events = POLLIN;

    while (g_reader_active) {
        int ret = poll(&pfd, 1, 10); /* 10 ms timeout so the exit flag is checked */
        if (ret <= 0) continue;
        if (!(pfd.revents & POLLIN)) continue;

        struct input_event ev;
        ssize_t r = read(g_gamepad_fd, &ev, sizeof(ev));
        if (r != (ssize_t)sizeof(ev)) break;

        if (!should_emit_evdev(ev.type, ev.code)) continue;

        int cfd = g_client_fd_for_reader;
        if (cfd < 0) continue;

        char buf[64];
        int len = snprintf(buf, sizeof(buf), "EVT %d %d %d\n",
                           (int)ev.type, (int)ev.code, (int)ev.value);
        if (len <= 0 || len >= (int)sizeof(buf)) continue;

        pthread_mutex_lock(&g_send_mutex);
        if (g_reader_active) {
            (void)write(cfd, buf, (size_t)len);
        }
        pthread_mutex_unlock(&g_send_mutex);
    }
    return NULL;
}

/*
 * Signals the reader thread to stop and blocks until it has exited.
 * Safe to call even if no thread is running (g_reader_active == 0).
 */
static void stop_reader_thread(void) {
    if (!g_reader_active) return;
    g_reader_active = 0;
    pthread_join(g_reader_thread, NULL);
    g_client_fd_for_reader = -1;
}

static void signal_handler(int sig) {
    (void)sig;
    g_should_exit = 1;
}

/*
 * Initializes a virtual relative mouse device via /dev/uinput.
 * Returns O_RDWR fd on success, -1 on failure.
 */
static int init_virtual_mouse(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        return -1;
    }
    /* Register EV_KEY for mouse buttons */
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT);
    ioctl(fd, UI_SET_KEYBIT, BTN_MIDDLE);
    ioctl(fd, UI_SET_KEYBIT, BTN_SIDE);    /* mouse button 4 */
    ioctl(fd, UI_SET_KEYBIT, BTN_EXTRA);   /* mouse button 5 */

    /* Register EV_REL for relative movement */
    ioctl(fd, UI_SET_EVBIT, EV_REL);
    ioctl(fd, UI_SET_RELBIT, REL_X);
    ioctl(fd, UI_SET_RELBIT, REL_Y);
    ioctl(fd, UI_SET_RELBIT, REL_WHEEL);

    ioctl(fd, UI_SET_EVBIT, EV_SYN);

    if (setup_uinput_device(fd, BUS_USB, 0x1234, 0x9002, "Megingiard Virtual Mouse") < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

/*
 * Initializes a virtual keyboard device via /dev/uinput.
 * Returns O_RDWR fd on success, -1 on failure.
 */
static int init_virtual_keyboard(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        return -1;
    }
    /* Register EV_KEY capability */
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) < 0) {
        close(fd);
        return -1;
    }
    if (ioctl(fd, UI_SET_EVBIT, EV_SYN) < 0) {
        close(fd);
        return -1;
    }

    /* Register standard keyboard keycodes (1–255: KEY_* range). */
    for (int i = 1; i <= 255; i++) {
        ioctl(fd, UI_SET_KEYBIT, i);
    }

    if (setup_uinput_device(fd, BUS_VIRTUAL, 0x1234, 0x5678, "Megingiard Virtual Keyboard") < 0) {
        close(fd);
        return -1;
    }
    return fd;
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



/*
 * Binds a local TCP socket to 127.0.0.1, scanning port range 51234-51238.
 * Returns a listening fd on success, -1 on failure.
 * Writes the bound port back to out_port.
 */
static int bind_listening_socket(int *out_port) {
    /* SOCK_CLOEXEC ensures the fd is automatically closed in forked children
     * (e.g. the mirror app_process child launched via start_direct_mirror_child).
     * Without it, the child inherits the fd and keeps the socket bound
     * even after the parent daemon is killed, causing the next spawn attempt to
     * fail with EADDRINUSE. */
    int srv = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (srv < 0) return -1;

    int opt = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");

    for (int port = PORT_START; port <= PORT_END; port++) {
        addr.sin_port = htons(port);
        if (bind(srv, (struct sockaddr *)&addr, sizeof(addr)) == 0) {
            if (listen(srv, 1) == 0) {
                *out_port = port;
                return srv;
            }
        }
    }
    close(srv);
    return -1;
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

static int start_direct_mirror_child(int width, int height) {
    if (g_mirror_pid > 0) return 0; /* already running */

    snprintf(g_mirror_socket, sizeof(g_mirror_socket),
             "megingiard.mirror.direct.%d", (int)getpid());

    pid_t pid = fork();
    if (pid < 0) return -1;
    if (pid == 0) {
        char w[16], h[16];
        snprintf(w, sizeof(w), "%d", width);
        snprintf(h, sizeof(h), "%d", height);

        setenv("CLASSPATH", MIRROR_DEX_PATH, 1);
        char *const argv[] = {
            (char *)"app_process",
            (char *)"/data/local/tmp",
            (char *)DIRECT_MIRROR_MAIN_CLASS,
            g_mirror_socket,
            w, h,
            NULL
        };
        execv("/system/bin/app_process", argv);
        _exit(127);
    }

    char search_name[96];
    snprintf(search_name, sizeof(search_name), "@%s", g_mirror_socket);

    int ready = 0;
    struct timespec ts = { .tv_sec = 0, .tv_nsec = 100 * 1000 * 1000 };
    for (int i = 0; i < 50 && !ready; i++) {
        nanosleep(&ts, NULL);

        int status;
        pid_t r = waitpid(pid, &status, WNOHANG);
        if (r == pid || r < 0) {
            g_mirror_pid = -1;
            g_mirror_socket[0] = '\0';
            return -1;
        }

        FILE *f = fopen("/proc/net/unix", "r");
        if (f) {
            char line[512];
            while (fgets(line, sizeof(line), f)) {
                if (strstr(line, search_name)) {
                    ready = 1;
                    break;
                }
            }
            fclose(f);
        }
    }

    if (!ready) {
        kill(pid, SIGTERM);
        /* Bounded teardown: up to 1 s grace for the child to respond to
         * SIGTERM, then SIGKILL.  This prevents the command loop from
         * blocking for more than ~6 s total (5 s poll + 1 s grace), which
         * would otherwise cause the Kotlin MIRROR_START_TIMEOUT_MS to fire
         * before MIRROR_DIRECT_ERR is ever sent. */
        int status;
        int reaped = 0;
        struct timespec grace_ts = { .tv_sec = 0, .tv_nsec = 100 * 1000 * 1000 };
        for (int j = 0; j < 10 && !reaped; j++) {
            nanosleep(&grace_ts, NULL);
            pid_t r = waitpid(pid, &status, WNOHANG);
            if (r == pid || r < 0) reaped = 1;
        }
        if (!reaped) {
            kill(pid, SIGKILL);
            waitpid(pid, &status, 0);
        }
        g_mirror_pid = -1;
        g_mirror_socket[0] = '\0';
        return -1;
    }

    g_mirror_pid = pid;
    return 0;
}

/*
 * Stops the mirror child if running. Sends SIGTERM, reaps with waitpid().
 */
static void stop_mirror_child(void) {
    if (g_mirror_pid <= 0) return;
    kill(g_mirror_pid, SIGTERM);
    int status;
    /* Bounded wait: 1 s grace, then SIGKILL. */
    for (int i = 0; i < 10; ++i) {
        pid_t r = waitpid(g_mirror_pid, &status, WNOHANG);
        if (r == g_mirror_pid || r < 0) {
            g_mirror_pid = -1;
            g_mirror_socket[0] = '\0';
            return;
        }
        struct timespec ts = { .tv_sec = 0, .tv_nsec = 100 * 1000 * 1000 };
        nanosleep(&ts, NULL);
    }
    kill(g_mirror_pid, SIGKILL);
    waitpid(g_mirror_pid, &status, 0);
    g_mirror_pid = -1;
    g_mirror_socket[0] = '\0';
}

/* =========================================================================
 * SHA-256 — public domain (Brad Conte, brad@bradconte.com).
 * Slightly reformatted; semantics unchanged.
 * HMAC-SHA256 and authenticate_client() added for megingiard_privd.
 * ========================================================================= */

#define SHA256_DIGEST_LEN 32
#define SHA256_BLOCK_LEN  64

typedef struct {
    uint8_t  data[64];
    uint32_t datalen;
    uint64_t bitlen;
    uint32_t state[8];
} SHA256_CTX;

#define ROTRIGHT32(a,b) (((a) >> (b)) | ((a) << (32-(b))))
#define SHA256_CH(x,y,z)  (((x) & (y)) ^ (~(x) & (z)))
#define SHA256_MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define SHA256_EP0(x) (ROTRIGHT32(x, 2) ^ ROTRIGHT32(x,13) ^ ROTRIGHT32(x,22))
#define SHA256_EP1(x) (ROTRIGHT32(x, 6) ^ ROTRIGHT32(x,11) ^ ROTRIGHT32(x,25))
#define SHA256_SIG0(x) (ROTRIGHT32(x, 7) ^ ROTRIGHT32(x,18) ^ ((x) >> 3))
#define SHA256_SIG1(x) (ROTRIGHT32(x,17) ^ ROTRIGHT32(x,19) ^ ((x) >> 10))

static const uint32_t sha256_k[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,
    0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
    0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,
    0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,
    0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
    0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,
    0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,
    0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
    0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

static void sha256_transform(SHA256_CTX *ctx, const uint8_t data[]) {
    uint32_t a, b, c, d, e, f, g, h, i, j, t1, t2, m[64];
    for (i = 0, j = 0; i < 16; ++i, j += 4)
        m[i] = ((uint32_t)data[j] << 24) | ((uint32_t)data[j+1] << 16) |
               ((uint32_t)data[j+2] << 8) | ((uint32_t)data[j+3]);
    for (; i < 64; ++i)
        m[i] = SHA256_SIG1(m[i-2]) + m[i-7] + SHA256_SIG0(m[i-15]) + m[i-16];
    a = ctx->state[0]; b = ctx->state[1]; c = ctx->state[2]; d = ctx->state[3];
    e = ctx->state[4]; f = ctx->state[5]; g = ctx->state[6]; h = ctx->state[7];
    for (i = 0; i < 64; ++i) {
        t1 = h + SHA256_EP1(e) + SHA256_CH(e,f,g) + sha256_k[i] + m[i];
        t2 = SHA256_EP0(a) + SHA256_MAJ(a,b,c);
        h = g; g = f; f = e; e = d + t1;
        d = c; c = b; b = a; a = t1 + t2;
    }
    ctx->state[0] += a; ctx->state[1] += b; ctx->state[2] += c; ctx->state[3] += d;
    ctx->state[4] += e; ctx->state[5] += f; ctx->state[6] += g; ctx->state[7] += h;
}

static void sha256_init(SHA256_CTX *ctx) {
    ctx->datalen = 0; ctx->bitlen = 0;
    ctx->state[0] = 0x6a09e667; ctx->state[1] = 0xbb67ae85;
    ctx->state[2] = 0x3c6ef372; ctx->state[3] = 0xa54ff53a;
    ctx->state[4] = 0x510e527f; ctx->state[5] = 0x9b05688c;
    ctx->state[6] = 0x1f83d9ab; ctx->state[7] = 0x5be0cd19;
}

static void sha256_update(SHA256_CTX *ctx, const uint8_t *data, size_t len) {
    size_t i;
    for (i = 0; i < len; ++i) {
        ctx->data[ctx->datalen] = data[i];
        if (++ctx->datalen == 64) {
            sha256_transform(ctx, ctx->data);
            ctx->bitlen += 512;
            ctx->datalen = 0;
        }
    }
}

static void sha256_final(SHA256_CTX *ctx, uint8_t hash[SHA256_DIGEST_LEN]) {
    uint32_t i = ctx->datalen;
    if (ctx->datalen < 56) {
        ctx->data[i++] = 0x80;
        while (i < 56) ctx->data[i++] = 0x00;
    } else {
        ctx->data[i++] = 0x80;
        while (i < 64) ctx->data[i++] = 0x00;
        sha256_transform(ctx, ctx->data);
        memset(ctx->data, 0, 56);
    }
    ctx->bitlen += (uint64_t)ctx->datalen * 8;
    ctx->data[63] = (uint8_t)(ctx->bitlen);
    ctx->data[62] = (uint8_t)(ctx->bitlen >> 8);
    ctx->data[61] = (uint8_t)(ctx->bitlen >> 16);
    ctx->data[60] = (uint8_t)(ctx->bitlen >> 24);
    ctx->data[59] = (uint8_t)(ctx->bitlen >> 32);
    ctx->data[58] = (uint8_t)(ctx->bitlen >> 40);
    ctx->data[57] = (uint8_t)(ctx->bitlen >> 48);
    ctx->data[56] = (uint8_t)(ctx->bitlen >> 56);
    sha256_transform(ctx, ctx->data);
    for (i = 0; i < 4; ++i) {
        hash[i]    = (uint8_t)(ctx->state[0] >> (24 - i * 8));
        hash[i+4]  = (uint8_t)(ctx->state[1] >> (24 - i * 8));
        hash[i+8]  = (uint8_t)(ctx->state[2] >> (24 - i * 8));
        hash[i+12] = (uint8_t)(ctx->state[3] >> (24 - i * 8));
        hash[i+16] = (uint8_t)(ctx->state[4] >> (24 - i * 8));
        hash[i+20] = (uint8_t)(ctx->state[5] >> (24 - i * 8));
        hash[i+24] = (uint8_t)(ctx->state[6] >> (24 - i * 8));
        hash[i+28] = (uint8_t)(ctx->state[7] >> (24 - i * 8));
    }
}

/*
 * HMAC-SHA256(key[key_len], data[data_len]) → out[32].
 * Keys longer than 64 bytes are pre-hashed per RFC 2104.
 */
static void hmac_sha256(const uint8_t *key, size_t key_len,
                        const uint8_t *data, size_t data_len,
                        uint8_t out[SHA256_DIGEST_LEN]) {
    uint8_t k_ipad[SHA256_BLOCK_LEN], k_opad[SHA256_BLOCK_LEN];
    uint8_t tk[SHA256_DIGEST_LEN];
    if (key_len > SHA256_BLOCK_LEN) {
        SHA256_CTX tc;
        sha256_init(&tc);
        sha256_update(&tc, key, key_len);
        sha256_final(&tc, tk);
        key = tk; key_len = SHA256_DIGEST_LEN;
    }
    memset(k_ipad, 0x36, SHA256_BLOCK_LEN);
    memset(k_opad, 0x5c, SHA256_BLOCK_LEN);
    for (size_t i = 0; i < key_len; i++) {
        k_ipad[i] ^= key[i];
        k_opad[i] ^= key[i];
    }
    SHA256_CTX ctx;
    uint8_t inner[SHA256_DIGEST_LEN];
    sha256_init(&ctx);
    sha256_update(&ctx, k_ipad, SHA256_BLOCK_LEN);
    sha256_update(&ctx, data, data_len);
    sha256_final(&ctx, inner);
    sha256_init(&ctx);
    sha256_update(&ctx, k_opad, SHA256_BLOCK_LEN);
    sha256_update(&ctx, inner, SHA256_DIGEST_LEN);
    sha256_final(&ctx, out);
}

#define NONCE_BYTES      16
#define NONCE_HEX_LEN    32   /* NONCE_BYTES * 2 */
#define HMAC_HEX_LEN     64   /* SHA256_DIGEST_LEN * 2 */
#define AUTH_LINE_MAX    80   /* "AUTH " + 64 hex + '\0' */
#define VERIFY_LINE_MAX  40   /* "VERIFY " + 32 hex + '\0' */
#define PROOF_MSG_LEN    72   /* "PROOF " + 64 hex + '\n' + '\0' */

/*
 * Reads up to (max_len-1) chars from fd until '\n' (or EOF/error).
 * Returns char count (excluding '\n'), 0 on EOF, -1 on error.
 */
static int read_line_n(int fd, char *out, int max_len) {
    int n = 0;
    char ch;
    while (n < max_len - 1) {
        ssize_t r = read(fd, &ch, 1);
        if (r == 0) return 0;
        if (r < 0) { if (errno == EINTR) continue; return -1; }
        if (ch == '\n') { out[n] = '\0'; return n; }
        out[n++] = ch;
    }
    while (1) { ssize_t r = read(fd, &ch, 1); if (r <= 0 || ch == '\n') break; }
    out[n] = '\0';
    return n;
}

/*
 * Reads KEY= and UID= from STATE_FILE, decodes into g_hmac_key and
 * g_expected_app_uid. Returns 1 on success, 0 if the file is missing
 * or cannot be parsed (daemon must refuse to start without it).
 */
static int load_state_file(void) {
    FILE *f = fopen(STATE_FILE, "r");
    if (!f) {
        fprintf(stderr, "privd: state file not found: %s\n", STATE_FILE);
        return 0;
    }
    char line[128];
    int got_key = 0, got_uid = 0;
    while (fgets(line, sizeof(line), f)) {
        /* Strip trailing newline. */
        size_t len = strlen(line);
        if (len > 0 && line[len - 1] == '\n') line[--len] = '\0';

        if (strncmp(line, "KEY=", 4) == 0) {
            const char *hex = line + 4;
            if (strlen(hex) != KEY_HEX_LEN) continue;
            for (int i = 0; i < HMAC_KEY_BYTES; i++) {
                char b[3] = { hex[i * 2], hex[i * 2 + 1], '\0' };
                g_hmac_key[i] = (uint8_t)strtol(b, NULL, 16);
            }
            got_key = 1;
        } else if (strncmp(line, "UID=", 4) == 0) {
            long uid = strtol(line + 4, NULL, 10);
            if (uid > 0) {
                g_expected_app_uid = (uid_t)uid;
                got_uid = 1;
            }
        }
    }
    fclose(f);
    if (!got_key || !got_uid) {
        fprintf(stderr, "privd: state file missing KEY or UID\n");
        return 0;
    }
    return 1;
}

/*
 * --provision <key_hex> <app_uid>
 * Writes the state file and exits. Called by PrivdBootstrapper during bootstrap
 * via `adb shell megingiard_privd --provision <key_hex> <app_uid>`.
 *
 * State file format: KEY=<64 uppercase hex>\nUID=<decimal>\n, mode 0600.
 * Returns 0 on success, 1 on error.
 */
static int provision_state(const char *key_hex, const char *uid_str) {
    if (strlen(key_hex) != KEY_HEX_LEN) {
        fprintf(stderr, "privd: provision: invalid key length %zu (need %d)\n",
                strlen(key_hex), KEY_HEX_LEN);
        return 1;
    }
    /* Validate key is hex. */
    for (int i = 0; i < KEY_HEX_LEN; i++) {
        char c = key_hex[i];
        if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'))) {
            fprintf(stderr, "privd: provision: non-hex char '%c' in key\n", c);
            return 1;
        }
    }
    long uid = strtol(uid_str, NULL, 10);
    if (uid <= 0) {
        fprintf(stderr, "privd: provision: invalid UID %ld\n", uid);
        return 1;
    }
    /* Write with mode 0600 so only the shell user (UID 2000) can read it. */
    int fd = open(STATE_FILE, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) {
        fprintf(stderr, "privd: provision: cannot write %s: %s\n", STATE_FILE, strerror(errno));
        return 1;
    }
    dprintf(fd, "KEY=%s\nUID=%ld\n", key_hex, uid);
    close(fd);
    /* Ensure umask didn't widen permissions. */
    chmod(STATE_FILE, 0600);
    fprintf(stderr, "privd: provisioned (UID=%ld)\n", uid);
    return 0;
}

/*
 * Mutual HMAC-SHA256 challenge-response.
 *
 * Server (daemon) → Client: CHAL <32-hex-nonce>\n   (daemon challenges app)
 * Client → Server (daemon): AUTH <64-hex-hmac>\n    (app proves it knows the key)
 * Server (daemon) → Client: OK\n                    (daemon accepts app)
 * Client → Server (daemon): VERIFY <32-hex-nonce2>\n (app challenges daemon back)
 * Server (daemon) → Client: PROOF <64-hex-hmac>\n   (daemon proves it knows the key)
 *
 * Both halves use HMAC-SHA256(per_install_key, nonce_bytes).
 * The key is loaded from STATE_FILE into g_hmac_key at daemon startup.
 * Returns 1 on successful mutual authentication, 0 on any failure.
 * The caller must close client_fd on failure.
 */
static int authenticate_client(int client_fd) {
    /* Generate 16-byte random nonce. */
    uint8_t nonce[NONCE_BYTES];
    if (getrandom(nonce, NONCE_BYTES, 0) != (ssize_t)NONCE_BYTES) {
        /* Fallback: /dev/urandom (available on all Android versions). */
        int urnd = open("/dev/urandom", O_RDONLY);
        if (urnd < 0) return 0;
        ssize_t r = read(urnd, nonce, NONCE_BYTES);
        close(urnd);
        if (r != (ssize_t)NONCE_BYTES) return 0;
    }

    /* Hex-encode nonce → 32 chars. */
    char nonce_hex[NONCE_HEX_LEN + 1];
    for (int i = 0; i < NONCE_BYTES; i++)
        snprintf(nonce_hex + i * 2, 3, "%02X", (unsigned)nonce[i]);
    nonce_hex[NONCE_HEX_LEN] = '\0';

    /* Send: "CHAL <nonce_hex>\n" */
    char chal_msg[48];
    int chal_len = snprintf(chal_msg, sizeof(chal_msg), "CHAL %s\n", nonce_hex);
    if (write(client_fd, chal_msg, (size_t)chal_len) != (ssize_t)chal_len) return 0;

    /* 5-second read timeout for the AUTH response. */
    struct timeval tv = { .tv_sec = 5, .tv_usec = 0 };
    setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    /* Read AUTH response. */
    char auth_line[AUTH_LINE_MAX];
    int n = read_line_n(client_fd, auth_line, AUTH_LINE_MAX);

    /* Reset to blocking. */
    tv.tv_sec = 0;
    setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    /* Validate: "AUTH " prefix + exactly 64 hex chars. */
    if (n != (int)(5 + HMAC_HEX_LEN)) return 0;
    if (strncmp(auth_line, "AUTH ", 5) != 0) return 0;
    const char *received_hex = auth_line + 5;

    /* Use the per-install key loaded from the state file at startup. */
    const uint8_t *key = g_hmac_key;

    /* Compute expected HMAC-SHA256(key, nonce). */
    uint8_t expected[SHA256_DIGEST_LEN];
    hmac_sha256(key, SHA256_DIGEST_LEN, nonce, NONCE_BYTES, expected);

    /* Hex-encode expected → 64 chars. */
    char expected_hex[HMAC_HEX_LEN + 1];
    for (int i = 0; i < SHA256_DIGEST_LEN; i++)
        snprintf(expected_hex + i * 2, 3, "%02X", (unsigned)expected[i]);
    expected_hex[HMAC_HEX_LEN] = '\0';

    /* Constant-time comparison — prevents timing side-channels. */
    int diff = 0;
    for (int i = 0; i < HMAC_HEX_LEN; i++)
        diff |= ((unsigned char)received_hex[i] ^ (unsigned char)expected_hex[i]);
    if (diff != 0) return 0;

    /* Send OK. */
    if (write(client_fd, "OK\n", 3) != 3) return 0;

    /* --- Mutual authentication: client now challenges the server --- */
    /* Set 5-second read timeout for VERIFY. */
    tv.tv_sec = 5;
    tv.tv_usec = 0;
    setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    /* Read VERIFY nonce from client. */
    char verify_line[VERIFY_LINE_MAX];
    int vn = read_line_n(client_fd, verify_line, VERIFY_LINE_MAX);

    /* Reset to blocking. */
    tv.tv_sec = 0;
    setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));

    /* Validate: "VERIFY " prefix + exactly 32 hex chars. */
    if (vn != (int)(7 + NONCE_HEX_LEN)) return 0;
    if (strncmp(verify_line, "VERIFY ", 7) != 0) return 0;
    const char *verify_hex = verify_line + 7;

    /* Decode VERIFY nonce hex → 16 bytes. */
    uint8_t verify_nonce[NONCE_BYTES];
    for (int i = 0; i < NONCE_BYTES; i++) {
        char b[3] = { verify_hex[i*2], verify_hex[i*2+1], '\0' };
        verify_nonce[i] = (uint8_t)strtol(b, NULL, 16);
    }

    /* Compute HMAC-SHA256(key, verify_nonce). */
    uint8_t proof[SHA256_DIGEST_LEN];
    hmac_sha256(key, SHA256_DIGEST_LEN, verify_nonce, NONCE_BYTES, proof);

    /* Hex-encode proof → 64 chars. */
    char proof_hex[HMAC_HEX_LEN + 1];
    for (int i = 0; i < SHA256_DIGEST_LEN; i++)
        snprintf(proof_hex + i * 2, 3, "%02X", (unsigned)proof[i]);
    proof_hex[HMAC_HEX_LEN] = '\0';

    /* Send PROOF. */
    char proof_msg[PROOF_MSG_LEN];
    int proof_len = snprintf(proof_msg, sizeof(proof_msg), "PROOF %s\n", proof_hex);
    if (write(client_fd, proof_msg, (size_t)proof_len) != (ssize_t)proof_len) return 0;
    return 1;
}

static void handle_read_file(int client_fd, const char *path) {
    pthread_mutex_lock(&g_send_mutex);
    FILE *fp = fopen(path, "r");
    if (!fp) {
        (void)write(client_fd, "READ_ERR FILE_NOT_FOUND\n", 24);
        pthread_mutex_unlock(&g_send_mutex);
        return;
    }

    (void)write(client_fd, "READ_BEGIN\n", 11);
    char buf[1024];
    size_t total_read = 0;
    const size_t MAX_READ_BYTES = 128 * 1024;
    while (total_read < MAX_READ_BYTES) {
        size_t n = fread(buf, 1, sizeof(buf), fp);
        if (n == 0) break;
        (void)write(client_fd, buf, n);
        total_read += n;
    }
    fclose(fp);

    (void)write(client_fd, "\nREAD_END\n", 10);
    pthread_mutex_unlock(&g_send_mutex);
}

static void handle_list_processes(int client_fd) {
    pthread_mutex_lock(&g_send_mutex);
    DIR *dir = opendir("/proc");
    if (!dir) {
        (void)write(client_fd, "PROC_ERR OPENDIR_FAILED\n", 24);
        pthread_mutex_unlock(&g_send_mutex);
        return;
    }

    (void)write(client_fd, "PROC_BEGIN\n", 11);

    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        char *endptr;
        long pid = strtol(entry->d_name, &endptr, 10);
        if (*endptr == '\0' && pid > 0) {
            char path[256];
            snprintf(path, sizeof(path), "/proc/%ld/cmdline", pid);
            FILE *fp = fopen(path, "r");
            if (fp) {
                char cmdline[1024];
                memset(cmdline, 0, sizeof(cmdline));
                size_t n = fread(cmdline, 1, sizeof(cmdline) - 1, fp);
                fclose(fp);
                if (n > 0) {
                    for (size_t i = 0; i < n; i++) {
                        if (cmdline[i] == '\0') {
                            cmdline[i] = ' ';
                        }
                    }
                    while (n > 0 && (cmdline[n - 1] == ' ' || cmdline[n - 1] == '\n' || cmdline[n - 1] == '\r')) {
                        cmdline[n - 1] = '\0';
                        n--;
                    }
                    if (n > 0) {
                        struct stat st;
                        snprintf(path, sizeof(path), "/proc/%ld", pid);
                        uid_t uid = 0;
                        if (stat(path, &st) == 0) {
                            uid = st.st_uid;
                        }
                        char resp[2048];
                        int len = snprintf(resp, sizeof(resp), "PROC %ld %u %s\n", pid, (unsigned int)uid, cmdline);
                        if (len > 0) {
                            (void)write(client_fd, resp, (size_t)len);
                        }
                    }
                }
            }
        }
    }
    closedir(dir);

    (void)write(client_fd, "PROC_END\n", 9);
    pthread_mutex_unlock(&g_send_mutex);
}

/*
 * Handles a single client connection.
 * Returns 0 on normal disconnect, 1 if the client requested QUIT.
 *
 * Extended protocol commands:
 *   SUB GAMEPAD\n   — start streaming physical evdev events (EVT lines)
 *   UNSUB GAMEPAD\n — stop streaming
 *   MIRROR START_DIRECT <w> <h>\n               — spawn direct-Surface mirror child
 *   MIRROR STOP\n                              — terminate mirror server
 */
static int serve_client(int client_fd) {
    char line[MAX_LINE];
    char action[5];
    int a, b;

    /* Publish this fd so evdev_reader_thread can send EVT lines to it. */
    g_client_fd_for_reader = client_fd;

    while (!g_should_exit) {
        int n = read_line(client_fd, line);
        if (n <= 0) break;  /* EOF or error */

        if (line[0] == 'P' && strcmp(line, "PING") == 0) {
            const char *resp = "PONG\n";
            pthread_mutex_lock(&g_send_mutex);
            (void)write(client_fd, resp, 5);
            pthread_mutex_unlock(&g_send_mutex);
            continue;
        }
        if (line[0] == 'Q' && strcmp(line, "QUIT") == 0) {
            stop_reader_thread();
            stop_mirror_child();
            return 1;
        }
        if (strcmp(line, "KB_START") == 0) {
            if (g_keyboard_fd < 0) {
                g_keyboard_fd = init_virtual_keyboard();
                if (g_keyboard_fd < 0) {
                    fprintf(stderr, "privd: failed to create virtual keyboard errno=%d\n", errno);
                } else {
                    fprintf(stderr, "privd: virtual keyboard registered\n");
                }
            }
            continue;
        }
        if (strcmp(line, "KB_STOP") == 0) {
            if (g_keyboard_fd >= 0) {
                ioctl(g_keyboard_fd, UI_DEV_DESTROY);
                close(g_keyboard_fd);
                g_keyboard_fd = -1;
                fprintf(stderr, "privd: virtual keyboard de-registered\n");
            }
            continue;
        }
        if (strcmp(line, "SUB GAMEPAD") == 0) {
            if (!g_reader_active) {
                /* Drain any events that accumulated in the kernel buffer while the
                 * reader was inactive. These include stale physical presses and
                 * synthetic events written by previous GD/GU/JS injection sessions;
                 * delivering them to the app would create spurious steps at the very
                 * beginning of the recording. */
                int fl = fcntl(g_gamepad_fd, F_GETFL);
                fcntl(g_gamepad_fd, F_SETFL, fl | O_NONBLOCK);
                struct input_event drain_ev;
                while (read(g_gamepad_fd, &drain_ev, sizeof(drain_ev)) > 0) {}
                fcntl(g_gamepad_fd, F_SETFL, fl);

                /* Re-publish the client fd. stop_reader_thread() resets it to -1,
                 * so a second SUB GAMEPAD on the same connection would silently
                 * discard every event without this assignment. */
                g_client_fd_for_reader = client_fd;
                g_reader_active = 1;
                pthread_create(&g_reader_thread, NULL, evdev_reader_thread, NULL);
            }
            continue;
        }
        if (strcmp(line, "UNSUB GAMEPAD") == 0) {
            stop_reader_thread();
            continue;
        }

        if (strncmp(line, "MIRROR START_DIRECT", 19) == 0) {
            int w, h;
            char resp[96];
            int rl;
            if (sscanf(line, "MIRROR START_DIRECT %d %d", &w, &h) == 2 &&
                w > 0 && h > 0) {
                int rc = start_direct_mirror_child(w, h);
                if (rc == 0) {
                    rl = snprintf(resp, sizeof(resp), "MIRROR_DIRECT_READY\n");
                } else {
                    rl = snprintf(resp, sizeof(resp), "MIRROR_DIRECT_ERR START_FAILED\n");
                }
            } else {
                rl = snprintf(resp, sizeof(resp), "MIRROR_DIRECT_ERR INVALID\n");
            }
            pthread_mutex_lock(&g_send_mutex);
            (void)write(client_fd, resp, rl);
            pthread_mutex_unlock(&g_send_mutex);
            continue;
        }

        if (strcmp(line, "MIRROR STOP") == 0) {
            stop_mirror_child();
            const char *resp = "MIRROR_STOPPED\n";
            pthread_mutex_lock(&g_send_mutex);
            (void)write(client_fd, resp, strlen(resp));
            pthread_mutex_unlock(&g_send_mutex);
            continue;
        }

        if (strncmp(line, "SCREENSHOT ", 11) == 0) {
            char path[384];
            char resp[128];
            int rl;
            char *p = line + 11;
            size_t len = strlen(p);
            while (len > 0 && (p[len - 1] == '\n' || p[len - 1] == '\r')) {
                p[len - 1] = '\0';
                len--;
            }
            if (len > 0 && len < sizeof(path)) {
                strncpy(path, p, sizeof(path));
                path[sizeof(path) - 1] = '\0';
                pid_t pid = fork();
                if (pid == 0) {
                    char *args[] = {"/system/bin/screencap", "-p", path, NULL};
                    execv(args[0], args);
                    _exit(127);
                } else if (pid > 0) {
                    int status;
                    if (waitpid(pid, &status, 0) == pid) {
                        if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
                            rl = snprintf(resp, sizeof(resp), "SCREENSHOT_OK\n");
                        } else {
                            int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
                            rl = snprintf(resp, sizeof(resp), "SCREENSHOT_ERR %d\n", exit_code);
                        }
                    } else {
                        rl = snprintf(resp, sizeof(resp), "SCREENSHOT_ERR WAIT_FAILED\n");
                    }
                } else {
                    rl = snprintf(resp, sizeof(resp), "SCREENSHOT_ERR FORK_FAILED\n");
                }
            } else {
                rl = snprintf(resp, sizeof(resp), "SCREENSHOT_ERR INVALID_PATH\n");
            }
            pthread_mutex_lock(&g_send_mutex);
            (void)write(client_fd, resp, rl);
            pthread_mutex_unlock(&g_send_mutex);
            continue;
        }

        if (strncmp(line, "READ_FILE ", 10) == 0) {
            char *p = line + 10;
            size_t len = strlen(p);
            while (len > 0 && (p[len - 1] == '\n' || p[len - 1] == '\r')) {
                p[len - 1] = '\0';
                len--;
            }
            while (*p == ' ') p++;
            handle_read_file(client_fd, p);
            continue;
        }

        if (strncmp(line, "LIST_PROCESSES", 14) == 0) {
            handle_list_processes(client_fd);
            continue;
        }

        static int active_slots_mask = 0;
        if (parse_touch_command(line, g_touch_fd, &active_slots_mask)) continue;
        if (parse_mouse_command(line, g_mouse_fd)) continue;
        if (parse_key_command(line, g_keyboard_fd)) continue;
        if (parse_gamepad_command(line, g_gamepad_fd)) continue;

        /* Unknown commands are silently ignored — forward-compat for future
         * feature prefixes. */
    }
    if (g_keyboard_fd >= 0) {
        ioctl(g_keyboard_fd, UI_DEV_DESTROY);
        close(g_keyboard_fd);
        g_keyboard_fd = -1;
        fprintf(stderr, "privd: virtual keyboard de-registered (cleanup)\n");
    }
    stop_reader_thread();
    stop_mirror_child();
    return 0;
}

static void detach_from_shell(void) {
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork");
        exit(1);
    }
    if (pid > 0) {
        /* Parent process exits. This closes stdout and signals the shell to exit. */
        exit(0);
    }

    /* Child process continues. Start a new session. */
    setsid();
    signal(SIGHUP, SIG_IGN);

    int devnull = open("/dev/null", O_RDWR);
    if (devnull >= 0) {
        dup2(devnull, STDIN_FILENO);
        dup2(devnull, STDOUT_FILENO);
        dup2(devnull, STDERR_FILENO);
        if (devnull > STDERR_FILENO) close(devnull);
    }
}

int main(int argc, char *argv[]) {
    /* Ignore SIGHUP immediately so the daemon survives the ADB shell stream
     * closing before setsid() is called.  The bootstrapper spawns with '&'
     * and closes the stream as soon as it reads MGRD_SPAWN_OK; the default
     * SIGHUP disposition would kill the process if the shell exits before
     * setsid() completes.  detach_from_shell() still calls setsid() later. */
    signal(SIGHUP,  SIG_IGN);
    /* Graceful shutdown on SIGTERM/SIGINT. */
    signal(SIGTERM, signal_handler);
    signal(SIGINT,  signal_handler);
    signal(SIGPIPE, SIG_IGN);

    /* --provision <key_hex> <app_uid>: write the state file and exit.
     * Called by PrivdBootstrapper during bootstrap over the ADB TLS channel. */
    if (argc == 4 && strcmp(argv[1], "--provision") == 0) {
        return provision_state(argv[2], argv[3]);
    }

    /* Daemon mode: load per-install key + authorized app UID from state file.
     * Refuse to start if no state file is present — daemon cannot authenticate
     * without a provisioned key. */
    if (!load_state_file()) {
        fprintf(stderr, "privd: refusing to start without state file — run bootstrap wizard\n");
        return 1;
    }

    g_gamepad_fd = discover_gamepad_fd();
    if (g_gamepad_fd < 0) {
        (void)write(STDOUT_FILENO, "N\n", 2);
        return 1;
    }

    g_mouse_fd = init_virtual_mouse();
    if (g_mouse_fd < 0) {
        fprintf(stderr, "privd: init_virtual_mouse failed\n");
        (void)write(STDOUT_FILENO, "E\n", 2);
        close(g_gamepad_fd);
        return 1;
    }

    g_touch_fd = open("/dev/input/event6", O_WRONLY);
    if (g_touch_fd < 0) {
        fprintf(stderr, "privd: warning: failed to open touchscreen node event6 (touch injection disabled) errno=%d\n", errno);
    }

    int bound_port = 0;
    int srv_fd = bind_listening_socket(&bound_port);
    if (srv_fd < 0) {
        fprintf(stderr, "privd: bind_listening_socket failed errno=%d\n", errno);
        (void)write(STDOUT_FILENO, "E\n", 2);
        if (g_touch_fd >= 0) close(g_touch_fd);
        close(g_mouse_fd);
        close(g_gamepad_fd);
        return 1;
    }
    fprintf(stderr, "privd: bound to TCP port %d\n", bound_port);

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
        /* HMAC challenge-response: reject clients that cannot prove they hold
         * the per-install key provisioned during bootstrap.
         * authenticate_client() sends CHAL, reads AUTH, verifies HMAC-SHA256,
         * and sends OK on success.  On failure the fd is closed here. */
        if (!authenticate_client(client)) {
            close(client);
            continue;
        }
        int quit = serve_client(client);
        close(client);
        if (quit) break;
    }

    close(srv_fd);
    if (g_touch_fd >= 0) {
        close(g_touch_fd);
    }
    if (g_keyboard_fd >= 0) {
        ioctl(g_keyboard_fd, UI_DEV_DESTROY);
        close(g_keyboard_fd);
    }
    if (g_mouse_fd >= 0) {
        ioctl(g_mouse_fd, UI_DEV_DESTROY);
        close(g_mouse_fd);
    }
    close(g_gamepad_fd);
    return 0;
}
