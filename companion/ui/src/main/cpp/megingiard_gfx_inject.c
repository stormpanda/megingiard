/*
 * megingiard_gfx_inject.c — ARM64 ptrace dlopen injector, /proc/<pid>/mem cave
 *                           dispatch-table hooker & RPC bridge for Megingiard
 *                           Universal Graphics Inspector
 *
 * Modes:
 *   1. Inject (ptrace dlopen for Android processes, /proc/<pid>/mem dispatch-table
 *      cave hook fallback for proot/containerized Wine/Proton processes):
 *      megingiard_gfx_inject_arm64 <pid> <absolute_path_to_so>
 *   2. RPC to in-process abstract Unix socket (\0megingiard_gfx_<pid>) or
 *      /proc/<pid>/mem cave state:
 *      megingiard_gfx_inject_arm64 --rpc <pid> <CMD> [ARGS...]
 *   3. Full /proc/<pid>/maps graphics API probe:
 *      megingiard_gfx_inject_arm64 --probe-api <pid>
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <signal.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/xattr.h>
#include <sys/time.h>
#include <linux/elf.h>
#include <asm/ptrace.h>

#ifndef NT_ARM_SYSTEM_CALL
#define NT_ARM_SYSTEM_CALL 0x404
#endif

#ifndef PTRACE_GETSIGMASK
#define PTRACE_GETSIGMASK 0x420a
#define PTRACE_SETSIGMASK 0x420b
#endif

/* ARM64 PSTATE Branch Target Identification (BTI) and Single-Step bits */
#define ARM64_PSR_BTYPE_MASK 0x00000c00ULL
#define ARM64_PSR_SS_BIT     (1ULL << 21)

#define SELINUX_SYS_LIB_CTX  "u:object_r:system_lib_file:s0"
#define STACK_SCRATCH_OFFSET 512UL
#define STACK_SCRATCH_SIZE   256UL
#define STACK_CALL_REDZONE   128UL

/* =========================================================================
 * /proc/<pid>/mem Direct Dispatch-Table Cave Hooker Structures
 * Used when target process is already ptrace-traced (e.g. proot TracerPid != 0)
 * or runs inside a Linux/glibc container without Android's /system/bin/linker64.
 * ========================================================================= */

#define CAVE_MAGIC      0x4D47524443415645ULL /* "MGRDCAVE" */
#define CAVE_MAX_NODES  40
#define CAVE_CMD_STATES 8

typedef struct {
    uint64_t handle;     /* +0x00 */
    uint32_t draws;      /* +0x08 */
    uint16_t category;   /* +0x0C: 1=RENDER_PASS, 2=PIPELINE */
    uint16_t enabled;    /* +0x0E: 1=enabled, 0=disabled */
} CaveNode;              /* 16 bytes */

typedef struct {
    uint64_t cmd_buf;     /* +0x00 */
    uint64_t active_rp;   /* +0x08 */
    uint64_t active_pipe; /* +0x10 */
} CaveCmdState;           /* 24 bytes */

typedef struct {
    uint64_t magic;                       /* +0x000 */
    uint32_t api;                         /* +0x008: 2=VULKAN */
    uint32_t node_count;                  /* +0x00C */
    uint64_t frame_counter;               /* +0x010 */
    uint64_t last_active_rp;              /* +0x018 */
    uint64_t last_active_pipe;            /* +0x020 */
    uint64_t real_CmdBeginRenderPass;     /* +0x028 */
    uint64_t real_CmdBeginRenderPass2;    /* +0x030 */
    uint64_t real_CmdEndRenderPass;       /* +0x038 */
    uint64_t real_CmdEndRenderPass2;      /* +0x040 */
    uint64_t real_CmdBeginRendering;      /* +0x048 */
    uint64_t real_CmdEndRendering;        /* +0x050 */
    uint64_t real_CmdBindPipeline;        /* +0x058 */
    uint64_t real_CmdDraw;                /* +0x060 */
    uint64_t real_CmdDrawIndexed;         /* +0x068 */
    uint64_t real_CmdDrawIndirect;        /* +0x070 */
    uint64_t real_CmdDrawIndexedIndirect; /* +0x078 */
    uint64_t real_CmdDispatch;            /* +0x080 */
    uint64_t real_QueueSubmit;            /* +0x088 */
    uint64_t real_QueueSubmit2;           /* +0x090 */
    uint64_t real_QueuePresentKHR;        /* +0x098 */
    CaveCmdState cmd_states[CAVE_CMD_STATES]; /* +0x0A0 (8 * 24 = 0xC0 bytes) */
    CaveNode nodes[CAVE_MAX_NODES];       /* +0x160 (40 * 16 = 0x280 bytes) */
} MemCaveState;                           /* Total = 0x3E0 (992 bytes) */

_Static_assert(sizeof(CaveNode) == 16, "CaveNode size");
_Static_assert(offsetof(MemCaveState, node_count) == 0x0C, "node_count offset");
_Static_assert(offsetof(MemCaveState, frame_counter) == 0x10, "frame_counter offset");
_Static_assert(offsetof(MemCaveState, last_active_rp) == 0x18, "last_active_rp offset");
_Static_assert(offsetof(MemCaveState, last_active_pipe) == 0x20, "last_active_pipe offset");
_Static_assert(offsetof(MemCaveState, real_CmdBeginRenderPass) == 0x28, "real_CmdBeginRenderPass offset");
_Static_assert(offsetof(MemCaveState, real_QueuePresentKHR) == 0x98, "real_QueuePresentKHR offset");
_Static_assert(offsetof(MemCaveState, cmd_states) == 0xA0, "cmd_states offset");
_Static_assert(offsetof(MemCaveState, nodes) == 0x160, "nodes offset");

/*
 * Self-contained position-independent ARM64 hook templates copied into an unused
 * executable page cave of the target process when ptrace dlopen is unavailable.
 * Only caller-saved scratch registers x9..x17 are modified; x0..x8 and x18..x28
 * and v0..v31 are untouched.
 */
extern const uint8_t cave_code_start[];
extern const uint8_t cave_hook_CmdBeginRenderPass[];
extern const uint8_t cave_hook_CmdBeginRenderPass2[];
extern const uint8_t cave_hook_CmdEndRenderPass[];
extern const uint8_t cave_hook_CmdEndRenderPass2[];
extern const uint8_t cave_hook_CmdBeginRendering[];
extern const uint8_t cave_hook_CmdEndRendering[];
extern const uint8_t cave_hook_CmdBindPipeline[];
extern const uint8_t cave_hook_CmdDraw[];
extern const uint8_t cave_hook_CmdDrawIndexed[];
extern const uint8_t cave_hook_CmdDrawIndirect[];
extern const uint8_t cave_hook_CmdDrawIndexedIndirect[];
extern const uint8_t cave_hook_CmdDispatch[];
extern const uint8_t cave_hook_QueueSubmit[];
extern const uint8_t cave_hook_QueueSubmit2[];
extern const uint8_t cave_hook_QueuePresentKHR[];
extern const uint8_t cave_code_end[];

