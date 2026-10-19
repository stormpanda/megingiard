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

#define CAVE_MAGIC_V1   0x4D47524443415645ULL /* "MGRDCAVE" */
#define CAVE_MAGIC_V2   0x4D47524443415632ULL /* "MGRDCAV2" */
#define CAVE_MAGIC_V3   0x4D47524443415633ULL /* "MGRDCAV3" */
#define CAVE_MAGIC      0x4D47524443415634ULL /* "MGRDCAV4" */
#define CAVE_MAX_NODES  128
#define CAVE_CMD_STATES 16

typedef struct {
    uint64_t handle;     /* +0x00 */
    uint32_t draws;      /* +0x08 */
    uint8_t  category;   /* +0x0C: 1=RENDER_PASS, 2=PIPELINE, 3=SUB_DRAW */
    uint8_t  draw_order; /* +0x0D: per-frame sequence order (1..255) */
    uint16_t enabled;    /* +0x0E: bit0=allow, bits1..3=mask3, bit4=split, bit8=tag, bits9..15=idle */
} CaveNode;              /* 16 bytes */

typedef struct {
    uint64_t cmd_buf;     /* +0x00 */
    uint64_t active_rp;   /* +0x08 */
    uint64_t active_pipe; /* +0x10 */
} CaveCmdState;           /* 24 bytes */

typedef struct {
    uint64_t magic;                             /* +0x000 */
    uint32_t api;                               /* +0x008: +0x08=api(2), +0x09=last_node_tag, +0x0A=frame_seq, +0x0B=cmd_ring_idx */
    uint32_t node_count;                        /* +0x00C */
    uint64_t frame_counter;                     /* +0x010 */
    uint64_t last_active_rp;                    /* +0x018 */
    uint64_t last_active_pipe;                  /* +0x020 */
    uint64_t real_CmdBeginRenderPass;           /* +0x028 */
    uint64_t real_CmdBeginRenderPass2;          /* +0x030 */
    uint64_t real_CmdEndRenderPass;             /* +0x038 */
    uint64_t real_CmdEndRenderPass2;            /* +0x040 */
    uint64_t real_CmdBeginRendering;            /* +0x048 */
    uint64_t real_CmdEndRendering;              /* +0x050 */
    uint64_t real_CmdBindPipeline;              /* +0x058 */
    uint64_t real_CmdDraw;                      /* +0x060 */
    uint64_t real_CmdDrawIndexed;               /* +0x068 */
    uint64_t real_CmdDrawIndirect;              /* +0x070 */
    uint64_t real_CmdDrawIndexedIndirect;       /* +0x078 */
    uint64_t real_CmdDispatch;                  /* +0x080 */
    uint64_t real_QueueSubmit;                  /* +0x088 */
    uint64_t real_QueueSubmit2;                 /* +0x090 */
    uint64_t real_QueuePresentKHR;              /* +0x098 */
    uint64_t real_CmdDrawIndirectCount;         /* +0x0A0 */
    uint64_t real_CmdDrawIndexedIndirectCount;  /* +0x0A8 */
    CaveCmdState cmd_states[CAVE_CMD_STATES];   /* +0x0B0 (16 * 24 = 0x180 bytes) */
    CaveNode nodes[CAVE_MAX_NODES];             /* +0x230 (128 * 16 = 0x800 bytes) */
} MemCaveState;                                 /* Total = 0xA30 (2608 bytes) */

_Static_assert(sizeof(CaveNode) == 16, "CaveNode size");
_Static_assert(offsetof(MemCaveState, node_count) == 0x0C, "node_count offset");
_Static_assert(offsetof(MemCaveState, frame_counter) == 0x10, "frame_counter offset");
_Static_assert(offsetof(MemCaveState, last_active_rp) == 0x18, "last_active_rp offset");
_Static_assert(offsetof(MemCaveState, last_active_pipe) == 0x20, "last_active_pipe offset");
_Static_assert(offsetof(MemCaveState, real_CmdBeginRenderPass) == 0x28, "real_CmdBeginRenderPass offset");
_Static_assert(offsetof(MemCaveState, real_QueuePresentKHR) == 0x98, "real_QueuePresentKHR offset");
_Static_assert(offsetof(MemCaveState, real_CmdDrawIndirectCount) == 0xA0, "real_CmdDrawIndirectCount offset");
_Static_assert(offsetof(MemCaveState, real_CmdDrawIndexedIndirectCount) == 0xA8, "real_CmdDrawIndexedIndirectCount offset");
_Static_assert(offsetof(MemCaveState, cmd_states) == 0xB0, "cmd_states offset");
_Static_assert(offsetof(MemCaveState, nodes) == 0x230, "nodes offset");

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
extern const uint8_t cave_hook_CmdDrawIndirectCount[];
extern const uint8_t cave_hook_CmdDrawIndexedIndirectCount[];
extern const uint8_t cave_code_end[];

