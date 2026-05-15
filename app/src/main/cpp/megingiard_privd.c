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
#include <sys/time.h>
#include <sys/random.h>
#include <stddef.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <pthread.h>
#include <poll.h>
#include <sys/wait.h>
#include <time.h>

#define ABSTRACT_SOCKET_NAME "megingiard.privd"
#define SCAN_MAX 32
#define INPUT_PATH_PREFIX "/dev/input/event"
#define MAX_LINE 64

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

/*
 * Default pre-shared key — used when PRIVD_HMAC_KEY_HEX is not supplied at
 * compile time (i.e. megingiard.privd.hmac.key not set in local.properties).
 * Functional but not secret; set a custom key for real isolation.
 */
#ifndef PRIVD_HMAC_KEY_HEX
#define PRIVD_HMAC_KEY_HEX "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6C7D8E9F0A1B2"
#endif

#define NONCE_BYTES   16
#define NONCE_HEX_LEN 32   /* NONCE_BYTES * 2 */
#define HMAC_HEX_LEN  64   /* SHA256_DIGEST_LEN * 2 */
#define AUTH_LINE_MAX 80   /* "AUTH " + 64 hex + '\0' */

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
 * HMAC-SHA256 challenge-response.
 *
 * Server sends:  CHAL <32-hex-nonce>\n   (16 random bytes, hex-encoded)
 * Client replies: AUTH <64-hex-hmac>\n   (HMAC-SHA256(key, nonce_bytes))
 * Server replies: OK\n                   (only on success)
 *
 * Returns 1 on successful authentication, 0 on any failure.
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

    /* Decode PRIVD_HMAC_KEY_HEX (64 hex chars) → 32 key bytes. */
    const char *key_hex = PRIVD_HMAC_KEY_HEX;
    if (strlen(key_hex) != 64) return 0;
    uint8_t key[SHA256_DIGEST_LEN];
    for (int i = 0; i < SHA256_DIGEST_LEN; i++) {
        char b[3] = { key_hex[i*2], key_hex[i*2+1], '\0' };
        key[i] = (uint8_t)strtol(b, NULL, 16);
    }

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
    return 1;
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

        if (line[0] == 'G') {
            /* GD/GU <btn> */
            if (sscanf(line, "%4s %d", action, &a) != 2) continue;
            if (a < BTN_MISC || a > KEY_MAX) continue;
            if (strcmp(action, "GD") == 0) {
                write_event(g_gamepad_fd, EV_KEY, (__u16)a, 1);
                write_event(g_gamepad_fd, EV_SYN, SYN_REPORT, 0);
            } else if (strcmp(action, "GU") == 0) {
                write_event(g_gamepad_fd, EV_KEY, (__u16)a, 0);
                write_event(g_gamepad_fd, EV_SYN, SYN_REPORT, 0);
            }
        } else if (line[0] == 'H') {
            /* HD <axis> <val>  D-Pad */
            if (sscanf(line, "%4s %d %d", action, &a, &b) != 3) continue;
            if (a < 0 || a > 1) continue;
            if (b < -1 || b > 1) continue;
            __u16 code = (a == 0) ? ABS_HAT0X : ABS_HAT0Y;
            write_event(g_gamepad_fd, EV_ABS, code, b);
            write_event(g_gamepad_fd, EV_SYN, SYN_REPORT, 0);
        } else if (line[0] == 'J') {
            /* JS <axis_code> <val>  analog stick */
            if (sscanf(line, "%4s %d %d", action, &a, &b) != 3) continue;
            if (a != ABS_X && a != ABS_Y && a != ABS_Z && a != ABS_RZ) continue;
            if (b < -32768 || b > 32767) continue;
            write_event(g_gamepad_fd, EV_ABS, (__u16)a, b);
            write_event(g_gamepad_fd, EV_SYN, SYN_REPORT, 0);
        }
        /* Unknown commands are silently ignored — forward-compat for future
         * feature prefixes. */
    }
    stop_reader_thread();
    stop_mirror_child();
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

    g_gamepad_fd = discover_gamepad_fd();
    if (g_gamepad_fd < 0) {
        (void)write(STDOUT_FILENO, "N\n", 2);
        return 1;
    }

    int srv_fd = bind_listening_socket();
    if (srv_fd < 0) {
        fprintf(stderr, "privd: bind_listening_socket failed errno=%d\n", errno);
        (void)write(STDOUT_FILENO, "E\n", 2);
        close(g_gamepad_fd);
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
        /* HMAC challenge-response: reject clients that cannot prove they hold
         * the pre-shared key.  authenticate_client() sends CHAL, reads AUTH,
         * verifies HMAC-SHA256, and sends OK on success.  On failure the fd
         * is left open; we close it here and loop back to accept(). */
        if (!authenticate_client(client)) {
            close(client);
            continue;
        }
        int quit = serve_client(client);
        close(client);
        if (quit) break;
    }

    close(srv_fd);
    close(g_gamepad_fd);
    return 0;
}