__asm__(
    ".text\n"
    ".balign 8\n"
    ".global cave_code_start\n"
    "cave_code_start:\n"
    "    .quad 0\n" /* Patched with remote MemCaveState address before injection */

    /* cave_get_cmd_state: x9 = state_ptr, x0 = cmd_buf -> x14 = &cmd_states[i] */
    "cave_get_cmd_state:\n"
    "    add x14, x9, #0xA0\n"
    "    mov w15, #8\n"
    "1:\n"
    "    ldr x13, [x14, #0]\n"
    "    cmp x13, x0\n"
    "    b.eq 4f\n"
    "    add x14, x14, #24\n"
    "    subs w15, w15, #1\n"
    "    b.ne 1b\n"
    "    add x14, x9, #0xA0\n"
    "    mov w15, #8\n"
    "2:\n"
    "    ldr x13, [x14, #0]\n"
    "    cbz x13, 3f\n"
    "    add x14, x14, #24\n"
    "    subs w15, w15, #1\n"
    "    b.ne 2b\n"
    "    lsr x13, x0, #4\n"
    "    and x13, x13, #7\n"
    "    mov x15, #24\n"
    "    mul x13, x13, x15\n"
    "    add x14, x9, #0xA0\n"
    "    add x14, x14, x13\n"
    "3:\n"
    "    str x0, [x14, #0]\n"
    "4:\n"
    "    ret\n"

    /* cave_find_or_add_node: x9 = state_ptr, x10 = handle, w11 = cat, w12 = inc_draw -> w12 = enabled */
    "cave_find_or_add_node:\n"
    "    cbz x10, 5f\n"
    "    add x14, x9, #0x160\n"
    "    mov w15, #40\n"
    "1:\n"
    "    ldr x13, [x14, #0]\n"
    "    cmp x13, x10\n"
    "    b.eq 3f\n"
    "    cbz x13, 2f\n"
    "    add x14, x14, #16\n"
    "    subs w15, w15, #1\n"
    "    b.ne 1b\n"
    "    mov w12, #1\n"
    "    ret\n"
    "2:\n"
    "    str x10, [x14, #0]\n"
    "    str wzr, [x14, #8]\n"
    "    strh w11, [x14, #12]\n"
    "    mov w13, #1\n"
    "    strh w13, [x14, #14]\n"
    "    ldr w13, [x9, #0x0C]\n"
    "    add w13, w13, #1\n"
    "    str w13, [x9, #0x0C]\n"
    "3:\n"
    "    cbz w12, 4f\n"
    "    ldr w13, [x14, #8]\n"
    "    add w13, w13, #1\n"
    "    str w13, [x14, #8]\n"
    "4:\n"
    "    ldrh w12, [x14, #14]\n"
    "    ret\n"
    "5:\n"
    "    mov w12, #1\n"
    "    ret\n"

    /* cave_record_begin_rp: x0 = cmdBuf, x1 = pRenderPassBegin -> x9 = state_ptr */
    "cave_record_begin_rp:\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    ldr x9, cave_code_start\n"
    "    mov x10, #0\n"
    "    cbz x1, 1f\n"
    "    ldr x10, [x1, #16]\n"
    "    cbnz x10, 1f\n"
    "    ldr x10, [x1, #24]\n"
    "1:\n"
    "    bl cave_get_cmd_state\n"
    "    str x10, [x14, #8]\n"
    "    str x10, [x9, #0x18]\n"
    "    mov w11, #1\n"
    "    mov w12, #0\n"
    "    bl cave_find_or_add_node\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ret\n"

    /* cave_record_end_rp: x0 = cmdBuf -> x9 = state_ptr */
    "cave_record_end_rp:\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    ldr x9, cave_code_start\n"
    "    bl cave_get_cmd_state\n"
    "    str xzr, [x14, #8]\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ret\n"

    /* cave_check_draw_allowed: x0 = cmdBuf -> x9 = state_ptr, w12 = allowed (1/0) */
    "cave_check_draw_allowed:\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    ldr x9, cave_code_start\n"
    "    bl cave_get_cmd_state\n"
    "    mov x17, x14\n"
    "    mov w16, #1\n"
    "    ldr x10, [x17, #8]\n"
    "    cbnz x10, 1f\n"
    "    ldr x10, [x9, #0x18]\n"
    "1:\n"
    "    cbz x10, 2f\n"
    "    mov w11, #1\n"
    "    mov w12, #1\n"
    "    bl cave_find_or_add_node\n"
    "    and w16, w16, w12\n"
    "2:\n"
    "    ldr x10, [x17, #16]\n"
    "    cbnz x10, 3f\n"
    "    ldr x10, [x9, #0x20]\n"
    "3:\n"
    "    cbz x10, 4f\n"
    "    mov w11, #2\n"
    "    mov w12, #1\n"
    "    bl cave_find_or_add_node\n"
    "    and w16, w16, w12\n"
    "4:\n"
    "    mov w12, w16\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ret\n"

    ".global cave_hook_CmdBeginRenderPass\n"
    "cave_hook_CmdBeginRenderPass:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_record_begin_rp\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ldr x16, [x9, #0x28]\n"
    "    br x16\n"

    ".global cave_hook_CmdBeginRenderPass2\n"
    "cave_hook_CmdBeginRenderPass2:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_record_begin_rp\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ldr x16, [x9, #0x30]\n"
    "    br x16\n"

    ".global cave_hook_CmdEndRenderPass\n"
    "cave_hook_CmdEndRenderPass:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_record_end_rp\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ldr x16, [x9, #0x38]\n"
    "    br x16\n"

    ".global cave_hook_CmdEndRenderPass2\n"
    "cave_hook_CmdEndRenderPass2:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_record_end_rp\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ldr x16, [x9, #0x40]\n"
    "    br x16\n"

    ".global cave_hook_CmdBeginRendering\n"
    "cave_hook_CmdBeginRendering:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    ldr x9, cave_code_start\n"
    "    mov x10, #0\n"
    "    cbz x1, 1f\n"
    "    ldr x10, [x1, #28]\n"
    "    ldr w11, [x1, #44]\n"
    "    eor x10, x10, x11, lsl #48\n"
    "    ldr x12, [x1, #56]\n"
    "    cmp x12, #0\n"
    "    cset x12, ne\n"
    "    eor x10, x10, x12, lsl #56\n"
    "    movz x12, #0xD900, lsl #48\n"
    "    orr x10, x10, x12\n"
    "1:\n"
    "    bl cave_get_cmd_state\n"
    "    str x10, [x14, #8]\n"
    "    str x10, [x9, #0x18]\n"
    "    mov w11, #1\n"
    "    mov w12, #0\n"
    "    bl cave_find_or_add_node\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ldr x16, [x9, #0x48]\n"
    "    br x16\n"

    ".global cave_hook_CmdEndRendering\n"
    "cave_hook_CmdEndRendering:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_record_end_rp\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ldr x16, [x9, #0x50]\n"
    "    br x16\n"

    ".global cave_hook_CmdBindPipeline\n"
    "cave_hook_CmdBindPipeline:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    ldr x9, cave_code_start\n"
    "    bl cave_get_cmd_state\n"
    "    str x2, [x14, #16]\n"
    "    str x2, [x9, #0x20]\n"
    "    mov x10, x2\n"
    "    mov w11, #2\n"
    "    mov w12, #0\n"
    "    bl cave_find_or_add_node\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ldr x16, [x9, #0x58]\n"
    "    br x16\n"

    ".global cave_hook_CmdDraw\n"
    "cave_hook_CmdDraw:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_check_draw_allowed\n"
    "    ldp x29, x30, [sp], #16\n"
    "    cbz w12, 1f\n"
    "    ldr x16, [x9, #0x60]\n"
    "    br x16\n"
    "1:  ret\n"

    ".global cave_hook_CmdDrawIndexed\n"
    "cave_hook_CmdDrawIndexed:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_check_draw_allowed\n"
    "    ldp x29, x30, [sp], #16\n"
    "    cbz w12, 1f\n"
    "    ldr x16, [x9, #0x68]\n"
    "    br x16\n"
    "1:  ret\n"

    ".global cave_hook_CmdDrawIndirect\n"
    "cave_hook_CmdDrawIndirect:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_check_draw_allowed\n"
    "    ldp x29, x30, [sp], #16\n"
    "    cbz w12, 1f\n"
    "    ldr x16, [x9, #0x70]\n"
    "    br x16\n"
    "1:  ret\n"

    ".global cave_hook_CmdDrawIndexedIndirect\n"
    "cave_hook_CmdDrawIndexedIndirect:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_check_draw_allowed\n"
    "    ldp x29, x30, [sp], #16\n"
    "    cbz w12, 1f\n"
    "    ldr x16, [x9, #0x78]\n"
    "    br x16\n"
    "1:  ret\n"

    ".global cave_hook_CmdDispatch\n"
    "cave_hook_CmdDispatch:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_check_draw_allowed\n"
    "    ldp x29, x30, [sp], #16\n"
    "    cbz w12, 1f\n"
    "    ldr x16, [x9, #0x80]\n"
    "    br x16\n"
    "1:  ret\n"

    ".global cave_hook_QueueSubmit\n"
    "cave_hook_QueueSubmit:\n"
    "    hint #34\n"
    "    ldr x9, cave_code_start\n"
    "    ldr x10, [x9, #0x10]\n"
    "    add x10, x10, #1\n"
    "    str x10, [x9, #0x10]\n"
    "    ldr x16, [x9, #0x88]\n"
    "    br x16\n"

    ".global cave_hook_QueueSubmit2\n"
    "cave_hook_QueueSubmit2:\n"
    "    hint #34\n"
    "    ldr x9, cave_code_start\n"
    "    ldr x10, [x9, #0x10]\n"
    "    add x10, x10, #1\n"
    "    str x10, [x9, #0x10]\n"
    "    ldr x16, [x9, #0x90]\n"
    "    br x16\n"

    ".global cave_hook_QueuePresentKHR\n"
    "cave_hook_QueuePresentKHR:\n"
    "    hint #34\n"
    "    ldr x9, cave_code_start\n"
    "    ldr x10, [x9, #0x10]\n"
    "    add x10, x10, #1\n"
    "    str x10, [x9, #0x10]\n"
    "    ldr x16, [x9, #0x98]\n"
    "    br x16\n"

    ".balign 8\n"
    ".global cave_code_end\n"
    "cave_code_end:\n"
);

typedef struct {
    uintptr_t start;
    uintptr_t end;
    uintptr_t file_offset;
    char perms[8];
    char path[256];
} VmaEntry;

static int find_vma_for_addr(pid_t pid, uintptr_t target_addr, VmaEntry *out) {
    char maps_path[64];
    if (pid == 0) {
        snprintf(maps_path, sizeof(maps_path), "/proc/self/maps");
    } else {
        snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", (int)pid);
    }
    FILE *fp = fopen(maps_path, "r");
    if (!fp) return -1;

    char line[512];
    int found = -1;
    while (fgets(line, sizeof(line), fp)) {
        uintptr_t start = 0, end = 0, offset = 0;
        char perms[8] = {0};
        int path_pos = 0;
        if (sscanf(line, "%lx-%lx %7s %lx %*x:%*x %*d %n", &start, &end, perms, &offset, &path_pos) >= 4) {
            if (target_addr >= start && target_addr < end) {
                out->start = start;
                out->end = end;
                out->file_offset = offset;
                snprintf(out->perms, sizeof(out->perms), "%s", perms);
                out->path[0] = '\0';
                if (path_pos > 0 && line[path_pos] != '\0') {
                    char *p = line + path_pos;
                    while (*p == ' ') p++;
                    size_t len = strlen(p);
                    while (len > 0 && (p[len - 1] == '\n' || p[len - 1] == '\r')) {
                        p[--len] = '\0';
                    }
                    snprintf(out->path, sizeof(out->path), "%s", p);
                }
                found = 0;
                break;
            }
        }
    }
    fclose(fp);
    return found;
}

static const char *path_basename(const char *path) {
    const char *slash = strrchr(path, '/');
    return slash ? (slash + 1) : path;
}