__asm__(
    ".text\n"
    ".balign 8\n"
    ".global cave_code_start\n"
    "cave_code_start:\n"
    "    .quad 0\n" /* Patched with remote MemCaveState address before injection */

    /* cave_get_cmd_state: x9 = state_ptr, x0 = cmd_buf -> x14 = &cmd_states[i] */
    "cave_get_cmd_state:\n"
    "    add x14, x9, #0xB0\n"
    "    mov w15, #16\n"
    "1:\n"
    "    ldr x13, [x14, #0]\n"
    "    cmp x13, x0\n"
    "    b.eq 4f\n"
    "    add x14, x14, #24\n"
    "    subs w15, w15, #1\n"
    "    b.ne 1b\n"
    "    add x14, x9, #0xB0\n"
    "    mov w15, #16\n"
    "2:\n"
    "    ldr x13, [x14, #0]\n"
    "    cbz x13, 3f\n"
    "    add x14, x14, #24\n"
    "    subs w15, w15, #1\n"
    "    b.ne 2b\n"
    "    ldrb w13, [x9, #0x0B]\n"
    "    add w15, w13, #1\n"
    "    and w15, w15, #15\n"
    "    strb w15, [x9, #0x0B]\n"
    "    and x13, x13, #15\n"
    "    mov x15, #24\n"
    "    mul x13, x13, x15\n"
    "    add x14, x9, #0xB0\n"
    "    add x14, x14, x13\n"
    "3:\n"
    "    str x0, [x14, #0]\n"
    "    str xzr, [x14, #8]\n"
    "    str xzr, [x14, #16]\n"
    "4:\n"
    "    ret\n"

    /* cave_find_or_add_node: x9 = state_ptr, x10 = handle, w11 = cat, w12 = inc_draw -> w12 = raw_enabled */
    "cave_find_or_add_node:\n"
    "    cbz x10, 5f\n"
    "    add x14, x9, #0x230\n"
    "    mov w15, #128\n"
    "1:\n"
    "    ldr x13, [x14, #0]\n"
    "    cmp x13, x10\n"
    "    b.eq 3f\n"
    "    add x14, x14, #16\n"
    "    subs w15, w15, #1\n"
    "    b.ne 1b\n"
    "    add x14, x9, #0x230\n"
    "    mov w15, #128\n"
    "6:\n"
    "    ldr x13, [x14, #0]\n"
    "    cbz x13, 2f\n"
    "    add x14, x14, #16\n"
    "    subs w15, w15, #1\n"
    "    b.ne 6b\n"
    "    add x14, x9, #0x230\n"
    "    mov w15, #128\n"
    "7:\n"
    "    ldr w13, [x14, #8]\n"
    "    cbnz w13, 8f\n"
    "    ldrh w13, [x14, #14]\n"
    "    tbnz w13, #0, 2f\n"
    "8:\n"
    "    add x14, x14, #16\n"
    "    subs w15, w15, #1\n"
    "    b.ne 7b\n"
    "    mov w12, #1\n"
    "    ret\n"
    "2:\n"
    "    str x10, [x14, #0]\n"
    "    str wzr, [x14, #8]\n"
    "    strb w11, [x14, #12]\n"
    "    strb wzr, [x14, #13]\n"
    "    mov w13, #0x010F\n"
    "    strh w13, [x14, #14]\n"
    "    ldr w13, [x9, #0x0C]\n"
    "    cmp w13, #128\n"
    "    b.hs 3f\n"
    "    add w13, w13, #1\n"
    "    str w13, [x9, #0x0C]\n"
    "3:\n"
    "    cbz w12, 4f\n"
    "    ldr w13, [x14, #8]\n"
    "    add w13, w13, #1\n"
    "    str w13, [x14, #8]\n"
    "    ldrb w13, [x9, #0x09]\n"
    "    ubfx x15, x14, #4, #7\n"
    "    orr w15, w15, #0x80\n"
    "    cmp w13, w15\n"
    "    b.eq 4f\n"
    "    strb w15, [x9, #0x09]\n"
    "    ldrb w13, [x9, #0x0A]\n"
    "    cmp w13, #254\n"
    "    b.hs 10f\n"
    "    add w13, w13, #1\n"
    "    strb w13, [x9, #0x0A]\n"
    "10:\n"
    "    strb w13, [x14, #13]\n"
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
    "    ldp x29, x30, [sp], #16\n"
    "    ret\n"

    /* cave_record_end_rp: x0 = cmdBuf -> x9 = state_ptr */
    "cave_record_end_rp:\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    ldr x9, cave_code_start\n"
    "    bl cave_get_cmd_state\n"
    "    str xzr, [x14, #8]\n"
    "    str xzr, [x9, #0x18]\n"
    "    ldp x29, x30, [sp], #16\n"
    "    ret\n"

    /* cave_check_draw_allowed: x0 = cmdBuf, w1 = vertOrIdxCount -> x9 = state_ptr, w12 = allowed (1/0) */
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
    "    and w13, w12, #1\n"
    "    and w16, w16, w13\n"
    "2:\n"
    "    ldr x10, [x17, #16]\n"
    "    cbnz x10, 3f\n"
    "    ldr x10, [x9, #0x20]\n"
    "3:\n"
    "    cbz x10, 4f\n"
    "    mov w11, #2\n"
    "    mov w12, #1\n"
    "    bl cave_find_or_add_node\n"
    "    and w13, w12, #1\n"
    "    and w16, w16, w13\n"
    "    tbz w12, #4, 4f\n"
    "    and x10, x10, #0x0000ffffffffffff\n"
    "    and x13, x1, #0x7fff\n"
    "    orr x13, x13, #0x8000\n"
    "    orr x10, x10, x13, lsl #48\n"
    "    mov w11, #3\n"
    "    mov w12, #1\n"
    "    bl cave_find_or_add_node\n"
    "    and w13, w12, #1\n"
    "    and w16, w16, w13\n"
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
    "    ldur x10, [x1, #28]\n"
    "    ldr w11, [x1, #16]\n"
    "    eor x10, x10, x11, lsl #32\n"
    "    ldr w11, [x1, #44]\n"
    "    eor x10, x10, x11, lsl #48\n"
    "    cbz w11, 2f\n"
    "    ldr x13, [x1, #48]\n"
    "    cbz x13, 2f\n"
    "    ldr x14, [x13, #24]\n"
    "    eor x10, x10, x14, lsl #16\n"
    "    ldr x14, [x13, #44]\n"
    "    eor x10, x10, x14, lsl #40\n"
    "2:\n"
    "    ldr x12, [x1, #56]\n"
    "    cbz x12, 3f\n"
    "    ldr x14, [x12, #44]\n"
    "    add x14, x14, #1\n"
    "    eor x10, x10, x14, lsl #36\n"
    "3:\n"
    "    movz x12, #0xD900, lsl #48\n"
    "    orr x10, x10, x12\n"
    "1:\n"
    "    bl cave_get_cmd_state\n"
    "    str x10, [x14, #8]\n"
    "    str x10, [x9, #0x18]\n"
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
    "    cbnz w1, 1f\n"
    "    bl cave_get_cmd_state\n"
    "    str x2, [x14, #16]\n"
    "    str x2, [x9, #0x20]\n"
    "1:\n"
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
    "    ldr x9, cave_code_start\n"
    "    ldr x16, [x9, #0x80]\n"
    "    br x16\n"

    ".global cave_hook_QueueSubmit\n"
    "cave_hook_QueueSubmit:\n"
    "    hint #34\n"
    "    ldr x9, cave_code_start\n"
    "    strb wzr, [x9, #0x09]\n"
    "    strb wzr, [x9, #0x0A]\n"
    "    ldr x10, [x9, #0x10]\n"
    "    add x10, x10, #1\n"
    "    str x10, [x9, #0x10]\n"
    "    ldr x16, [x9, #0x88]\n"
    "    br x16\n"

    ".global cave_hook_QueueSubmit2\n"
    "cave_hook_QueueSubmit2:\n"
    "    hint #34\n"
    "    ldr x9, cave_code_start\n"
    "    strb wzr, [x9, #0x09]\n"
    "    strb wzr, [x9, #0x0A]\n"
    "    ldr x10, [x9, #0x10]\n"
    "    add x10, x10, #1\n"
    "    str x10, [x9, #0x10]\n"
    "    ldr x16, [x9, #0x90]\n"
    "    br x16\n"

    ".global cave_hook_QueuePresentKHR\n"
    "cave_hook_QueuePresentKHR:\n"
    "    hint #34\n"
    "    ldr x9, cave_code_start\n"
    "    strb wzr, [x9, #0x09]\n"
    "    strb wzr, [x9, #0x0A]\n"
    "    ldr x10, [x9, #0x10]\n"
    "    add x10, x10, #1\n"
    "    str x10, [x9, #0x10]\n"
    "    ldr x16, [x9, #0x98]\n"
    "    br x16\n"

    ".global cave_hook_CmdDrawIndirectCount\n"
    "cave_hook_CmdDrawIndirectCount:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_check_draw_allowed\n"
    "    ldp x29, x30, [sp], #16\n"
    "    cbz w12, 1f\n"
    "    ldr x16, [x9, #0xA0]\n"
    "    br x16\n"
    "1:  ret\n"

    ".global cave_hook_CmdDrawIndexedIndirectCount\n"
    "cave_hook_CmdDrawIndexedIndirectCount:\n"
    "    hint #34\n"
    "    stp x29, x30, [sp, #-16]!\n"
    "    bl cave_check_draw_allowed\n"
    "    ldp x29, x30, [sp], #16\n"
    "    cbz w12, 1f\n"
    "    ldr x16, [x9, #0xA8]\n"
    "    br x16\n"
    "1:  ret\n"

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
    static int cached_fd = -1;
    static uintptr_t cached_base_vpn = ~(uintptr_t)0;
    static uint64_t cached_entries[512];
    static size_t cached_count = 0;

    uintptr_t vpn = vaddr / 4096ULL;
    if (pagemap_fd != cached_fd || vpn < cached_base_vpn || vpn >= cached_base_vpn + cached_count) {
        uintptr_t base_vpn = vpn & ~511ULL;
        off64_t off = (off64_t)(base_vpn * sizeof(uint64_t));
        ssize_t n = pread64(pagemap_fd, cached_entries, sizeof(cached_entries), off);
        if (n < (ssize_t)sizeof(uint64_t)) {
            cached_fd = -1;
            cached_count = 0;
            return 0;
        }
        cached_fd = pagemap_fd;
        cached_base_vpn = base_vpn;
        cached_count = (size_t)n / sizeof(uint64_t);
        if (vpn < cached_base_vpn || vpn >= cached_base_vpn + cached_count) {
            return 0;
        }
    }
    return ((cached_entries[vpn - cached_base_vpn] >> 63) & 1ULL) != 0;
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

static int inject_via_proc_mem_internal(pid_t pid, int quiet) {
    char maps_path[64], mem_path[64], pagemap_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", (int)pid);
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", (int)pid);
    snprintf(pagemap_path, sizeof(pagemap_path), "/proc/%d/pagemap", (int)pid);

    int mem_fd = open(mem_path, O_RDWR);
    if (mem_fd < 0) {
        if (!quiet) printf("INJECT_ERR PROC_MEM_OPEN errno=%d\n", errno);
        return 1;
    }
    int pagemap_fd = open(pagemap_path, O_RDONLY);

    FILE *fp = fopen(maps_path, "r");
    if (!fp) {
        if (pagemap_fd >= 0) close(pagemap_fd);
        close(mem_fd);
        if (!quiet) printf("INJECT_ERR MAPS_OPEN\n");
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
            /* Scan anonymous/heap mappings (path[0] != '/') for live dispatch tables */
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
        if (!quiet) printf("INJECT_ERR NO_VULKAN_LOADER\n");
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
    uintptr_t prev_state_addr = 0;
    uintptr_t prev_code_addr = 0;
    int has_prev_cave = 0;
    FILE *sf = fopen(state_file, "r");
    if (sf) {
        unsigned long existing_state = 0, existing_code = 0;
        if (fscanf(sf, "%lx %lx", &existing_state, &existing_code) == 2 && existing_state != 0) {
            const size_t hdr_sz = offsetof(MemCaveState, cmd_states);
            ssize_t hdr_rd = pread64(mem_fd, &prev_cave, hdr_sz, (off64_t)existing_state);
            if (hdr_rd == (ssize_t)hdr_sz && prev_cave.magic == CAVE_MAGIC) {
                if (pread64(mem_fd, &prev_cave, sizeof(prev_cave), (off64_t)existing_state) == (ssize_t)sizeof(prev_cave) &&
                    is_in_ranges(prev_cave.real_CmdDraw, target_code_ranges, target_code_count)) {
                    fclose(sf);
                    free(all_exec_ranges);
                    free(rw_ranges);
                    if (pagemap_fd >= 0) close(pagemap_fd);
                    close(mem_fd);
                    if (!quiet) printf("INJECT_OK handle=0x%lx\n", existing_state);
                    return 0;
                }
            } else if (hdr_rd == (ssize_t)hdr_sz &&
                       (prev_cave.magic == CAVE_MAGIC_V1 ||
                        prev_cave.magic == CAVE_MAGIC_V2 ||
                        prev_cave.magic == CAVE_MAGIC_V3)) {
                if (prev_cave.magic == CAVE_MAGIC_V1) {
                    prev_cave.real_CmdDrawIndirectCount = 0;
                    prev_cave.real_CmdDrawIndexedIndirectCount = 0;
                }
                prev_state_addr = (uintptr_t)existing_state;
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
        if (!quiet) {
            printf("INJECT_ERR NO_CAVE_SPACE state=0x%lx code=0x%lx\n",
                    (unsigned long)state_cave_addr, (unsigned long)code_cave_addr);
        }
        return 1;
    }

    /* Read the entire 4096-byte executable page containing any previous code cave BEFORE overwriting */
    uintptr_t old_code_page = (prev_code_addr != 0 ? prev_code_addr : code_cave_addr) & ~0xFFFULL;
    uint32_t old_page_insns[1024];
    memset(old_page_insns, 0, sizeof(old_page_insns));
    int has_old_page = (old_code_page != 0 &&
                        pread64(mem_fd, old_page_insns, sizeof(old_page_insns), (off64_t)old_code_page) ==
                            (ssize_t)sizeof(old_page_insns));

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
    int off_draw_ind_cnt = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDrawIndirectCount"));
    int off_draw_ind_cnt_khr = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDrawIndirectCountKHR"));
    int off_draw_idx_ind_cnt = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDrawIndexedIndirectCount"));
    int off_draw_idx_ind_cnt_khr = decode_remote_vk_dispatch_offset(mem_fd, remote_lookup_dyn_symbol(mem_fd, vk_loader_base, "vkCmdDrawIndexedIndirectCountKHR"));
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
        if (!quiet) {
            printf("INJECT_ERR VK_OFFSET_DECODE draw=%d idx=%d bind=%d\n",
                    off_draw, off_draw_idx, off_bind_pipe);
        }
        return 1;
    }

    MemCaveState cave_state;
    memset(&cave_state, 0, sizeof(cave_state));
    cave_state.magic = CAVE_MAGIC;
    cave_state.api = 2; /* VULKAN */

    int found_table = 0;

    if (has_prev_cave && is_in_ranges(prev_cave.real_CmdDraw, target_code_ranges, target_code_count)) {
        cave_state.real_CmdBeginRenderPass = prev_cave.real_CmdBeginRenderPass;
        cave_state.real_CmdBeginRenderPass2 = prev_cave.real_CmdBeginRenderPass2;
        cave_state.real_CmdEndRenderPass = prev_cave.real_CmdEndRenderPass;
        cave_state.real_CmdEndRenderPass2 = prev_cave.real_CmdEndRenderPass2;
        cave_state.real_CmdBeginRendering = prev_cave.real_CmdBeginRendering;
        cave_state.real_CmdEndRendering = prev_cave.real_CmdEndRendering;
        cave_state.real_CmdBindPipeline = prev_cave.real_CmdBindPipeline;
        cave_state.real_CmdDraw = prev_cave.real_CmdDraw;
        cave_state.real_CmdDrawIndexed = prev_cave.real_CmdDrawIndexed;
        cave_state.real_CmdDrawIndirect = prev_cave.real_CmdDrawIndirect;
        cave_state.real_CmdDrawIndexedIndirect = prev_cave.real_CmdDrawIndexedIndirect;
        cave_state.real_CmdDispatch = prev_cave.real_CmdDispatch;
        cave_state.real_QueueSubmit = prev_cave.real_QueueSubmit;
        cave_state.real_QueueSubmit2 = prev_cave.real_QueueSubmit2;
        cave_state.real_QueuePresentKHR = prev_cave.real_QueuePresentKHR;
        cave_state.real_CmdDrawIndirectCount = prev_cave.real_CmdDrawIndirectCount;
        cave_state.real_CmdDrawIndexedIndirectCount = prev_cave.real_CmdDrawIndexedIndirectCount;
        found_table = 1;
    }

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
                    cave_state.real_CmdDrawIndirectCount = READ_VERIFIED_SLOT(off_draw_ind_cnt);
                    if (cave_state.real_CmdDrawIndirectCount == 0) {
                        cave_state.real_CmdDrawIndirectCount = READ_VERIFIED_SLOT(off_draw_ind_cnt_khr);
                    }
                    cave_state.real_CmdDrawIndexedIndirectCount = READ_VERIFIED_SLOT(off_draw_idx_ind_cnt);
                    if (cave_state.real_CmdDrawIndexedIndirectCount == 0) {
                        cave_state.real_CmdDrawIndexedIndirectCount = READ_VERIFIED_SLOT(off_draw_idx_ind_cnt_khr);
                    }
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
        if (!quiet) printf("INJECT_ERR NO_LIVE_VK_DEVICE rw_count=%d icd_count=%d\n", rw_count, icd_exec_count);
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
        if (!quiet) printf("INJECT_ERR CAVE_WRITE_FAILED errno=%d\n", errno);
        return 1;
    }
    free(code_buf);

    #define CAVE_HOOK_ADDR(sym) (code_cave_addr + (uintptr_t)((sym) - cave_code_start))
    struct {
        uint64_t real_fn;
        uint64_t hook_fn;
    } hook_pairs[17] = {
        { cave_state.real_CmdBeginRenderPass,          CAVE_HOOK_ADDR(cave_hook_CmdBeginRenderPass) },
        { cave_state.real_CmdBeginRenderPass2,         CAVE_HOOK_ADDR(cave_hook_CmdBeginRenderPass2) },
        { cave_state.real_CmdEndRenderPass,            CAVE_HOOK_ADDR(cave_hook_CmdEndRenderPass) },
        { cave_state.real_CmdEndRenderPass2,           CAVE_HOOK_ADDR(cave_hook_CmdEndRenderPass2) },
        { cave_state.real_CmdBeginRendering,           CAVE_HOOK_ADDR(cave_hook_CmdBeginRendering) },
        { cave_state.real_CmdEndRendering,             CAVE_HOOK_ADDR(cave_hook_CmdEndRendering) },
        { cave_state.real_CmdBindPipeline,             CAVE_HOOK_ADDR(cave_hook_CmdBindPipeline) },
        { cave_state.real_CmdDraw,                     CAVE_HOOK_ADDR(cave_hook_CmdDraw) },
        { cave_state.real_CmdDrawIndexed,              CAVE_HOOK_ADDR(cave_hook_CmdDrawIndexed) },
        { cave_state.real_CmdDrawIndirect,             CAVE_HOOK_ADDR(cave_hook_CmdDrawIndirect) },
        { cave_state.real_CmdDrawIndexedIndirect,      CAVE_HOOK_ADDR(cave_hook_CmdDrawIndexedIndirect) },
        { cave_state.real_CmdDispatch,                 CAVE_HOOK_ADDR(cave_hook_CmdDispatch) },
        { cave_state.real_QueueSubmit,                 CAVE_HOOK_ADDR(cave_hook_QueueSubmit) },
        { cave_state.real_QueueSubmit2,                CAVE_HOOK_ADDR(cave_hook_QueueSubmit2) },
        { cave_state.real_QueuePresentKHR,             CAVE_HOOK_ADDR(cave_hook_QueuePresentKHR) },
        { cave_state.real_CmdDrawIndirectCount,        CAVE_HOOK_ADDR(cave_hook_CmdDrawIndirectCount) },
        { cave_state.real_CmdDrawIndexedIndirectCount, CAVE_HOOK_ADDR(cave_hook_CmdDrawIndexedIndirectCount) },
    };
    #undef CAVE_HOOK_ADDR

    int patched_slots = 0;
    for (int r = rw_count - 1; r >= 0; r--) {
        for (uintptr_t page = rw_ranges[r].start; page < rw_ranges[r].end; page += 4096) {
            if (!is_remote_page_resident(pagemap_fd, page)) continue;
            if (pread64(mem_fd, page_buf, 4096, (off64_t)page) != 4096) continue;

            if (has_old_page) {
                for (size_t off = 0; off < 4096; off += 8) {
                    uintptr_t slot_addr = page + off;
                    if (slot_addr >= state_cave_addr && slot_addr < state_cave_addr + sizeof(MemCaveState)) {
                        continue;
                    }
                    uint64_t val = *(uint64_t *)(page_buf + off);
                    if (val >= old_code_page && val < old_code_page + 4096 && ((val & 3u) == 0)) {
                        size_t idx = (size_t)(val - old_code_page) / 4u;
                        if (idx < 1024 && old_page_insns[idx] == 0xD503245Fu) {
                            for (size_t step = 0; step < 32 && (idx + step) < 1024; step++) {
                                uint32_t insn = old_page_insns[idx + step];
                                if ((insn & 0xFFC003FFu) == 0xF9400130u) {
                                    size_t field_off = (size_t)((insn >> 10) & 0xFFFu) * 8u;
                                    if (field_off >= 0x28u && field_off <= 0xA8u) {
                                        uint64_t orig_fn = *(uint64_t *)((uint8_t *)&cave_state + field_off);
                                        if (orig_fn != 0) {
                                            *(uint64_t *)(page_buf + off) = orig_fn;
                                            (void)pwrite64(mem_fd, &orig_fn, sizeof(uint64_t), (off64_t)slot_addr);
                                        }
                                    }
                                    break;
                                }
                                if (insn == 0xD61F0200u || insn == 0xD65F03C0u) break;
                            }
                        }
                    }
                }
            }

            for (size_t off = 0; off < 4096; off += 8) {
                uintptr_t slot_addr = page + off;
                if (slot_addr >= state_cave_addr && slot_addr < state_cave_addr + sizeof(MemCaveState)) {
                    continue;
                }
                uint64_t val = *(uint64_t *)(page_buf + off);
                int match_k = -1;
                for (int k = 0; k < 17; k++) {
                    if (hook_pairs[k].real_fn != 0 && val == hook_pairs[k].real_fn) {
                        match_k = k;
                        break;
                    }
                }
                if (match_k < 0) continue;

                /* Verify at least 1 sibling Vulkan pointer exists in the same page */
                int siblings = 0;
                for (size_t s = 0; s < 4096; s += 8) {
                    if (s == off) continue;
                    uint64_t sval = *(uint64_t *)(page_buf + s);
                    for (int k = 0; k < 17; k++) {
                        if (hook_pairs[k].real_fn != 0 &&
                            (sval == hook_pairs[k].real_fn || sval == hook_pairs[k].hook_fn)) {
                            siblings++;
                            break;
                        }
                    }
                    if (siblings >= 1) break;
                }
                if (siblings >= 1) {
                    uint64_t new_val = hook_pairs[match_k].hook_fn;
                    if (pwrite64(mem_fd, &new_val, sizeof(new_val), (off64_t)slot_addr) == (ssize_t)sizeof(new_val)) {
                        *(uint64_t *)(page_buf + off) = new_val;
                        patched_slots++;
                    }
                }
            }
        }
    }

    if ((prev_cave.magic == CAVE_MAGIC_V1 ||
         prev_cave.magic == CAVE_MAGIC_V2 ||
         prev_cave.magic == CAVE_MAGIC_V3) &&
        prev_state_addr != 0 && prev_state_addr != state_cave_addr) {
        uint64_t zero_magic = 0;
        (void)pwrite64(mem_fd, &zero_magic, sizeof(zero_magic), (off64_t)prev_state_addr);
    }

    free(all_exec_ranges);
    free(rw_ranges);
    if (pagemap_fd >= 0) close(pagemap_fd);
    close(mem_fd);

    if (patched_slots == 0) {
        if (!quiet) fprintf(stderr, "INJECT_ERR NO_SLOTS_PATCHED\n");
        return 1;
    }

    FILE *out_sf = fopen(state_file, "w");
    if (out_sf) {
        fprintf(out_sf, "%lx %lx\n", (unsigned long)state_cave_addr, (unsigned long)code_cave_addr);
        fclose(out_sf);
        (void)chmod(state_file, 0666);
    }

    if (!quiet) printf("INJECT_OK handle=0x%lx patched=%d\n", (unsigned long)state_cave_addr, patched_slots);
    return 0;
}

static int inject_via_proc_mem(pid_t pid) {
    return inject_via_proc_mem_internal(pid, 0);
}

static uint16_t unpack_cave_node_mask(uint16_t raw) {
    if ((raw & 0x0100u) != 0u) {
        return (uint16_t)((raw >> 1) & 0x7u);
    }
    return raw ? 0x7u : 0x0u;
}

static uint16_t pack_cave_node_state_with_flags(uint16_t mask3, uint16_t idle_polls, uint16_t prev_raw) {
    uint16_t m = (uint16_t)(mask3 & 0x7u);
    uint16_t idle = (uint16_t)(idle_polls & 0x7Fu);
    uint16_t split_flag = (uint16_t)(prev_raw & 0x0010u);
    uint16_t allow_draw = (m == 0x7u) ? 1u : 0u;
    return (uint16_t)(0x0100u | (idle << 9) | split_flag | (m << 1) | allow_draw);
}

static uint16_t pack_cave_node_mask_preserving(uint16_t mask3, uint16_t prev_raw) {
    return pack_cave_node_state_with_flags(mask3, 0u, prev_raw);
}

typedef struct {
    uint64_t handles[CAVE_MAX_NODES];
    uint32_t prev_draws[CAVE_MAX_NODES];
    uint32_t last_delta[CAVE_MAX_NODES];
    uint16_t idle_streak[CAVE_MAX_NODES];
} CavePollTracker;

static void get_cave_poll_tracker_path(pid_t pid, char *out, size_t out_sz) {
    snprintf(out, out_sz, "/data/local/tmp/megingiard_gfx_cave_%d.prev", (int)pid);
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
    memset(&state, 0, sizeof(state));
    ssize_t rd = pread64(mem_fd, &state, sizeof(state), (off64_t)state_addr);
    if (rd >= 0xA0 &&
        (state.magic == CAVE_MAGIC_V1 ||
         state.magic == CAVE_MAGIC_V2 ||
         state.magic == CAVE_MAGIC_V3)) {
        close(mem_fd);
        if (inject_via_proc_mem_internal(pid, 1) != 0) {
            return -1;
        }
        usleep(120000);
        sf = fopen(state_file, "r");
        if (!sf) return -1;
        scanned = fscanf(sf, "%lx %lx", &state_addr, &code_addr);
        fclose(sf);
        if (scanned < 1 || state_addr == 0) return -1;
        mem_fd = open(mem_path, O_RDWR);
        if (mem_fd < 0) return -1;
        rd = pread64(mem_fd, &state, sizeof(state), (off64_t)state_addr);
    }
    if (rd != (ssize_t)sizeof(state) || state.magic != CAVE_MAGIC) {
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
        char tracker_path[128];
        get_cave_poll_tracker_path(pid, tracker_path, sizeof(tracker_path));
        CavePollTracker tracker;
        memset(&tracker, 0, sizeof(tracker));
        FILE *tf = fopen(tracker_path, "rb");
        if (tf) {
            (void)fread(&tracker, 1, sizeof(tracker), tf);
            fclose(tf);
        }

        uint32_t deltas[CAVE_MAX_NODES];
        uint64_t total_delta = 0;
        for (uint32_t i = 0; i < CAVE_MAX_NODES; i++) {
            const CaveNode *n = &state.nodes[i];
            if (n->handle == 0) {
                deltas[i] = 0;
                tracker.handles[i] = 0;
                tracker.prev_draws[i] = 0;
                tracker.last_delta[i] = 0;
                tracker.idle_streak[i] = 0;
                continue;
            }
            if (tracker.handles[i] != n->handle) {
                tracker.handles[i] = n->handle;
                tracker.prev_draws[i] = 0;
                tracker.last_delta[i] = n->draws > 0 ? n->draws : 1u;
                tracker.idle_streak[i] = 0;
            }
            uint32_t d = (n->draws >= tracker.prev_draws[i]) ? (n->draws - tracker.prev_draws[i]) : n->draws;
            deltas[i] = d;
            total_delta += d;
        }

        printf("API|VULKAN\n");
        for (uint32_t i = 0; i < CAVE_MAX_NODES; i++) {
            const CaveNode *n = &state.nodes[i];
            if (n->handle == 0) continue;
            off64_t node_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) + (size_t)i * sizeof(CaveNode));
            unsigned long long h = (unsigned long long)n->handle;
            unsigned long long h32 = h & 0xFFFFFFFFULL;
            uint16_t mask3 = unpack_cave_node_mask(n->enabled);
            unsigned int order = (unsigned int)n->draw_order;

            if (total_delta > 0) {
                tracker.prev_draws[i] = n->draws;
                if (deltas[i] > 0) {
                    tracker.last_delta[i] = deltas[i];
                    tracker.idle_streak[i] = 0;
                } else {
                    tracker.idle_streak[i]++;
                    tracker.last_delta[i] = 0;
                    if (mask3 == 0x7u && (n->enabled & 0x0010u) == 0u && tracker.idle_streak[i] >= 5u) {
                        CaveNode empty_node;
                        memset(&empty_node, 0, sizeof(empty_node));
                        (void)pwrite64(mem_fd, &empty_node, sizeof(empty_node), node_off);
                        tracker.handles[i] = 0;
                        tracker.prev_draws[i] = 0;
                        tracker.last_delta[i] = 0;
                        tracker.idle_streak[i] = 0;
                        continue;
                    }
                }
            }

            unsigned int draws = (unsigned int)tracker.last_delta[i];
            if (n->category == 1) {
                unsigned int rp_en = (mask3 & 1u) ? 1u : 0u;
                printf("NODE|RP_0x%llx|RENDER_PASS|VULKAN|%08llX|Order:#%03u|%u|%u|Vulkan RenderPass (0x%llx)\n",
                       h, h32, order, draws, rp_en, h);
            } else if (n->category == 2) {
                unsigned int pipe_en = (mask3 & 1u) ? 1u : 0u;
                unsigned int vs_en = (mask3 & 2u) ? 1u : 0u;
                unsigned int fs_en = (mask3 & 4u) ? 1u : 0u;
                printf("NODE|PIPE_0x%llx|PIPELINE|VULKAN|%08llX|Order:#%03u|%u|%u|Vulkan Pipeline (0x%llx)\n",
                       h, h32, order, draws, pipe_en, h);
                printf("NODE|VS_SYNTH_0x%llx|VERTEX_SHADER|VULKAN|%08llX|Order:#%03u|%u|%u|Vertex Shader (Pipeline 0x%llx)\n",
                       h, h32, order, draws, vs_en, h);
                printf("NODE|FS_SYNTH_0x%llx|FRAGMENT_SHADER|VULKAN|%08llX|Order:#%03u|%u|%u|Fragment Shader (Pipeline 0x%llx)\n",
                       h, h32, order, draws, fs_en, h);
            } else if (n->category == 3) {
                unsigned int sub_en = (mask3 & 1u) ? 1u : 0u;
                unsigned long long parent_h = h & 0x0000FFFFFFFFFFFFULL;
                unsigned int idx_count = (unsigned int)((h >> 48) & 0x7FFFu);
                printf("NODE|SUB_0x%llx|PIPELINE|VULKAN|%08llX|Order:#%03u Idx:%u|%u|%u|↳ Sub-Draw (%u verts/idx · Pipe 0x%llx)\n",
                       h, h32, order, idx_count, draws, sub_en, idx_count, parent_h);
            }
        }

        FILE *out_tf = fopen(tracker_path, "wb");
        if (out_tf) {
            (void)fwrite(&tracker, 1, sizeof(tracker), out_tf);
            fclose(out_tf);
            (void)chmod(tracker_path, 0666);
        }

        close(mem_fd);
        printf("END\n");
        return 0;
    }

    if (strcmp(cmd, "SET") == 0 && argc >= 6) {
        const char *node_id = argv[4];
        uint16_t en = (uint16_t)(atoi(argv[5]) != 0 ? 1u : 0u);
        const char *hex_part = strstr(node_id, "0x");
        uint64_t target_handle = hex_part ? strtoull(hex_part, NULL, 16) : 0;
        for (uint32_t i = 0; i < CAVE_MAX_NODES; i++) {
            if (state.nodes[i].handle == target_handle && target_handle != 0) {
                uint16_t mask3 = unpack_cave_node_mask(state.nodes[i].enabled);
                if (state.nodes[i].category == 1 || state.nodes[i].category == 3) {
                    mask3 = en ? 0x7u : 0x0u;
                } else {
                    uint16_t bit = 1u;
                    if (strncmp(node_id, "VS_SYNTH_", 9) == 0) {
                        bit = 2u;
                    } else if (strncmp(node_id, "FS_SYNTH_", 9) == 0) {
                        bit = 4u;
                    }
                    if (en) {
                        mask3 |= bit;
                    } else {
                        mask3 &= (uint16_t)~bit;
                    }
                }
                uint16_t packed = pack_cave_node_mask_preserving(mask3, state.nodes[i].enabled);
                off64_t en_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                           (size_t)i * sizeof(CaveNode) + offsetof(CaveNode, enabled));
                (void)pwrite64(mem_fd, &packed, sizeof(packed), en_off);
            }
        }
        close(mem_fd);
        printf("OK\n");
        return 0;
    }

    if (strcmp(cmd, "SPLIT") == 0 && argc >= 6) {
        const char *node_id = argv[4];
        int split = atoi(argv[5]) != 0 ? 1 : 0;
        const char *hex_part = strstr(node_id, "0x");
        uint64_t target_handle = hex_part ? (strtoull(hex_part, NULL, 16) & 0x0000FFFFFFFFFFFFULL) : 0;
        for (uint32_t i = 0; i < CAVE_MAX_NODES; i++) {
            if (state.nodes[i].handle == target_handle && target_handle != 0 && state.nodes[i].category == 2) {
                uint16_t mask3 = unpack_cave_node_mask(state.nodes[i].enabled);
                uint16_t flags = split ? 0x0010u : 0x0000u;
                uint16_t packed = pack_cave_node_mask_preserving(mask3, flags);
                off64_t en_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                           (size_t)i * sizeof(CaveNode) + offsetof(CaveNode, enabled));
                (void)pwrite64(mem_fd, &packed, sizeof(packed), en_off);
            } else if (!split && state.nodes[i].category == 3 &&
                       (state.nodes[i].handle & 0x0000FFFFFFFFFFFFULL) == target_handle) {
                CaveNode empty_node;
                memset(&empty_node, 0, sizeof(empty_node));
                off64_t node_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                             (size_t)i * sizeof(CaveNode));
                (void)pwrite64(mem_fd, &empty_node, sizeof(empty_node), node_off);
            }
        }
        close(mem_fd);
        printf("OK\n");
        return 0;
    }

    if (strcmp(cmd, "SET_ALL") == 0 && argc >= 5) {
        uint16_t en = (uint16_t)(atoi(argv[4]) != 0 ? 1u : 0u);
        for (uint32_t i = 0; i < CAVE_MAX_NODES; i++) {
            if (state.nodes[i].handle != 0) {
                uint16_t packed = pack_cave_node_mask_preserving(en ? 0x7u : 0x0u, state.nodes[i].enabled);
                off64_t en_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                           (size_t)i * sizeof(CaveNode) + offsetof(CaveNode, enabled));
                (void)pwrite64(mem_fd, &packed, sizeof(packed), en_off);
            }
        }
        close(mem_fd);
        printf("OK\n");
        return 0;
    }

    if (strcmp(cmd, "INVERT") == 0) {
        for (uint32_t i = 0; i < CAVE_MAX_NODES; i++) {
            if (state.nodes[i].handle != 0) {
                uint16_t mask3 = unpack_cave_node_mask(state.nodes[i].enabled);
                if (state.nodes[i].category == 1 || state.nodes[i].category == 3) {
                    mask3 = (mask3 & 1u) ? 0x0u : 0x7u;
                } else {
                    mask3 = (uint16_t)((~mask3) & 0x7u);
                }
                uint16_t packed = pack_cave_node_mask_preserving(mask3, state.nodes[i].enabled);
                off64_t en_off = (off64_t)(state_addr + offsetof(MemCaveState, nodes) +
                                           (size_t)i * sizeof(CaveNode) + offsetof(CaveNode, enabled));
                (void)pwrite64(mem_fd, &packed, sizeof(packed), en_off);
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
    /* After call_remote_func, the thread is stopped on SIGSEGV rather than a syscall stop;
     * convert kernel internal restart codes (-ERESTART_RESTARTBLOCK = -516 .. -ERESTARTSYS = -512)
     * in saved x0 to -EINTR so libc sees a normal interrupted syscall instead of errno 516. */
    if ((int64_t)saved_regs.regs[0] >= -516 && (int64_t)saved_regs.regs[0] <= -512) {
        saved_regs.regs[0] = (uint64_t)(int64_t)-EINTR;
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