static uintptr_t resolve_remote_symbol_exact(pid_t pid, void *local_sym) {
    if (!local_sym) return 0;
    VmaEntry local_vma;
    if (find_vma_for_addr(0, (uintptr_t)local_sym, &local_vma) < 0 || local_vma.path[0] == '\0') {
        return 0;
    }
    uintptr_t sym_file_off = ((uintptr_t)local_sym - local_vma.start) + local_vma.file_offset;
    const char *base_name = path_basename(local_vma.path);

    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", (int)pid);
    FILE *fp = fopen(maps_path, "r");
    if (!fp) return 0;

    char line[512];
    uintptr_t resolved = 0;
    while (fgets(line, sizeof(line), fp)) {
        uintptr_t start = 0, end = 0, offset = 0;
        char perms[8] = {0};
        int path_pos = 0;
        if (sscanf(line, "%lx-%lx %7s %lx %*x:%*x %*d %n", &start, &end, perms, &offset, &path_pos) >= 4) {
            if (path_pos <= 0 || line[path_pos] == '\0') continue;
            char *p = line + path_pos;
            while (*p == ' ') p++;
            size_t len = strlen(p);
            while (len > 0 && (p[len - 1] == '\n' || p[len - 1] == '\r')) {
                p[--len] = '\0';
            }
            int path_match = (strcmp(p, local_vma.path) == 0) ||
                             (strcmp(path_basename(p), base_name) == 0);
            if (!path_match) continue;

            uintptr_t vma_len = end - start;
            if (sym_file_off >= offset && sym_file_off < offset + vma_len) {
                resolved = start + (sym_file_off - offset);
                break;
            }
        }
    }
    fclose(fp);
    return resolved;
}

static uintptr_t find_system_namespace_caller_addr(pid_t pid) {
    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", (int)pid);
    FILE *fp = fopen(maps_path, "r");
    if (!fp) return 0;

    char line[512];
    uintptr_t preferred = 0;
    uintptr_t fallback = 0;
    while (fgets(line, sizeof(line), fp)) {
        uintptr_t start = 0, end = 0;
        char perms[8] = {0};
        if (sscanf(line, "%lx-%lx %7s", &start, &end, perms) == 3) {
            if (strchr(perms, 'x') == NULL) continue;
            if (strstr(line, "/system/lib64/libEGL.so") ||
                strstr(line, "/system/lib64/libGLESv2.so") ||
                strstr(line, "/system/lib64/libvulkan.so") ||
                strstr(line, "/system/lib64/libandroid.so") ||
                strstr(line, "/system/lib64/liblog.so")) {
                preferred = start;
                break;
            }
            if (fallback == 0 && strstr(line, "/system/lib64/")) {
                fallback = start;
            }
        }
    }
    fclose(fp);
    return preferred != 0 ? preferred : fallback;
}

/* =========================================================================
 * Remote ELF Symbol & VkLayerDispatchTable Resolution via /proc/<pid>/mem
 * ========================================================================= */

static uint32_t elf_sysv_hash_str(const char *name) {
    uint32_t h = 0, g;
    while (*name) {
        h = (h << 4) + (unsigned char)*name++;
        if ((g = h & 0xf0000000u) != 0) h ^= g >> 24;
        h &= ~g;
    }
    return h;
}

static uint32_t elf_gnu_hash_str(const char *name) {
    uint32_t h = 5381u;
    for (unsigned char c = *name; c != '\0'; c = *++name) {
        h = (h << 5) + h + c;
    }
    return h;
}

static uintptr_t normalize_dyn_ptr(uintptr_t val, uintptr_t load_bias, uintptr_t base) {
    if (val == 0) return 0;
    return (val < base) ? (load_bias + val) : val;
}

static int read_remote_cstr(int mem_fd, uintptr_t addr, char *buf, size_t max_len) {
    if (max_len == 0) return -1;
    ssize_t n = pread64(mem_fd, buf, max_len - 1, (off64_t)addr);
    if (n <= 0) return -1;
    buf[n] = '\0';
    return 0;
}

static uintptr_t remote_lookup_dyn_symbol(int mem_fd, uintptr_t lib_base, const char *sym_name) {
    Elf64_Ehdr ehdr;
    if (pread64(mem_fd, &ehdr, sizeof(ehdr), (off64_t)lib_base) != (ssize_t)sizeof(ehdr)) return 0;
    if (memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0) return 0;

    Elf64_Phdr phdrs[32];
    size_t ph_num = ehdr.e_phnum < 32 ? ehdr.e_phnum : 32;
    if (pread64(mem_fd, phdrs, ph_num * sizeof(Elf64_Phdr), (off64_t)(lib_base + ehdr.e_phoff)) !=
        (ssize_t)(ph_num * sizeof(Elf64_Phdr))) {
        return 0;
    }

    uintptr_t load_bias = lib_base;
    int bias_set = 0;
    uintptr_t dyn_vaddr = 0;
    size_t dyn_memsz = 0;

    for (size_t i = 0; i < ph_num; i++) {
        if (phdrs[i].p_type == PT_LOAD && !bias_set) {
            load_bias = lib_base - (uintptr_t)(phdrs[i].p_vaddr & ~0xFFFULL);
            bias_set = 1;
        } else if (phdrs[i].p_type == PT_DYNAMIC) {
            dyn_vaddr = phdrs[i].p_vaddr;
            dyn_memsz = phdrs[i].p_memsz;
        }
    }
    if (dyn_vaddr == 0 || dyn_memsz == 0) return 0;

    Elf64_Dyn dyns[128];
    size_t max_dyns = dyn_memsz / sizeof(Elf64_Dyn);
    if (max_dyns > 128) max_dyns = 128;
    if (pread64(mem_fd, dyns, max_dyns * sizeof(Elf64_Dyn), (off64_t)(load_bias + dyn_vaddr)) <= 0) {
        return 0;
    }

    uintptr_t symtab = 0, strtab = 0, sysv_hash = 0, gnu_hash = 0;
    for (size_t i = 0; i < max_dyns && dyns[i].d_tag != DT_NULL; i++) {
        if (dyns[i].d_tag == DT_SYMTAB) symtab = normalize_dyn_ptr(dyns[i].d_un.d_ptr, load_bias, lib_base);
        else if (dyns[i].d_tag == DT_STRTAB) strtab = normalize_dyn_ptr(dyns[i].d_un.d_ptr, load_bias, lib_base);
        else if (dyns[i].d_tag == DT_HASH) sysv_hash = normalize_dyn_ptr(dyns[i].d_un.d_ptr, load_bias, lib_base);
        else if (dyns[i].d_tag == 0x6ffffef5ULL) gnu_hash = normalize_dyn_ptr(dyns[i].d_un.d_ptr, load_bias, lib_base);
    }
    if (!symtab || !strtab) return 0;

    if (gnu_hash != 0) {
        uint32_t hdr[4];
        if (pread64(mem_fd, hdr, sizeof(hdr), (off64_t)gnu_hash) == (ssize_t)sizeof(hdr)) {
            uint32_t nbuckets = hdr[0];
            uint32_t symoffset = hdr[1];
            uint32_t bloom_size = hdr[2];
            if (nbuckets > 0 && nbuckets < 65536 && bloom_size > 0 && bloom_size < 65536) {
                uintptr_t buckets_addr = gnu_hash + 16 + (size_t)bloom_size * sizeof(uint64_t);
                uintptr_t chain_addr = buckets_addr + (size_t)nbuckets * sizeof(uint32_t);
                uint32_t h = elf_gnu_hash_str(sym_name);
                uint32_t idx = 0;
                if (pread64(mem_fd, &idx, sizeof(idx),
                            (off64_t)(buckets_addr + (h % nbuckets) * sizeof(uint32_t))) == (ssize_t)sizeof(idx) &&
                    idx >= symoffset) {
                    for (int steps = 0; steps < 4096; steps++, idx++) {
                        uint32_t ch = 0;
                        if (pread64(mem_fd, &ch, sizeof(ch),
                                    (off64_t)(chain_addr + (idx - symoffset) * sizeof(uint32_t))) != (ssize_t)sizeof(ch)) {
                            break;
                        }
                        if ((ch | 1u) == (h | 1u)) {
                            Elf64_Sym sym;
                            if (pread64(mem_fd, &sym, sizeof(sym),
                                        (off64_t)(symtab + (size_t)idx * sizeof(Elf64_Sym))) == (ssize_t)sizeof(sym) &&
                                sym.st_name != 0 && sym.st_value != 0) {
                                char sname[128];
                                if (read_remote_cstr(mem_fd, strtab + sym.st_name, sname, sizeof(sname)) == 0 &&
                                    strcmp(sname, sym_name) == 0) {
                                    return load_bias + sym.st_value;
                                }
                            }
                        }
                        if (ch & 1u) break;
                    }
                }
            }
        }
    }

    if (sysv_hash != 0) {
        uint32_t hdr[2];
        if (pread64(mem_fd, hdr, sizeof(hdr), (off64_t)sysv_hash) == (ssize_t)sizeof(hdr)) {
            uint32_t nbucket = hdr[0];
            uint32_t nchain = hdr[1];
            if (nbucket > 0 && nbucket < 65536 && nchain > 0 && nchain < 65536) {
                uint32_t h = elf_sysv_hash_str(sym_name);
                uintptr_t buckets_addr = sysv_hash + 8;
                uintptr_t chain_addr = buckets_addr + (size_t)nbucket * sizeof(uint32_t);
                uint32_t idx = 0;
                if (pread64(mem_fd, &idx, sizeof(idx),
                            (off64_t)(buckets_addr + (h % nbucket) * sizeof(uint32_t))) == (ssize_t)sizeof(idx)) {
                    for (uint32_t steps = 0; idx != 0 && idx < nchain && steps < nchain; steps++) {
                        Elf64_Sym sym;
                        if (pread64(mem_fd, &sym, sizeof(sym),
                                    (off64_t)(symtab + (size_t)idx * sizeof(Elf64_Sym))) == (ssize_t)sizeof(sym) &&
                            sym.st_name != 0 && sym.st_value != 0) {
                            char sname[128];
                            if (read_remote_cstr(mem_fd, strtab + sym.st_name, sname, sizeof(sname)) == 0 &&
                                strcmp(sname, sym_name) == 0) {
                                return load_bias + sym.st_value;
                            }
                        }
                        if (pread64(mem_fd, &idx, sizeof(idx),
                                    (off64_t)(chain_addr + (size_t)idx * sizeof(uint32_t))) != (ssize_t)sizeof(idx)) {
                            break;
                        }
                    }
                }
            }
        }
    }
    return 0;
}

static int decode_remote_vk_dispatch_offset(int mem_fd, uintptr_t fn_addr) {
    if (fn_addr == 0) return -1;
    uint32_t insns[24];
    if (pread64(mem_fd, insns, sizeof(insns), (off64_t)fn_addr) != (ssize_t)sizeof(insns)) {
        return -1;
    }
    uint32_t arg0_reg = 0;
    int reg_r = -1;
    int add_offset = 0;
    for (size_t i = 0; i < 24; i++) {
        uint32_t insn = insns[i];
        /* mov xD, x0: 0xAA0003E0 */
        if ((insn & 0xFFE0FFFFu) == 0xAA0003E0u) {
            arg0_reg = insn & 0x1Fu;
            continue;
        }
        /* ldr xR, [xArg0, #0]: 0xF9400000 | (rn << 5) | rt */
        if ((insn & 0xFFFFFC00u) == 0xF9400000u) {
            uint32_t rn = (insn >> 5) & 0x1Fu;
            uint32_t rt = insn & 0x1Fu;
            if (rn == 0 || rn == arg0_reg) {
                reg_r = (int)rt;
                add_offset = 0;
                continue;
            }
        }
        if (reg_r >= 0) {
            /* add xD, xR, #imm12 (with optional shift) */
            if ((insn & 0xFF000000u) == 0x91000000u) {
                uint32_t rn = (insn >> 5) & 0x1Fu;
                uint32_t rd = insn & 0x1Fu;
                uint32_t sh = (insn >> 22) & 0x1u;
                uint32_t imm12 = (insn >> 10) & 0xFFFu;
                if ((int)rn == reg_r) {
                    reg_r = (int)rd;
                    add_offset += (int)(sh ? (imm12 << 12) : imm12);
                    continue;
                }
            }
            /* ldr xTarget, [xR, #imm12*8] */
            if ((insn & 0xFFC00000u) == 0xF9400000u) {
                uint32_t rn = (insn >> 5) & 0x1Fu;
                uint32_t imm12 = (insn >> 10) & 0xFFFu;
                if ((int)rn == reg_r && (imm12 > 0 || add_offset > 0)) {
                    return add_offset + (int)(imm12 * 8u);
                }
            }
        }
    }
    return -1;
}

static int is_remote_page_resident(int pagemap_fd, uintptr_t vaddr) {
    if (pagemap_fd < 0) return 1;
    uint64_t entry = 0;
    off64_t off = (off64_t)((vaddr / 4096ULL) * sizeof(uint64_t));
    if (pread64(pagemap_fd, &entry, sizeof(entry), off) != (ssize_t)sizeof(entry)) {
        return 0;
    }
    return ((entry >> 63) & 1ULL) != 0;
}

typedef struct {
    uintptr_t start;
    uintptr_t end;
} AddrRange;

static int is_in_ranges(uintptr_t addr, const AddrRange *ranges, int count) {
    for (int i = 0; i < count; i++) {
        if (addr >= ranges[i].start && addr < ranges[i].end) return 1;
    }
    return 0;
}

static uintptr_t find_zero_cave(int mem_fd, uintptr_t start, uintptr_t end, size_t needed_bytes) {
    if (end <= start || (end - start) < needed_bytes) return 0;
    size_t scan_len = end - start;
    if (scan_len > 16384) scan_len = 16384;
    uintptr_t scan_base = end - scan_len;
    uint8_t buf[16384];
    if (pread64(mem_fd, buf, scan_len, (off64_t)scan_base) != (ssize_t)scan_len) {
        return 0;
    }
    /* Search backwards from the end of the VMA, leaving 32 bytes of guard padding */
    if (scan_len < needed_bytes + 64) return 0;
    for (ssize_t pos = (ssize_t)(scan_len - needed_bytes - 32) & ~0xF; pos >= 32; pos -= 16) {
        int all_zero = 1;
        for (size_t k = 0; k < needed_bytes + 32; k++) {
            if (buf[(size_t)pos - 16 + k] != 0) {
                all_zero = 0;
                break;
            }
        }
        if (all_zero) {
            return scan_base + (uintptr_t)pos;
        }
    }
    return 0;
}

static void get_cave_state_file_path(pid_t pid, char *out, size_t out_sz) {
    snprintf(out, out_sz, "/data/local/tmp/megingiard_gfx_cave_%d.state", (int)pid);
}

static int inject_via_proc_mem(pid_t pid) {
    char maps_path[64], mem_path[64], pagemap_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", (int)pid);
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", (int)pid);
    snprintf(pagemap_path, sizeof(pagemap_path), "/proc/%d/pagemap", (int)pid);

    int mem_fd = open(mem_path, O_RDWR);
    if (mem_fd < 0) {
        printf("INJECT_ERR PROC_MEM_OPEN errno=%d\n", errno);
        return 1;
    }
    int pagemap_fd = open(pagemap_path, O_RDONLY);

    FILE *fp = fopen(maps_path, "r");
    if (!fp) {
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        printf("INJECT_ERR MAPS_OPEN\n");
        return 1;
    }

    uintptr_t vk_loader_base = 0;
    AddrRange icd_exec_ranges[64];
    int icd_exec_count = 0;
    int max_exec_ranges = 2048;
    int max_rw_ranges = 8192;
    AddrRange *all_exec_ranges = (AddrRange *)calloc((size_t)max_exec_ranges, sizeof(AddrRange));
    AddrRange *rw_ranges = (AddrRange *)calloc((size_t)max_rw_ranges, sizeof(AddrRange));
    if (!all_exec_ranges || !rw_ranges) {
        free(all_exec_ranges);
        free(rw_ranges);
        fclose(fp);
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        return 1;
    }
    int all_exec_count = 0;
    int rw_count = 0;

    uintptr_t state_cave_addr = 0;
    uintptr_t code_cave_addr = 0;
    size_t code_cave_needed = (size_t)(cave_code_end - cave_code_start);

    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        uintptr_t start = 0, end = 0, offset = 0;
        char perms[8] = {0};
        int path_pos = 0;
        if (sscanf(line, "%lx-%lx %7s %lx %*x:%*x %*d %n", &start, &end, perms, &offset, &path_pos) < 4) {
            continue;
        }
        char *path = line + path_pos;
        while (*path == ' ') path++;
        size_t plen = strlen(path);
        while (plen > 0 && (path[plen - 1] == '\n' || path[plen - 1] == '\r')) path[--plen] = '\0';

        if (vk_loader_base == 0 && offset == 0 && strstr(path, "libvulkan.so") != NULL) {
            vk_loader_base = start;
        }

        if (strchr(perms, 'x') != NULL && strchr(perms, 'p') != NULL) {
            if (all_exec_count < max_exec_ranges) {
                all_exec_ranges[all_exec_count].start = start;
                all_exec_ranges[all_exec_count].end = end;
                all_exec_count++;
            }
            if (strstr(path, ".so") != NULL &&
                strstr(path, "winevulkan") == NULL &&
                (strstr(path, "freedreno") != NULL ||
                 strstr(path, "adreno") != NULL ||
                 strstr(path, "vulkan.") != NULL ||
                 strstr(path, "libvulkan_") != NULL ||
                 strstr(path, "swiftshader") != NULL) &&
                icd_exec_count < 64) {
                icd_exec_ranges[icd_exec_count].start = start;
                icd_exec_ranges[icd_exec_count].end = end;
                icd_exec_count++;
            }
            if (code_cave_addr == 0 && strstr(path, ".so") != NULL && (end - start) >= 4096) {
                code_cave_addr = find_zero_cave(mem_fd, start, end, code_cave_needed);
            }
        } else if (strncmp(perms, "rw-p", 4) == 0) {
            if (state_cave_addr == 0 && (end - start) >= 4096 && strstr(path, ".so") != NULL) {
                state_cave_addr = find_zero_cave(mem_fd, start, end, sizeof(MemCaveState));
            }
            /* Only scan anonymous/heap mappings (path[0] != '/') for live dispatch tables */
            if (path[0] != '/' &&
                strstr(path, "[stack") == NULL &&
                (end - start) <= (256ULL << 20) &&
                rw_count < max_rw_ranges) {
                rw_ranges[rw_count].start = start;
                rw_ranges[rw_count].end = end;
                rw_count++;
            }
        }
    }
    fclose(fp);

    if (vk_loader_base == 0) {
        free(all_exec_ranges);
        free(rw_ranges);
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        printf("INJECT_ERR NO_VULKAN_LOADER\n");
        return 1;
    }

    const AddrRange *target_code_ranges = icd_exec_count > 0 ? icd_exec_ranges : all_exec_ranges;
    int target_code_count = icd_exec_count > 0 ? icd_exec_count : all_exec_count;

    uint8_t page_buf[8192];

    /* Check if cave state was already installed in this process */
    char state_file[128];
    get_cave_state_file_path(pid, state_file, sizeof(state_file));
    MemCaveState prev_cave;
    memset(&prev_cave, 0, sizeof(prev_cave));
    uintptr_t prev_code_addr = 0;
    int has_prev_cave = 0;
    FILE *sf = fopen(state_file, "r");
    if (sf) {
        unsigned long existing_state = 0, existing_code = 0;
        if (fscanf(sf, "%lx %lx", &existing_state, &existing_code) == 2 && existing_state != 0) {
            if (pread64(mem_fd, &prev_cave, sizeof(prev_cave), (off64_t)existing_state) == (ssize_t)sizeof(prev_cave) &&
                prev_cave.magic == CAVE_MAGIC) {
                if (prev_cave.node_count > 0 &&
                    is_in_ranges(prev_cave.real_CmdDraw, target_code_ranges, target_code_count)) {
                    fclose(sf);
                    free(all_exec_ranges);
                    free(rw_ranges);
                    if (pagemap_fd >= 0) close(pagemap_fd);
                    close(mem_fd);
                    printf("INJECT_OK handle=0x%lx\n", existing_state);
                    return 0;
                }
                state_cave_addr = (uintptr_t)existing_state;
                code_cave_addr = (uintptr_t)existing_code;
                prev_code_addr = (uintptr_t)existing_code;
                has_prev_cave = 1;
            }
        }
        fclose(sf);
    }

    if (state_cave_addr == 0 || code_cave_addr == 0) {
        free(all_exec_ranges);
        free(rw_ranges);
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        printf("INJECT_ERR NO_CAVE_SPACE state=0x%lx code=0x%lx\n",
                (unsigned long)state_cave_addr, (unsigned long)code_cave_addr);
        return 1;
    }

    /* If a previous cave was installed, unpatch its hooks first so we can cleanly inspect or re-hook */
    if (has_prev_cave && prev_code_addr != 0) {
        #define PREV_HOOK_ADDR(sym) (prev_code_addr + (uintptr_t)((sym) - cave_code_start))
        struct {
            uint64_t real_fn;
            uint64_t hook_fn;
        } prev_pairs[15] = {
            { prev_cave.real_CmdBeginRenderPass,     PREV_HOOK_ADDR(cave_hook_CmdBeginRenderPass) },
            { prev_cave.real_CmdBeginRenderPass2,    PREV_HOOK_ADDR(cave_hook_CmdBeginRenderPass2) },
            { prev_cave.real_CmdEndRenderPass,       PREV_HOOK_ADDR(cave_hook_CmdEndRenderPass) },
            { prev_cave.real_CmdEndRenderPass2,      PREV_HOOK_ADDR(cave_hook_CmdEndRenderPass2) },
            { prev_cave.real_CmdBeginRendering,      PREV_HOOK_ADDR(cave_hook_CmdBeginRendering) },
            { prev_cave.real_CmdEndRendering,        PREV_HOOK_ADDR(cave_hook_CmdEndRendering) },
            { prev_cave.real_CmdBindPipeline,        PREV_HOOK_ADDR(cave_hook_CmdBindPipeline) },
            { prev_cave.real_CmdDraw,                PREV_HOOK_ADDR(cave_hook_CmdDraw) },
            { prev_cave.real_CmdDrawIndexed,         PREV_HOOK_ADDR(cave_hook_CmdDrawIndexed) },
            { prev_cave.real_CmdDrawIndirect,        PREV_HOOK_ADDR(cave_hook_CmdDrawIndirect) },
            { prev_cave.real_CmdDrawIndexedIndirect, PREV_HOOK_ADDR(cave_hook_CmdDrawIndexedIndirect) },
            { prev_cave.real_CmdDispatch,            PREV_HOOK_ADDR(cave_hook_CmdDispatch) },
            { prev_cave.real_QueueSubmit,            PREV_HOOK_ADDR(cave_hook_QueueSubmit) },
            { prev_cave.real_QueueSubmit2,           PREV_HOOK_ADDR(cave_hook_QueueSubmit2) },
            { prev_cave.real_QueuePresentKHR,        PREV_HOOK_ADDR(cave_hook_QueuePresentKHR) },
        };
        #undef PREV_HOOK_ADDR
        for (int r = rw_count - 1; r >= 0; r--) {
            for (uintptr_t page = rw_ranges[r].start; page < rw_ranges[r].end; page += 4096) {
                if (!is_remote_page_resident(pagemap_fd, page)) continue;
                if (pread64(mem_fd, page_buf, 4096, (off64_t)page) != 4096) continue;
                for (size_t off = 0; off < 4096; off += 8) {
                    uintptr_t slot_addr = page + off;
                    if (slot_addr >= state_cave_addr && slot_addr < state_cave_addr + sizeof(MemCaveState)) {
                        continue;
                    }
                    uint64_t val = *(uint64_t *)(page_buf + off);
                    for (int k = 0; k < 15; k++) {
                        if (prev_pairs[k].real_fn != 0 && val == prev_pairs[k].hook_fn) {
                            (void)pwrite64(mem_fd, &prev_pairs[k].real_fn, sizeof(uint64_t), (off64_t)slot_addr);
                            break;
                        }
                    }
                }
            }
        }
    }

    int off_begin_rp  = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdBeginRenderPass"));
    int off_begin_rp2 = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdBeginRenderPass2"));
    int off_begin_rp2_khr = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdBeginRenderPass2KHR"));
    int off_end_rp    = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdEndRenderPass"));
    int off_end_rp2   = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdEndRenderPass2"));
    int off_end_rp2_khr = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdEndRenderPass2KHR"));
    int off_begin_dyn = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdBeginRendering"));
    int off_begin_dyn_khr = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdBeginRenderingKHR"));
    int off_end_dyn   = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdEndRendering"));
    int off_end_dyn_khr = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdEndRenderingKHR"));
    int off_bind_pipe = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdBindPipeline"));
    int off_draw      = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDraw"));
    int off_draw_idx  = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDrawIndexed"));
    int off_draw_ind  = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDrawIndirect"));
    int off_draw_idx_ind = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDrawIndexedIndirect"));
    int off_dispatch  = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDispatch"));
    int off_qsubmit   = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkQueueSubmit"));
    int off_qsubmit2  = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkQueueSubmit2"));
    int off_qsubmit2_khr = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkQueueSubmit2KHR"));
    int off_qpresent  = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkQueuePresentKHR"));

    if (off_draw < 0 || off_draw_idx < 0 || off_bind_pipe < 0) {
        free(all_exec_ranges);
        free(rw_ranges);
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        printf("INJECT_ERR VK_OFFSET_DECODE draw=%d idx=%d bind=%d\n",
                off_draw, off_draw_idx, off_bind_pipe);
        return 1;
    }

    MemCaveState cave_state;
    memset(&cave_state, 0, sizeof(cave_state));
    cave_state.magic = CAVE_MAGIC;
    cave_state.api = 2; /* VULKAN */

    int found_table = 0;

    /* Pass 1: Look for canonical Khronos loader magic (0x10ADED040410ADED) at table base +0x0 */
    for (int require_magic = 1; require_magic >= 0 && !found_table; require_magic--) {
        for (int r = rw_count - 1; r >= 0 && !found_table; r--) {
            for (uintptr_t page = rw_ranges[r].start; page < rw_ranges[r].end; page += 4096) {
                if (!is_remote_page_resident(pagemap_fd, page)) continue;
                size_t read_len = 4096;
                if (page + 4096 < rw_ranges[r].end && is_remote_page_resident(pagemap_fd, page + 4096)) {
                    read_len = 8192;
                }
                if (pread64(mem_fd, page_buf, read_len, (off64_t)page) != (ssize_t)read_len) continue;

                for (size_t off = 0; off + 2048 <= read_len; off += 8) {
                    if (require_magic && *(uint64_t *)(page_buf + off) != 0x10ADED040410ADEDULL) {
                        continue;
                    }
                    uint64_t p_draw = *(uint64_t *)(page_buf + off + (size_t)off_draw);
                    uint64_t p_idx  = *(uint64_t *)(page_buf + off + (size_t)off_draw_idx);
                    uint64_t p_bind = *(uint64_t *)(page_buf + off + (size_t)off_bind_pipe);
                    if (!is_in_ranges(p_draw, target_code_ranges, target_code_count)) continue;
                    if (!is_in_ranges(p_idx, target_code_ranges, target_code_count)) continue;
                    if (!is_in_ranges(p_bind, target_code_ranges, target_code_count)) continue;
                    if (p_draw == p_idx || p_draw == p_bind || p_idx == p_bind) continue;
                    if ((p_idx > p_draw && (p_idx - p_draw) == 0x10ULL) ||
                        (p_draw > p_idx && (p_draw - p_idx) == 0x10ULL)) {
                        continue;
                    }

                    #define READ_VERIFIED_SLOT(slot_off) \
                        (((slot_off) >= 0 && off + (size_t)(slot_off) + 8 <= read_len && \
                          is_in_ranges(*(uint64_t *)(page_buf + off + (size_t)(slot_off)), target_code_ranges, target_code_count)) ? \
                            *(uint64_t *)(page_buf + off + (size_t)(slot_off)) : 0ULL)

                    uint64_t p_begin_rp = READ_VERIFIED_SLOT(off_begin_rp);
                    uint64_t p_begin_dyn = READ_VERIFIED_SLOT(off_begin_dyn);
                    if (p_begin_dyn == 0) p_begin_dyn = READ_VERIFIED_SLOT(off_begin_dyn_khr);
                    uint64_t p_end_rp = READ_VERIFIED_SLOT(off_end_rp);
                    if (p_begin_rp == 0 && p_begin_dyn == 0 && p_end_rp == 0) {
                        continue;
                    }

                    cave_state.real_CmdDraw = p_draw;
                    cave_state.real_CmdDrawIndexed = p_idx;
                    cave_state.real_CmdBindPipeline = p_bind;
                    cave_state.real_CmdBeginRenderPass = p_begin_rp;
                    cave_state.real_CmdBeginRenderPass2 = READ_VERIFIED_SLOT(off_begin_rp2);
                    if (cave_state.real_CmdBeginRenderPass2 == 0) {
                        cave_state.real_CmdBeginRenderPass2 = READ_VERIFIED_SLOT(off_begin_rp2_khr);
                    }
                    cave_state.real_CmdEndRenderPass = p_end_rp;
                    cave_state.real_CmdEndRenderPass2 = READ_VERIFIED_SLOT(off_end_rp2);
                    if (cave_state.real_CmdEndRenderPass2 == 0) {
                        cave_state.real_CmdEndRenderPass2 = READ_VERIFIED_SLOT(off_end_rp2_khr);
                    }
                    cave_state.real_CmdBeginRendering = p_begin_dyn;
                    cave_state.real_CmdEndRendering = READ_VERIFIED_SLOT(off_end_dyn);
                    if (cave_state.real_CmdEndRendering == 0) {
                        cave_state.real_CmdEndRendering = READ_VERIFIED_SLOT(off_end_dyn_khr);
                    }
                    cave_state.real_CmdDrawIndirect = READ_VERIFIED_SLOT(off_draw_ind);
                    cave_state.real_CmdDrawIndexedIndirect = READ_VERIFIED_SLOT(off_draw_idx_ind);
                    cave_state.real_CmdDispatch = READ_VERIFIED_SLOT(off_dispatch);
                    cave_state.real_QueueSubmit = READ_VERIFIED_SLOT(off_qsubmit);
                    cave_state.real_QueueSubmit2 = READ_VERIFIED_SLOT(off_qsubmit2);
                    if (cave_state.real_QueueSubmit2 == 0) {
                        cave_state.real_QueueSubmit2 = READ_VERIFIED_SLOT(off_qsubmit2_khr);
                    }
                    cave_state.real_QueuePresentKHR = READ_VERIFIED_SLOT(off_qpresent);
                    #undef READ_VERIFIED_SLOT

                    found_table = 1;
                    break;
                }
                if (found_table) break;
            }
        }
    }

    if (!found_table) {
        free(all_exec_ranges);
        free(rw_ranges);
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        printf("INJECT_ERR NO_LIVE_VK_DEVICE rw_count=%d icd_count=%d\n", rw_count, icd_exec_count);
        return 1;
    }

    /* Stage MemCaveState and position-independent ARM64 cave hooks into target memory */
    uint8_t *code_buf = (uint8_t *)malloc(code_cave_needed);
    if (!code_buf) {
        free(all_exec_ranges);
        free(rw_ranges);
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        return 1;
    }
    memcpy(code_buf, cave_code_start, code_cave_needed);
    *(uint64_t *)code_buf = (uint64_t)state_cave_addr;

    if (pwrite64(mem_fd, &cave_state, sizeof(cave_state), (off64_t)state_cave_addr) != (ssize_t)sizeof(cave_state) ||
        pwrite64(mem_fd, code_buf, code_cave_needed, (off64_t)code_cave_addr) != (ssize_t)code_cave_needed) {
        free(code_buf);
        free(all_exec_ranges);
        free(rw_ranges);
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        printf("INJECT_ERR CAVE_WRITE_FAILED errno=%d\n", errno);
        return 1;
    }
    free(code_buf);

    #define CAVE_HOOK_ADDR(sym) (code_cave_addr + (uintptr_t)((sym) - cave_code_start))
    struct {
        uint64_t real_fn;
        uint64_t hook_fn;
    } hook_pairs[15] = {
        { cave_state.real_CmdBeginRenderPass,     CAVE_HOOK_ADDR(cave_hook_CmdBeginRenderPass) },
        { cave_state.real_CmdBeginRenderPass2,    CAVE_HOOK_ADDR(cave_hook_CmdBeginRenderPass2) },
        { cave_state.real_CmdEndRenderPass,       CAVE_HOOK_ADDR(cave_hook_CmdEndRenderPass) },
        { cave_state.real_CmdEndRenderPass2,      CAVE_HOOK_ADDR(cave_hook_CmdEndRenderPass2) },
        { cave_state.real_CmdBeginRendering,      CAVE_HOOK_ADDR(cave_hook_CmdBeginRendering) },
        { cave_state.real_CmdEndRendering,        CAVE_HOOK_ADDR(cave_hook_CmdEndRendering) },
        { cave_state.real_CmdBindPipeline,        CAVE_HOOK_ADDR(cave_hook_CmdBindPipeline) },
        { cave_state.real_CmdDraw,                CAVE_HOOK_ADDR(cave_hook_CmdDraw) },
        { cave_state.real_CmdDrawIndexed,         CAVE_HOOK_ADDR(cave_hook_CmdDrawIndexed) },
        { cave_state.real_CmdDrawIndirect,        CAVE_HOOK_ADDR(cave_hook_CmdDrawIndirect) },
        { cave_state.real_CmdDrawIndexedIndirect, CAVE_HOOK_ADDR(cave_hook_CmdDrawIndexedIndirect) },
        { cave_state.real_CmdDispatch,            CAVE_HOOK_ADDR(cave_hook_CmdDispatch) },
        { cave_state.real_QueueSubmit,            CAVE_HOOK_ADDR(cave_hook_QueueSubmit) },
        { cave_state.real_QueueSubmit2,           CAVE_HOOK_ADDR(cave_hook_QueueSubmit2) },
        { cave_state.real_QueuePresentKHR,        CAVE_HOOK_ADDR(cave_hook_QueuePresentKHR) },
    };
    #undef CAVE_HOOK_ADDR

    int patched_slots = 0;
    for (int r = rw_count - 1; r >= 0; r--) {
        for (uintptr_t page = rw_ranges[r].start; page < rw_ranges[r].end; page += 4096) {
            if (!is_remote_page_resident(pagemap_fd, page)) continue;
            if (pread64(mem_fd, page_buf, 4096, (off64_t)page) != 4096) continue;

            for (size_t off = 0; off < 4096; off += 8) {
                uintptr_t slot_addr = page + off;
                if (slot_addr >= state_cave_addr && slot_addr < state_cave_addr + sizeof(MemCaveState)) {
                    continue;
                }
                uint64_t val = *(uint64_t *)(page_buf + off);
                int match_k = -1;
                for (int k = 0; k < 15; k++) {
                    if (hook_pairs[k].real_fn != 0 && val == hook_pairs[k].real_fn) {
                        match_k = k;
                        break;
                    }
                }
                if (match_k < 0) continue;

                /* Verify at least 2 sibling Vulkan pointers exist in the same page */
                int siblings = 0;
                for (size_t s = 0; s < 4096; s += 8) {
                    if (s == off) continue;
                    uint64_t sval = *(uint64_t *)(page_buf + s);
                    for (int k = 0; k < 15; k++) {
                        if (hook_pairs[k].real_fn != 0 &&
                            (sval == hook_pairs[k].real_fn || sval == hook_pairs[k].hook_fn)) {
                            siblings++;
                            break;
                        }
                    }
                    if (siblings >= 2) break;
                }
                if (siblings >= 2) {
                    uint64_t new_val = hook_pairs[match_k].hook_fn;
                    if (pwrite64(mem_fd, &new_val, sizeof(new_val), (off64_t)slot_addr) == (ssize_t)sizeof(new_val)) {
                        *(uint64_t *)(page_buf + off) = new_val;
                        patched_slots++;
                    }
                }
            }
        }
    }

    free(all_exec_ranges);
    free(rw_ranges);
    if (pagemap_fd >= 0) close(pagemap_fd);
    close(mem_fd);

    if (patched_slots == 0) {
        fprintf(stderr, "INJECT_ERR NO_SLOTS_PATCHED\n");
        return 1;
    }

    FILE *out_sf = fopen(state_file, "w");
    if (out_sf) {
        fprintf(out_sf, "%lx %lx\n", (unsigned long)state_cave_addr, (unsigned long)code_cave_addr);
        fclose(out_sf);
        (void)chmod(state_file, 0666);
    }

    printf("INJECT_OK handle=0x%lx patched=%d\n", (unsigned long)state_cave_addr, patched_slots);
    return 0;
}

static int handle_cave_rpc(pid_t pid, int argc, char **argv) {
    char state_file[128];
    get_cave_state_file_path(pid, state_file, sizeof(state_file));
    FILE *sf = fopen(state_file, "r");
    if (!sf) return -1;

    unsigned long state_addr = 0, code_addr = 0;
    int scanned = fscanf(sf, "%lx %lx", &state_addr, &code_addr);
    fclose(sf);
    if (scanned < 1 || state_addr == 0) return -1;

    char mem_path[64];
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", (int)pid);
    int mem_fd = open(mem_path, O_RDWR);
    if (mem_fd < 0) return -1;

    MemCaveState state;
    if (pread64(mem_fd, &state, sizeof(state), (off64_t)state_addr) != (ssize_t)sizeof(state) ||
        state.magic != CAVE_MAGIC) {
        close(mem_fd);
        return -1;
    }

    const char *cmd = argv[3];
    if (strcmp(cmd, "PING") == 0) {
        close(mem_fd);
        printf("PONG\n");
        return 0;
    }

    if (strcmp(cmd, "LIST") == 0) {
        close(mem_fd);
        printf("API|VULKAN\n");
        uint32_t count = state.node_count;
        if (count > CAVE_MAX_NODES) count = CAVE_MAX_NODES;
        for (uint32_t i = 0; i < count; i++) {
            const CaveNode *n = &state.nodes[i];
            if (n->handle == 0) continue;
            unsigned long long h = (unsigned long long)n->handle;
            unsigned long long h32 = h & 0xFFFFFFFFULL;
            unsigned int draws = (unsigned int)n->draws;
            unsigned int en = n->enabled ? 1u : 0u;
            if (n->category == 1) {
                printf("NODE|RP_0x%llx|RENDER_PASS|VULKAN|%08llX|Active|%u|%u|Vulkan RenderPass (0x%llx)\n",
                       h, h32, draws, en, h);
            } else if (n->category == 2) {
                printf("NODE|PIPE_0x%llx|PIPELINE|VULKAN|%08llX|Active|%u|%u|Vulkan Pipeline (0x%llx)\n",
                       h, h32, draws, en, h);
                printf("NODE|VS_SYNTH_0x%llx|VERTEX_SHADER|VULKAN|%08llX|Active|%u|%u|Vertex Shader (Pipeline 0x%llx)\n",
                       h, h32, draws, en, h);
                printf("NODE|FS_SYNTH_0x%llx|FRAGMENT_SHADER|VULKAN|%08llX|Active|%u|%u|Fragment Shader (Pipeline 0x%llx)\n",
                       h, h32, draws, en, h);
            }
        }
        printf("END\n");
        return 0;
    }

    if (strcmp(cmd, "SET") == 0 && argc >= 6) {
        const char *node_id = argv[4];
        uint16_t en = (uint16_t)(atoi(argv[5]) != 0 ? 1u : 0u);
        const char *hex_part = strstr(node_id, "0x");
        uint64_t target_handle = hex_part ? strtoull(hex_part, NULL, 16) : 0;
        uint32_t count = state.node_count;
        if (count > CAVE_MAX_NODES) count = CAVE_MAX_NODES;
        for (uint32_t i = 0; i < count; i++) {
            if (state.nodes[i].handle == target_handle && target_handle != 0) {
                off64_t en_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                           (size_t)i * sizeof(CaveNode) + offsetof(CaveNode, enabled));
                (void)pwrite64(mem_fd, &en, sizeof(en), en_off);
            }
        }
        close(mem_fd);
        printf("OK\n");
        return 0;
    }

    if (strcmp(cmd, "SET_ALL") == 0 && argc >= 5) {
        uint16_t en = (uint16_t)(atoi(argv[4]) != 0 ? 1u : 0u);
        uint32_t count = state.node_count;
        if (count > CAVE_MAX_NODES) count = CAVE_MAX_NODES;
        for (uint32_t i = 0; i < count; i++) {
            if (state.nodes[i].handle != 0) {
                off64_t en_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                           (size_t)i * sizeof(CaveNode) + offsetof(CaveNode, enabled));
                (void)pwrite64(mem_fd, &en, sizeof(en), en_off);
            }
        }
        close(mem_fd);
        printf("OK\n");
        return 0;
    }

    if (strcmp(cmd, "INVERT") == 0) {
        uint32_t count = state.node_count;
        if (count > CAVE_MAX_NODES) count = CAVE_MAX_NODES;
        for (uint32_t i = 0; i < count; i++) {
            if (state.nodes[i].handle != 0) {
                uint16_t en = state.nodes[i].enabled ? 0u : 1u;
                off64_t en_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                           (size_t)i * sizeof(CaveNode) + offsetof(CaveNode, enabled));
                (void)pwrite64(mem_fd, &en, sizeof(en), en_off);
            }
        }
        close(mem_fd);
        printf("OK\n");
        return 0;
    }

    close(mem_fd);
    return -1;
}

static int get_regs(pid_t pid, struct user_pt_regs *regs) {
    struct iovec iov;
    iov.iov_base = regs;
    iov.iov_len = sizeof(*regs);
    return ptrace(PTRACE_GETREGSET, pid, (void *)NT_PRSTATUS, &iov);
}

static int set_regs(pid_t pid, const struct user_pt_regs *regs) {
    struct iovec iov;
    iov.iov_base = (void *)regs;
    iov.iov_len = sizeof(*regs);
    return ptrace(PTRACE_SETREGSET, pid, (void *)NT_PRSTATUS, &iov);
}

static int read_remote_mem(pid_t pid, uintptr_t addr, void *buf, size_t len) {
    uint8_t *dst = (uint8_t *)buf;
    size_t offset = 0;
    while (offset < len) {
        errno = 0;
        long word = ptrace(PTRACE_PEEKDATA, pid, (void *)(addr + offset), NULL);
        if (errno != 0) return -1;
        size_t chunk = len - offset;
        if (chunk > sizeof(long)) chunk = sizeof(long);
        memcpy(dst + offset, &word, chunk);
        offset += sizeof(long);
    }
    return 0;
}

static int write_remote_mem(pid_t pid, uintptr_t addr, const void *buf, size_t len) {
    const uint8_t *src = (const uint8_t *)buf;
    size_t offset = 0;
    while (offset < len) {
        long word = 0;
        size_t chunk = len - offset;
        if (chunk > sizeof(long)) chunk = sizeof(long);
        if (chunk < sizeof(long)) {
            errno = 0;
            word = ptrace(PTRACE_PEEKDATA, pid, (void *)(addr + offset), NULL);
            if (errno != 0) return -1;
        }
        memcpy(&word, src + offset, chunk);
        if (ptrace(PTRACE_POKEDATA, pid, (void *)(addr + offset), (void *)word) < 0) {
            return -1;
        }
        offset += sizeof(long);
    }
    return 0;
}

static int call_remote_func(
    pid_t pid,
    const struct user_pt_regs *orig_regs,
    uintptr_t call_sp,
    uintptr_t func_addr,
    const uintptr_t *args,
    int argc,
    uintptr_t *ret_val
) {
    struct user_pt_regs regs = *orig_regs;
    for (int i = 0; i < argc && i < 8; i++) {
        regs.regs[i] = args[i];
    }
    regs.sp = call_sp;
    regs.pc = func_addr;
    /* Set LR to 0 so returning triggers SIGSEGV at 0x0 and traps back to ptrace */
    regs.regs[30] = 0;
    /* Clear ARMv9 BTI branch-type state and single-step bit */
    regs.pstate &= ~(ARM64_PSR_BTYPE_MASK | ARM64_PSR_SS_BIT);

    if (set_regs(pid, &regs) < 0) return -1;
    if (ptrace(PTRACE_CONT, pid, NULL, NULL) < 0) return -1;

    int status = 0;
    while (waitpid(pid, &status, WUNTRACED) == pid) {
        if (WIFSTOPPED(status)) {
            int sig = WSTOPSIG(status);
            struct user_pt_regs cur;
            if (get_regs(pid, &cur) == 0) {
                uintptr_t stripped_pc = cur.pc & 0x00FFFFFFFFFFFFFFULL;
                if (sig == SIGSEGV && stripped_pc == 0) {
                    if (ret_val) *ret_val = cur.regs[0];
                    return 0;
                }
                if (sig != SIGSEGV && sig != SIGILL && sig != SIGBUS && sig != SIGFPE && sig != SIGTRAP) {
                    if (cur.pc == orig_regs->pc) {
                        (void)set_regs(pid, &regs);
                    }
                    if (ptrace(PTRACE_CONT, pid, NULL, NULL) < 0) return -1;
                    continue;
                }
                fprintf(stderr, "INJECT_TRAP sig=%d pc=0x%lx lr=0x%lx x0=0x%lx func=0x%lx\n",
                        sig, (unsigned long)cur.pc, (unsigned long)cur.regs[30],
                        (unsigned long)cur.regs[0], (unsigned long)func_addr);
            }
            return -1;
        }
        break;
    }
    return -1;
}

static void apply_selinux_lib_label(const char *path) {
    (void)chmod(path, 0755);
    (void)setxattr(
        path,
        "security.selinux",
        SELINUX_SYS_LIB_CTX,
        strlen(SELINUX_SYS_LIB_CTX) + 1,
        0
    );
}

static int stage_so_into_app_cache(pid_t pid, const char *src_so_path, char *out_path, size_t out_sz) {
    char cmdline_path[64];
    snprintf(cmdline_path, sizeof(cmdline_path), "/proc/%d/cmdline", (int)pid);
    FILE *fp = fopen(cmdline_path, "r");
    if (!fp) return -1;

    char pkg[128] = {0};
    size_t n = fread(pkg, 1, sizeof(pkg) - 1, fp);
    fclose(fp);
    if (n == 0 || pkg[0] == '\0') return -1;

    for (size_t i = 0; i < n && pkg[i] != '\0'; i++) {
        if (pkg[i] == ':' || pkg[i] == ' ' || pkg[i] == '/') {
            pkg[i] = '\0';
            break;
        }
    }
    if (pkg[0] == '\0') return -1;

    snprintf(out_path, out_sz, "/data/data/%s/cache/libVkLayer_MegingiardInspector.so", pkg);
    int in_fd = open(src_so_path, O_RDONLY);
    if (in_fd < 0) return -1;

    int out_fd = open(out_path, O_WRONLY | O_CREAT | O_TRUNC, 0755);
    if (out_fd < 0) {
        snprintf(out_path, out_sz, "/data/data/%s/libVkLayer_MegingiardInspector.so", pkg);
        out_fd = open(out_path, O_WRONLY | O_CREAT | O_TRUNC, 0755);
        if (out_fd < 0) {
            close(in_fd);
            return -1;
        }
    }

    char buf[4096];
    ssize_t r;
    int ok = 0;
    while ((r = read(in_fd, buf, sizeof(buf))) > 0) {
        ssize_t w = write(out_fd, buf, (size_t)r);
        if (w != r) {
            ok = -1;
            break;
        }
    }
    close(in_fd);
    close(out_fd);

    if (ok == 0) {
        apply_selinux_lib_label(out_path);
    }
    return ok;
}

static int handle_probe_api(pid_t pid) {
    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", (int)pid);
    FILE *fp = fopen(maps_path, "r");
    if (!fp) {
        printf("UNKNOWN\n");
        return 1;
    }
    int has_vk = 0;
    int has_gles = 0;
    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "libvulkan.so") ||
            strstr(line, "libvulkan_") ||
            strstr(line, "vulkan.") ||
            strstr(line, "winevulkan")) {
            has_vk = 1;
        }
        if (strstr(line, "libGLESv2") || strstr(line, "libGLESv3") || strstr(line, "libGLES_")) {
            has_gles = 1;
        }
        if (has_vk && has_gles) break;
    }
    fclose(fp);

    if (has_vk && has_gles) printf("HYBRID\n");
    else if (has_vk) printf("VULKAN\n");
    else if (has_gles) printf("OPENGL_ES\n");
    else printf("UNKNOWN\n");
    return 0;
}

static int handle_unix_rpc(pid_t pid, int argc, char **argv) {
    if (handle_cave_rpc(pid, argc, argv) == 0) {
        return 0;
    }

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        printf("RPC_ERR SOCKET\n");
        return 1;
    }

    struct timeval tv;
    tv.tv_sec = 1;
    tv.tv_usec = 500000;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    int name_len = snprintf(addr.sun_path + 1, sizeof(addr.sun_path) - 2, "megingiard_gfx_%d", (int)pid);
    socklen_t sock_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + (size_t)name_len);

    if (connect(fd, (struct sockaddr *)&addr, sock_len) < 0) {
        close(fd);
        printf("RPC_ERR CONNECT errno=%d\n", errno);
        return 1;
    }

    char cmd_buf[256] = {0};
    size_t pos = 0;
    for (int i = 3; i < argc; i++) {
        int written = snprintf(
            cmd_buf + pos,
            sizeof(cmd_buf) - pos,
            "%s%s",
            (i > 3 ? " " : ""),
            argv[i]
        );
        if (written > 0) pos += (size_t)written;
    }
    if (pos + 2 < sizeof(cmd_buf)) {
        cmd_buf[pos++] = '\n';
        cmd_buf[pos] = '\0';
    }

    if (write(fd, cmd_buf, pos) != (ssize_t)pos) {
        close(fd);
        printf("RPC_ERR WRITE\n");
        return 1;
    }

    char read_buf[1024];
    ssize_t n;
    while ((n = read(fd, read_buf, sizeof(read_buf))) > 0) {
        (void)fwrite(read_buf, 1, (size_t)n, stdout);
    }
    fflush(stdout);
    close(fd);
    return 0;
}

int main(int argc, char **argv) {
    if (argc >= 3 && strcmp(argv[1], "--probe-api") == 0) {
        pid_t pid = (pid_t)atoi(argv[2]);
        return handle_probe_api(pid);
    }

    if (argc >= 4 && strcmp(argv[1], "--rpc") == 0) {
        pid_t pid = (pid_t)atoi(argv[2]);
        return handle_unix_rpc(pid, argc, argv);
    }

    if (argc < 3) {
        fprintf(stderr, "INJECT_ERR USAGE\n");
        return 1;
    }

    pid_t pid = (pid_t)atoi(argv[1]);
    const char *so_path = argv[2];
    if (pid <= 0 || so_path[0] != '/') {
        fprintf(stderr, "INJECT_ERR INVALID_ARGS\n");
        return 1;
    }

    apply_selinux_lib_label(so_path);
    char staged_app_so[256] = {0};
    int has_staged_app_so = (stage_so_into_app_cache(pid, so_path, staged_app_so, sizeof(staged_app_so)) == 0);

    void *local_loader_dlopen = dlsym(RTLD_DEFAULT, "__loader_dlopen");
    uintptr_t remote_loader_dlopen = resolve_remote_symbol_exact(pid, local_loader_dlopen);
    uintptr_t remote_dlopen = resolve_remote_symbol_exact(pid, (void *)dlopen);
    uintptr_t remote_dlerror = resolve_remote_symbol_exact(pid, (void *)dlerror);
    uintptr_t remote_caller_addr = find_system_namespace_caller_addr(pid);

    if (remote_loader_dlopen == 0 && remote_dlopen == 0) {
        return inject_via_proc_mem(pid);
    }

    if (ptrace(PTRACE_ATTACH, pid, NULL, NULL) < 0) {
        return inject_via_proc_mem(pid);
    }

    int status = 0;
    if (waitpid(pid, &status, WUNTRACED) != pid || !WIFSTOPPED(status)) {
        ptrace(PTRACE_DETACH, pid, NULL, NULL);
        fprintf(stderr, "INJECT_ERR WAITPID_FAILED\n");
        return 1;
    }

    uint64_t saved_sigmask = 0;
    int has_sigmask = (ptrace(PTRACE_GETSIGMASK, pid, (void *)sizeof(uint64_t), &saved_sigmask) == 0);
    if (has_sigmask) {
        uint64_t block_mask = ~(
            (1ULL << (SIGSEGV - 1)) |
            (1ULL << (SIGTRAP - 1)) |
            (1ULL << (SIGILL - 1)) |
            (1ULL << (SIGBUS - 1)) |
            (1ULL << (SIGFPE - 1)) |
            (1ULL << (SIGKILL - 1)) |
            (1ULL << (SIGSTOP - 1))
        );
        (void)ptrace(PTRACE_SETSIGMASK, pid, (void *)sizeof(uint64_t), &block_mask);
    }

    struct user_pt_regs saved_regs;
    if (get_regs(pid, &saved_regs) < 0) {
        if (has_sigmask) (void)ptrace(PTRACE_SETSIGMASK, pid, (void *)sizeof(uint64_t), &saved_sigmask);
        ptrace(PTRACE_DETACH, pid, NULL, NULL);
        fprintf(stderr, "INJECT_ERR GETREGS_FAILED errno=%d\n", errno);
        return 1;
    }

    int saved_syscall = -1;
    struct iovec sys_iov = { &saved_syscall, sizeof(saved_syscall) };
    int has_syscall = (ptrace(PTRACE_GETREGSET, pid, (void *)NT_ARM_SYSTEM_CALL, &sys_iov) == 0);
    if (has_syscall && saved_syscall != -1) {
        int no_sys = -1;
        struct iovec no_sys_iov = { &no_sys, sizeof(no_sys) };
        (void)ptrace(PTRACE_SETREGSET, pid, (void *)NT_ARM_SYSTEM_CALL, &no_sys_iov);
    }

    uintptr_t scratch_addr = (saved_regs.sp - STACK_SCRATCH_OFFSET) & ~0xFUL;
    uintptr_t call_sp = (scratch_addr - STACK_CALL_REDZONE) & ~0xFUL;
    uint8_t saved_stack[STACK_SCRATCH_SIZE];

    if (read_remote_mem(pid, scratch_addr, saved_stack, sizeof(saved_stack)) < 0) {
        if (has_syscall && saved_syscall != -1) {
            (void)ptrace(PTRACE_SETREGSET, pid, (void *)NT_ARM_SYSTEM_CALL, &sys_iov);
        }
        set_regs(pid, &saved_regs);
        if (has_sigmask) (void)ptrace(PTRACE_SETSIGMASK, pid, (void *)sizeof(uint64_t), &saved_sigmask);
        ptrace(PTRACE_DETACH, pid, NULL, NULL);
        fprintf(stderr, "INJECT_ERR STACK_PEEK_FAILED\n");
        return 1;
    }

    const char *candidates[2];
    int num_candidates = 0;
    if (has_staged_app_so) {
        candidates[num_candidates++] = staged_app_so;
    }
    candidates[num_candidates++] = so_path;

    uintptr_t so_handle = 0;
    int dl_rc = -1;

    for (int c = 0; c < num_candidates; c++) {
        const char *cand = candidates[c];
        size_t path_len = strlen(cand) + 1;
        if (path_len > sizeof(saved_stack)) continue;
        if (write_remote_mem(pid, scratch_addr, cand, path_len) < 0) continue;

        if (remote_loader_dlopen != 0 && remote_caller_addr != 0) {
            uintptr_t dlopen_args[3] = { scratch_addr, RTLD_NOW | RTLD_GLOBAL, remote_caller_addr };
            dl_rc = call_remote_func(pid, &saved_regs, call_sp, remote_loader_dlopen, dlopen_args, 3, &so_handle);
        } else {
            uintptr_t dlopen_args[2] = { scratch_addr, RTLD_NOW | RTLD_GLOBAL };
            dl_rc = call_remote_func(pid, &saved_regs, call_sp, remote_dlopen, dlopen_args, 2, &so_handle);
        }
        if (dl_rc == 0 && so_handle != 0) {
            break;
        }
    }

    char dlerror_msg[192] = {0};
    if ((dl_rc == 0 && so_handle == 0) && remote_dlerror != 0) {
        uintptr_t err_ptr = 0;
        if (call_remote_func(pid, &saved_regs, call_sp, remote_dlerror, NULL, 0, &err_ptr) == 0 && err_ptr != 0) {
            (void)read_remote_mem(pid, err_ptr, dlerror_msg, sizeof(dlerror_msg) - 1);
            dlerror_msg[sizeof(dlerror_msg) - 1] = '\0';
        }
    }

    (void)write_remote_mem(pid, scratch_addr, saved_stack, sizeof(saved_stack));
    if (has_syscall && saved_syscall != -1) {
        (void)ptrace(PTRACE_SETREGSET, pid, (void *)NT_ARM_SYSTEM_CALL, &sys_iov);
    }
    set_regs(pid, &saved_regs);
    if (has_sigmask) {
        (void)ptrace(PTRACE_SETSIGMASK, pid, (void *)sizeof(uint64_t), &saved_sigmask);
    }
    ptrace(PTRACE_DETACH, pid, NULL, NULL);

    if (dl_rc < 0 || so_handle == 0) {
        if (inject_via_proc_mem(pid) == 0) {
            return 0;
        }
        fprintf(stderr, "INJECT_ERR DLOPEN_NULL rc=%d reason=%s\n", dl_rc, dlerror_msg[0] ? dlerror_msg : "unknown");
        return 1;
    }

    printf("INJECT_OK handle=0x%lx\n", (unsigned long)so_handle);
    return 0;
}
