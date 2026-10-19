/*
 * megingiard_gfx_layer.c — Universal Graphics UI & Shader Inspector (Vulkan + OpenGL ES)
 *
 * Builds into `libVkLayer_MegingiardInspector.so` (linked with `-ldl` only — zero
 * graphics DT_NEEDED dependencies so it loads into any linker namespace) and
 * operates in two modes:
 *   1. Live In-Process Attachment (via `megingiard_gfx_inject_arm64`):
 *      Uses `dl_iterate_phdr` across all linker namespaces to resolve vendor driver
 *      entrypoints (`vulkan.adreno.so`, `libvulkan_freedreno.so`, `libGLESv2_*.so`,
 *      `libEGL_*.so`), decodes `libvulkan.so` `DeviceDispatchTable` offsets, and
 *      patches ELF PLT/GOT entries (`DT_JMPREL` / `DT_RELA`), `.data`/`.bss`
 *      (`gEGLImpl.hooks` / `gEGLImpl.egl`), and resident heap dispatch tables.
 *   2. Android Native Vulkan Layer (`VK_LAYER_MEGINGIARD_INSPECTOR`):
 *      Exports `vkEnumerateInstanceLayerProperties`, `vkGetInstanceProcAddr`,
 *      and `vkGetDeviceProcAddr` so Android's Vulkan loader (`gpu_debug_layers`)
 *      chains the layer from Frame 0.
 *
 * Exposes both:
 *   - Per-process abstract Unix domain socket `\0megingiard_gfx_<pid>`
 *   - Local loopback TCP server on 127.0.0.1:51255
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <dlfcn.h>
#include <errno.h>
#include <link.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <elf.h>
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <vulkan/vulkan.h>

#ifndef DT_GNU_HASH
#define DT_GNU_HASH 0x6ffffef5
#endif

#define INSPECTOR_PORT 51255
#define MAX_NODES 256
#define MAX_CMD_BUF_SLOTS 16
#define MAX_ORIG_CANDIDATES 4

typedef enum {
    CAT_RENDER_PASS = 0,
    CAT_PIPELINE = 1,
    CAT_FRAGMENT_SHADER = 2,
    CAT_VERTEX_SHADER = 3,
    CAT_SUB_DRAW = 4
} NodeCategory;

typedef enum {
    API_NONE = 0,
    API_GLES = 1,
    API_VK = 2
} NodeApi;

typedef struct {
    int in_use;
    char id[48];
    char name[96];
    NodeCategory category;
    NodeApi api;
    uint32_t hash;
    uint64_t handle_key;
    uint64_t vs_key;
    uint64_t fs_key;
    uint64_t parent_pipe_key;
    int width;
    int height;
    int draw_calls_accum;
    int draw_calls_last_frame;
    int enabled;
    int split_enabled;
} InspectorNode;

typedef struct {
    uint64_t cmd_buf;
    uint64_t render_pass;
    uint64_t gfx_pipeline;
    uint64_t comp_pipeline;
    int rp_w;
    int rp_h;
} VkCmdBufState;

static pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;
static InspectorNode g_nodes[MAX_NODES];
static int g_node_count = 0;
static volatile int g_server_started = 0;
static volatile int g_seen_gles = 0;
static volatile int g_seen_vk = 0;

/* Current GL state on render thread */
static GLuint g_cur_gl_fbo = 0;
static GLuint g_cur_gl_program = 0;
static int g_cur_gl_vp_w = 1920;
static int g_cur_gl_vp_h = 1080;
static int g_gl_probed_existing = 0;
static int g_gl_draws_since_rollover = 0;
static int g_gl_has_swap_hook = 0;

/* Current Vulkan state (per-VkCommandBuffer + global fallback) */
static VkCmdBufState g_vk_cmd_states[MAX_CMD_BUF_SLOTS];
static uint32_t g_vk_cmd_rr = 0;
static uint64_t g_cur_vk_render_pass = 0;
static uint64_t g_cur_vk_pipeline = 0;
static uint64_t g_cur_vk_comp_pipeline = 0;
static int g_cur_vk_rp_w = 1920;
static int g_cur_vk_rp_h = 1080;
static int g_vk_draws_since_rollover = 0;
static int g_vk_has_present_hook = 0;

/* CRC32 computation for SPIR-V bytecode and GLSL shader source */
static uint32_t compute_crc32(const void *data, size_t len) {
    const uint8_t *bytes = (const uint8_t *)data;
    uint32_t crc = 0xFFFFFFFFu;
    for (size_t i = 0; i < len; i++) {
        crc ^= bytes[i];
        for (int b = 0; b < 8; b++) {
            uint32_t mask = -(crc & 1u);
            crc = (crc >> 1) ^ (0xEDB88320u & mask);
        }
    }
    return ~crc;
}

static uint32_t mix_hash64(uint64_t val) {
    return compute_crc32(&val, sizeof(val));
}

static const char *category_to_str(NodeCategory cat) {
    switch (cat) {
        case CAT_RENDER_PASS: return "RENDER_PASS";
        case CAT_PIPELINE: return "PIPELINE";
        case CAT_FRAGMENT_SHADER: return "FRAGMENT_SHADER";
        case CAT_VERTEX_SHADER: return "VERTEX_SHADER";
        default: return "PIPELINE";
    }
}

static const char *api_to_str(NodeApi api) {
    switch (api) {
        case API_GLES: return "OPENGL_ES";
        case API_VK: return "VULKAN";
        default: return "UNKNOWN";
    }
}

/* Must be called with g_mutex held */
static InspectorNode *find_or_create_node_locked(
    NodeCategory category,
    NodeApi api,
    uint64_t handle_key,
    uint32_t hash,
    const char *prefix_id,
    const char *display_name,
    int width,
    int height
) {
    int free_idx = -1;
    for (int i = 0; i < g_node_count; i++) {
        if (!g_nodes[i].in_use) {
            if (free_idx < 0) free_idx = i;
            continue;
        }
        if (g_nodes[i].category == category &&
            g_nodes[i].api == api &&
            g_nodes[i].handle_key == handle_key) {
            if (width > 0 && height > 0) {
                g_nodes[i].width = width;
                g_nodes[i].height = height;
            }
            return &g_nodes[i];
        }
    }
    InspectorNode *node = NULL;
    if (free_idx >= 0) {
        node = &g_nodes[free_idx];
    } else {
        if (g_node_count >= MAX_NODES) {
            return NULL;
        }
        node = &g_nodes[g_node_count++];
    }
    memset(node, 0, sizeof(*node));
    node->in_use = 1;
    node->category = category;
    node->api = api;
    node->handle_key = handle_key;
    node->hash = hash != 0 ? hash : mix_hash64(handle_key);
    snprintf(node->id, sizeof(node->id), "%s_%08X", prefix_id, node->hash);
    snprintf(node->name, sizeof(node->name), "%s", display_name);
    node->width = width > 0 ? width : 1920;
    node->height = height > 0 ? height : 1080;
    node->enabled = 1;
    return node;
}

/* Must be called with g_mutex held */
static int is_node_enabled_locked(NodeCategory cat, NodeApi api, uint64_t handle_key) {
    if (handle_key == 0 && cat != CAT_RENDER_PASS) return 1;
    for (int i = 0; i < g_node_count; i++) {
        if (g_nodes[i].in_use &&
            g_nodes[i].category == cat &&
            g_nodes[i].api == api &&
            g_nodes[i].handle_key == handle_key) {
            return g_nodes[i].enabled;
        }
    }
    return 1;
}

/* Must be called with g_mutex held */
static void end_of_frame_rollover_locked(NodeApi api) {
    for (int i = 0; i < g_node_count; i++) {
        if (g_nodes[i].in_use && g_nodes[i].api == api) {
            if (g_nodes[i].draw_calls_accum > 0 || g_nodes[i].draw_calls_last_frame > 0) {
                g_nodes[i].draw_calls_last_frame = g_nodes[i].draw_calls_accum;
                g_nodes[i].draw_calls_accum = 0;
            }
        }
    }
}

static void end_of_frame_rollover(NodeApi api) {
    pthread_mutex_lock(&g_mutex);
    end_of_frame_rollover_locked(api);
    pthread_mutex_unlock(&g_mutex);
}

/* Must be called with g_mutex held */
static VkCmdBufState *get_or_alloc_cmd_state_locked(VkCommandBuffer commandBuffer) {
    uint64_t key = (uint64_t)(uintptr_t)commandBuffer;
    if (key == 0) return NULL;
    for (int i = 0; i < MAX_CMD_BUF_SLOTS; i++) {
        if (g_vk_cmd_states[i].cmd_buf == key) {
            return &g_vk_cmd_states[i];
        }
    }
    for (int i = 0; i < MAX_CMD_BUF_SLOTS; i++) {
        if (g_vk_cmd_states[i].cmd_buf == 0) {
            memset(&g_vk_cmd_states[i], 0, sizeof(VkCmdBufState));
            g_vk_cmd_states[i].cmd_buf = key;
            g_vk_cmd_states[i].rp_w = g_cur_vk_rp_w;
            g_vk_cmd_states[i].rp_h = g_cur_vk_rp_h;
            return &g_vk_cmd_states[i];
        }
    }
    uint32_t idx = (g_vk_cmd_rr++) & (MAX_CMD_BUF_SLOTS - 1U);
    memset(&g_vk_cmd_states[idx], 0, sizeof(VkCmdBufState));
    g_vk_cmd_states[idx].cmd_buf = key;
    g_vk_cmd_states[idx].rp_w = g_cur_vk_rp_w;
    g_vk_cmd_states[idx].rp_h = g_cur_vk_rp_h;
    return &g_vk_cmd_states[idx];
}

/* =========================================================================
 * OpenGL ES 2/3 Function Pointers & Interceptors
 * ========================================================================= */

typedef void (*PFN_glUseProgram)(GLuint program);
typedef void (*PFN_glBindFramebuffer)(GLenum target, GLuint framebuffer);
typedef void (*PFN_glViewport)(GLint x, GLint y, GLsizei width, GLsizei height);
typedef GLuint (*PFN_glCreateShader)(GLenum type);
typedef void (*PFN_glShaderSource)(GLuint shader, GLsizei count, const GLchar *const *string, const GLint *length);
typedef void (*PFN_glAttachShader)(GLuint program, GLuint shader);
typedef void (*PFN_glDrawArrays)(GLenum mode, GLint first, GLsizei count);
typedef void (*PFN_glDrawElements)(GLenum mode, GLsizei count, GLenum type, const void *indices);
typedef void (*PFN_glDrawArraysInstanced)(GLenum mode, GLint first, GLsizei count, GLsizei instancecount);
typedef void (*PFN_glDrawElementsInstanced)(GLenum mode, GLsizei count, GLenum type, const void *indices, GLsizei instancecount);
typedef void (*PFN_glGetIntegerv)(GLenum pname, GLint *data);
typedef EGLBoolean (*PFN_eglSwapBuffers)(EGLDisplay dpy, EGLSurface surface);
typedef EGLBoolean (*PFN_eglSwapBuffersWithDamageKHR)(EGLDisplay dpy, EGLSurface surface, EGLint *rects, EGLint n_rects);
typedef __eglMustCastToProperFunctionPointerType (*PFN_eglGetProcAddress)(const char *procname);

static PFN_glUseProgram real_glUseProgram = NULL;
static PFN_glBindFramebuffer real_glBindFramebuffer = NULL;
static PFN_glViewport real_glViewport = NULL;
static PFN_glCreateShader real_glCreateShader = NULL;
static PFN_glShaderSource real_glShaderSource = NULL;
static PFN_glAttachShader real_glAttachShader = NULL;
static PFN_glDrawArrays real_glDrawArrays = NULL;
static PFN_glDrawElements real_glDrawElements = NULL;
static PFN_glDrawArraysInstanced real_glDrawArraysInstanced = NULL;
static PFN_glDrawElementsInstanced real_glDrawElementsInstanced = NULL;
static PFN_glGetIntegerv real_glGetIntegerv = NULL;
static PFN_eglSwapBuffers real_eglSwapBuffers = NULL;
static PFN_eglSwapBuffersWithDamageKHR real_eglSwapBuffersWithDamageKHR = NULL;
static PFN_eglGetProcAddress real_eglGetProcAddress = NULL;

static void probe_existing_gl_programs_once(void) {
    if (g_gl_probed_existing) return;
    if (!real_glGetIntegerv) return;
    g_gl_probed_existing = 1;

    GLint vp[4] = {0, 0, 1920, 1080};
    real_glGetIntegerv(GL_VIEWPORT, vp);
    if (vp[2] > 0 && vp[3] > 0) {
        g_cur_gl_vp_w = vp[2];
        g_cur_gl_vp_h = vp[3];
    }

    GLint cur_prog = 0;
    real_glGetIntegerv(GL_CURRENT_PROGRAM, &cur_prog);
    if (cur_prog > 0 && g_cur_gl_program == 0) {
        g_cur_gl_program = (GLuint)cur_prog;
    }

    GLint cur_fbo = 0;
    real_glGetIntegerv(GL_FRAMEBUFFER_BINDING, &cur_fbo);
    if (cur_fbo >= 0 && g_cur_gl_fbo == 0) {
        g_cur_gl_fbo = (GLuint)cur_fbo;
    }
}

static int should_allow_gl_draw_and_record(GLsizei vert_or_idx_count) {
    g_seen_gles = 1;
    probe_existing_gl_programs_once();

    if (g_cur_gl_program == 0 && real_glGetIntegerv) {
        GLint cur_prog = 0;
        real_glGetIntegerv(GL_CURRENT_PROGRAM, &cur_prog);
        if (cur_prog > 0) g_cur_gl_program = (GLuint)cur_prog;
    }

    pthread_mutex_lock(&g_mutex);

    char pass_name[96];
    if (g_cur_gl_fbo == 0) {
        snprintf(pass_name, sizeof(pass_name), "GLES Default Framebuffer (FBO #0)");
    } else {
        snprintf(pass_name, sizeof(pass_name), "GLES Offscreen RenderPass (FBO #%u)", (unsigned)g_cur_gl_fbo);
    }
    InspectorNode *pass_node = find_or_create_node_locked(
        CAT_RENDER_PASS,
        API_GLES,
        (uint64_t)g_cur_gl_fbo,
        0xFB000000u ^ (g_cur_gl_fbo * 2654435761u),
        "gl_pass",
        pass_name,
        g_cur_gl_vp_w,
        g_cur_gl_vp_h
    );
    if (pass_node) pass_node->draw_calls_accum++;

    InspectorNode *prog_node = NULL;
    InspectorNode *sub_node = NULL;
    if (g_cur_gl_program != 0) {
        char prog_name[96];
        snprintf(prog_name, sizeof(prog_name), "GLES Program Pipeline #%u", (unsigned)g_cur_gl_program);
        prog_node = find_or_create_node_locked(
            CAT_PIPELINE,
            API_GLES,
            (uint64_t)g_cur_gl_program,
            0x70000000u ^ (g_cur_gl_program * 2246822519u),
            "gl_prog",
            prog_name,
            g_cur_gl_vp_w,
            g_cur_gl_vp_h
        );
        if (prog_node) {
            if (prog_node->vs_key == 0) {
                uint64_t synth_vs = 0x10000000ULL | (uint64_t)g_cur_gl_program;
                char vs_name[96];
                snprintf(vs_name, sizeof(vs_name), "GLES Vertex Shader (Prog #%u)", (unsigned)g_cur_gl_program);
                find_or_create_node_locked(
                    CAT_VERTEX_SHADER,
                    API_GLES,
                    synth_vs,
                    0x51000000u ^ (g_cur_gl_program * 2654435761u),
                    "gl_vs_prog",
                    vs_name,
                    g_cur_gl_vp_w,
                    g_cur_gl_vp_h
                );
                prog_node->vs_key = synth_vs;
            }
            if (prog_node->fs_key == 0) {
                uint64_t synth_fs = 0x20000000ULL | (uint64_t)g_cur_gl_program;
                char fs_name[96];
                snprintf(fs_name, sizeof(fs_name), "GLES Pixel Shader (Prog #%u)", (unsigned)g_cur_gl_program);
                find_or_create_node_locked(
                    CAT_FRAGMENT_SHADER,
                    API_GLES,
                    synth_fs,
                    0x61000000u ^ (g_cur_gl_program * 2654435761u),
                    "gl_fs_prog",
                    fs_name,
                    g_cur_gl_vp_w,
                    g_cur_gl_vp_h
                );
                prog_node->fs_key = synth_fs;
            }
            prog_node->draw_calls_accum++;
            for (int i = 0; i < g_node_count; i++) {
                if (!g_nodes[i].in_use || g_nodes[i].api != API_GLES) continue;
                if (g_nodes[i].category == CAT_VERTEX_SHADER && g_nodes[i].handle_key == prog_node->vs_key && prog_node->vs_key != 0) {
                    g_nodes[i].draw_calls_accum++;
                }
                if (g_nodes[i].category == CAT_FRAGMENT_SHADER && g_nodes[i].handle_key == prog_node->fs_key && prog_node->fs_key != 0) {
                    g_nodes[i].draw_calls_accum++;
                }
            }
            if (prog_node->split_enabled && vert_or_idx_count > 0) {
                uint32_t vcnt = (uint32_t)vert_or_idx_count;
                uint64_t sub_key = ((uint64_t)g_cur_gl_program << 32) | (uint64_t)vcnt;
                uint32_t sub_hash = prog_node->hash ^ (vcnt * 2654435761u);
                char sub_name[96];
                snprintf(sub_name, sizeof(sub_name), "  ↳ Sub-Draw (%u verts/idx) [Prog #%u]", vcnt, (unsigned)g_cur_gl_program);
                sub_node = find_or_create_node_locked(
                    CAT_SUB_DRAW,
                    API_GLES,
                    sub_key,
                    sub_hash,
                    "gl_sub",
                    sub_name,
                    g_cur_gl_vp_w,
                    g_cur_gl_vp_h
                );
                if (sub_node) {
                    sub_node->parent_pipe_key = (uint64_t)g_cur_gl_program;
                    sub_node->draw_calls_accum++;
                }
            }
        }
    }

    int allow = 1;
    if (pass_node && !pass_node->enabled) allow = 0;
    if (prog_node && !prog_node->enabled) allow = 0;
    if (sub_node && !sub_node->enabled) allow = 0;
    if (prog_node && prog_node->vs_key != 0 && !is_node_enabled_locked(CAT_VERTEX_SHADER, API_GLES, prog_node->vs_key)) {
        allow = 0;
    }
    if (prog_node && prog_node->fs_key != 0 && !is_node_enabled_locked(CAT_FRAGMENT_SHADER, API_GLES, prog_node->fs_key)) {
        allow = 0;
    }

    if (!g_gl_has_swap_hook && ++g_gl_draws_since_rollover >= 2048) {
        g_gl_draws_since_rollover = 0;
        end_of_frame_rollover_locked(API_GLES);
    }

    pthread_mutex_unlock(&g_mutex);
    return allow;
}

static void hook_glUseProgram(GLuint program) {
    g_seen_gles = 1;
    g_cur_gl_program = program;
    if (real_glUseProgram) real_glUseProgram(program);
}

static void hook_glBindFramebuffer(GLenum target, GLuint framebuffer) {
    g_seen_gles = 1;
    if (target == GL_FRAMEBUFFER || target == GL_DRAW_FRAMEBUFFER) {
        g_cur_gl_fbo = framebuffer;
    }
    if (real_glBindFramebuffer) real_glBindFramebuffer(target, framebuffer);
}

static void hook_glViewport(GLint x, GLint y, GLsizei width, GLsizei height) {
    if (width > 0 && height > 0) {
        g_cur_gl_vp_w = width;
        g_cur_gl_vp_h = height;
    }
    if (real_glViewport) real_glViewport(x, y, width, height);
}

static GLuint hook_glCreateShader(GLenum type) {
    GLuint sh = real_glCreateShader ? real_glCreateShader(type) : 0;
    if (sh != 0) {
        pthread_mutex_lock(&g_mutex);
        char name[96];
        if (type == GL_VERTEX_SHADER) {
            snprintf(name, sizeof(name), "GLES Vertex Shader #%u", (unsigned)sh);
            find_or_create_node_locked(
                CAT_VERTEX_SHADER, API_GLES, (uint64_t)sh,
                0x50000000u ^ (sh * 2654435761u),
                "gl_vs", name, g_cur_gl_vp_w, g_cur_gl_vp_h
            );
        } else if (type == GL_FRAGMENT_SHADER) {
            snprintf(name, sizeof(name), "GLES Pixel Shader #%u", (unsigned)sh);
            find_or_create_node_locked(
                CAT_FRAGMENT_SHADER, API_GLES, (uint64_t)sh,
                0x60000000u ^ (sh * 2654435761u),
                "gl_fs", name, g_cur_gl_vp_w, g_cur_gl_vp_h
            );
        }
        pthread_mutex_unlock(&g_mutex);
    }
    return sh;
}

static void hook_glShaderSource(GLuint shader, GLsizei count, const GLchar *const *string, const GLint *length) {
    if (real_glShaderSource) real_glShaderSource(shader, count, string, length);
    if (shader != 0 && count > 0 && string != NULL && string[0] != NULL) {
        size_t len = (length && length[0] > 0) ? (size_t)length[0] : strlen(string[0]);
        uint32_t crc = compute_crc32(string[0], len);
        pthread_mutex_lock(&g_mutex);
        for (int i = 0; i < g_node_count; i++) {
            if (g_nodes[i].in_use && g_nodes[i].api == API_GLES && g_nodes[i].handle_key == (uint64_t)shader) {
                g_nodes[i].hash = crc;
                snprintf(
                    g_nodes[i].id,
                    sizeof(g_nodes[i].id),
                    "%s_%08X",
                    g_nodes[i].category == CAT_VERTEX_SHADER ? "gl_vs" : "gl_fs",
                    crc
                );
                break;
            }
        }
        pthread_mutex_unlock(&g_mutex);
    }
}

static void hook_glAttachShader(GLuint program, GLuint shader) {
    if (real_glAttachShader) real_glAttachShader(program, shader);
    pthread_mutex_lock(&g_mutex);
    char prog_name[96];
    snprintf(prog_name, sizeof(prog_name), "GLES Program Pipeline #%u", (unsigned)program);
    InspectorNode *pnode = find_or_create_node_locked(
        CAT_PIPELINE, API_GLES, (uint64_t)program,
        0x70000000u ^ (program * 2246822519u),
        "gl_prog", prog_name, g_cur_gl_vp_w, g_cur_gl_vp_h
    );
    if (pnode) {
        for (int i = 0; i < g_node_count; i++) {
            if (g_nodes[i].in_use && g_nodes[i].api == API_GLES && g_nodes[i].handle_key == (uint64_t)shader) {
                if (g_nodes[i].category == CAT_VERTEX_SHADER) pnode->vs_key = shader;
                if (g_nodes[i].category == CAT_FRAGMENT_SHADER) pnode->fs_key = shader;
            }
        }
    }
    pthread_mutex_unlock(&g_mutex);
}

static void hook_glDrawArrays(GLenum mode, GLint first, GLsizei count) {
    if (!should_allow_gl_draw_and_record(count)) return;
    if (real_glDrawArrays) real_glDrawArrays(mode, first, count);
}

static void hook_glDrawElements(GLenum mode, GLsizei count, GLenum type, const void *indices) {
    if (!should_allow_gl_draw_and_record(count)) return;
    if (real_glDrawElements) real_glDrawElements(mode, count, type, indices);
}

static void hook_glDrawArraysInstanced(GLenum mode, GLint first, GLsizei count, GLsizei instancecount) {
    if (!should_allow_gl_draw_and_record(count)) return;
    if (real_glDrawArraysInstanced) real_glDrawArraysInstanced(mode, first, count, instancecount);
}

static void hook_glDrawElementsInstanced(GLenum mode, GLsizei count, GLenum type, const void *indices, GLsizei instancecount) {
    if (!should_allow_gl_draw_and_record(count)) return;
    if (real_glDrawElementsInstanced) real_glDrawElementsInstanced(mode, count, type, indices, instancecount);
}

static EGLBoolean hook_eglSwapBuffers(EGLDisplay dpy, EGLSurface surface) {
    g_seen_gles = 1;
    g_gl_has_swap_hook = 1;
    probe_existing_gl_programs_once();
    g_gl_draws_since_rollover = 0;
    end_of_frame_rollover(API_GLES);
    return real_eglSwapBuffers ? real_eglSwapBuffers(dpy, surface) : EGL_TRUE;
}

static EGLBoolean hook_eglSwapBuffersWithDamageKHR(EGLDisplay dpy, EGLSurface surface, EGLint *rects, EGLint n_rects) {
    g_seen_gles = 1;
    g_gl_has_swap_hook = 1;
    probe_existing_gl_programs_once();
    g_gl_draws_since_rollover = 0;
    end_of_frame_rollover(API_GLES);
    return real_eglSwapBuffersWithDamageKHR
        ? real_eglSwapBuffersWithDamageKHR(dpy, surface, rects, n_rects)
        : EGL_TRUE;
}

/* =========================================================================
 * Vulkan Layer & Live Dispatch Hooks (VK_LAYER_MEGINGIARD_INSPECTOR)
 * ========================================================================= */

typedef void (VKAPI_PTR *PFN_vkCmdBeginRenderPass2_Compat)(
    VkCommandBuffer commandBuffer,
    const VkRenderPassBeginInfo *pRenderPassBegin,
    const void *pSubpassBeginInfo
);
typedef void (VKAPI_PTR *PFN_vkCmdEndRenderPass2_Compat)(
    VkCommandBuffer commandBuffer,
    const void *pSubpassEndInfo
);
typedef void (VKAPI_PTR *PFN_vkCmdBeginRendering_Compat)(
    VkCommandBuffer commandBuffer,
    const void *pRenderingInfo
);
typedef void (VKAPI_PTR *PFN_vkCmdEndRendering_Compat)(
    VkCommandBuffer commandBuffer
);
typedef VkResult (VKAPI_PTR *PFN_vkQueueSubmit2_Compat)(
    VkQueue queue,
    uint32_t submitCount,
    const void *pSubmits,
    VkFence fence
);

typedef struct {
    uint32_t sType;
    const void *pNext;
    uint32_t flags;
    VkRect2D renderArea;
    uint32_t layerCount;
    uint32_t viewMask;
    uint32_t colorAttachmentCount;
    const void *pColorAttachments;
    const void *pDepthAttachment;
    const void *pStencilAttachment;
} VkRenderingInfo_Compat;

static PFN_vkGetInstanceProcAddr real_vkGetInstanceProcAddr = NULL;
static PFN_vkGetDeviceProcAddr real_vkGetDeviceProcAddr = NULL;
static PFN_vkCreateShaderModule real_vkCreateShaderModule = NULL;
static PFN_vkCreateGraphicsPipelines real_vkCreateGraphicsPipelines = NULL;
static PFN_vkCreateComputePipelines real_vkCreateComputePipelines = NULL;
static PFN_vkCmdBeginRenderPass real_vkCmdBeginRenderPass = NULL;
static PFN_vkCmdEndRenderPass real_vkCmdEndRenderPass = NULL;
static PFN_vkCmdBeginRenderPass2_Compat real_vkCmdBeginRenderPass2 = NULL;
static PFN_vkCmdEndRenderPass2_Compat real_vkCmdEndRenderPass2 = NULL;
static PFN_vkCmdBeginRendering_Compat real_vkCmdBeginRendering = NULL;
static PFN_vkCmdBeginRendering_Compat real_vkCmdBeginRenderingKHR = NULL;
static PFN_vkCmdEndRendering_Compat real_vkCmdEndRendering = NULL;
static PFN_vkCmdEndRendering_Compat real_vkCmdEndRenderingKHR = NULL;
static PFN_vkCmdBindPipeline real_vkCmdBindPipeline = NULL;
static PFN_vkCmdDraw real_vkCmdDraw = NULL;
static PFN_vkCmdDrawIndexed real_vkCmdDrawIndexed = NULL;
static PFN_vkCmdDrawIndirect real_vkCmdDrawIndirect = NULL;
static PFN_vkCmdDrawIndexedIndirect real_vkCmdDrawIndexedIndirect = NULL;
static PFN_vkCmdDispatch real_vkCmdDispatch = NULL;
static PFN_vkQueueSubmit real_vkQueueSubmit = NULL;
static PFN_vkQueueSubmit2_Compat real_vkQueueSubmit2 = NULL;
static PFN_vkQueuePresentKHR real_vkQueuePresentKHR = NULL;

static int should_allow_vk_draw_and_record(VkCommandBuffer commandBuffer, int is_compute, uint32_t vert_or_idx_count) {
    g_seen_vk = 1;
    pthread_mutex_lock(&g_mutex);

    VkCmdBufState *cs = get_or_alloc_cmd_state_locked(commandBuffer);
    uint64_t rp_key = (cs && cs->render_pass != 0) ? cs->render_pass : g_cur_vk_render_pass;
    uint64_t pipe_key = is_compute
        ? ((cs && cs->comp_pipeline != 0) ? cs->comp_pipeline : g_cur_vk_comp_pipeline)
        : ((cs && cs->gfx_pipeline != 0) ? cs->gfx_pipeline : g_cur_vk_pipeline);
    int rp_w = (cs && cs->rp_w > 0) ? cs->rp_w : g_cur_vk_rp_w;
    int rp_h = (cs && cs->rp_h > 0) ? cs->rp_h : g_cur_vk_rp_h;

    InspectorNode *pass_node = NULL;
    if (!is_compute) {
        if (rp_key != 0) {
            uint32_t rp_hash = mix_hash64(rp_key);
            char pass_name[96];
            snprintf(pass_name, sizeof(pass_name), "Vulkan RenderPass 0x%08X", rp_hash);
            pass_node = find_or_create_node_locked(
                CAT_RENDER_PASS, API_VK, rp_key, rp_hash,
                "vk_pass", pass_name, rp_w, rp_h
            );
            if (pass_node) pass_node->draw_calls_accum++;
        } else {
            pass_node = find_or_create_node_locked(
                CAT_RENDER_PASS, API_VK, 0, 0xFB000001u,
                "vk_pass", "Vulkan Default RenderPass", rp_w, rp_h
            );
            if (pass_node) pass_node->draw_calls_accum++;
        }
    } else {
        pass_node = find_or_create_node_locked(
            CAT_RENDER_PASS, API_VK, 0xC0400001ULL, 0xC0400001u,
            "vk_pass", "Vulkan Compute Pass", rp_w, rp_h
        );
        if (pass_node) pass_node->draw_calls_accum++;
    }

    InspectorNode *pipe_node = NULL;
    InspectorNode *sub_node = NULL;
    if (pipe_key != 0) {
        uint32_t p_hash = mix_hash64(pipe_key);
        char pipe_name[96];
        snprintf(
            pipe_name,
            sizeof(pipe_name),
            is_compute ? "Vulkan ComputePipeline 0x%08X" : "Vulkan GraphicsPipeline 0x%08X",
            p_hash
        );
        pipe_node = find_or_create_node_locked(
            CAT_PIPELINE, API_VK, pipe_key, p_hash,
            "vk_pipe", pipe_name, rp_w, rp_h
        );
        if (pipe_node) {
            /* If pipeline was created before live attachment, synthesize linked VS/FS shader nodes */
            if (pipe_node->vs_key == 0 && pipe_node->fs_key == 0) {
                if (!is_compute) {
                    uint64_t synth_vs = pipe_key ^ 0x5653000000000000ULL;
                    uint64_t synth_fs = pipe_key ^ 0x4653000000000000ULL;
                    uint32_t vs_hash = mix_hash64(synth_vs);
                    uint32_t fs_hash = mix_hash64(synth_fs);
                    char vs_name[96];
                    char fs_name[96];
                    snprintf(vs_name, sizeof(vs_name), "Vulkan Vertex Shader 0x%08X", vs_hash);
                    snprintf(fs_name, sizeof(fs_name), "Vulkan Pixel Shader 0x%08X", fs_hash);
                    if (find_or_create_node_locked(CAT_VERTEX_SHADER, API_VK, synth_vs, vs_hash, "vk_vs", vs_name, rp_w, rp_h)) {
                        pipe_node->vs_key = synth_vs;
                    }
                    if (find_or_create_node_locked(CAT_FRAGMENT_SHADER, API_VK, synth_fs, fs_hash, "vk_fs", fs_name, rp_w, rp_h)) {
                        pipe_node->fs_key = synth_fs;
                    }
                } else {
                    uint64_t synth_cs = pipe_key ^ 0x4353000000000000ULL;
                    uint32_t cs_hash = mix_hash64(synth_cs);
                    char cs_name[96];
                    snprintf(cs_name, sizeof(cs_name), "Vulkan Compute Shader 0x%08X", cs_hash);
                    if (find_or_create_node_locked(CAT_FRAGMENT_SHADER, API_VK, synth_cs, cs_hash, "vk_fs", cs_name, rp_w, rp_h)) {
                        pipe_node->fs_key = synth_cs;
                    }
                }
            }

            pipe_node->draw_calls_accum++;
            for (int i = 0; i < g_node_count; i++) {
                if (!g_nodes[i].in_use || g_nodes[i].api != API_VK) continue;
                if (g_nodes[i].category == CAT_VERTEX_SHADER && g_nodes[i].handle_key == pipe_node->vs_key && pipe_node->vs_key != 0) {
                    g_nodes[i].draw_calls_accum++;
                }
                if (g_nodes[i].category == CAT_FRAGMENT_SHADER && g_nodes[i].handle_key == pipe_node->fs_key && pipe_node->fs_key != 0) {
                    g_nodes[i].draw_calls_accum++;
                }
            }
            if (pipe_node->split_enabled && !is_compute && vert_or_idx_count > 0) {
                uint64_t sub_key = (pipe_key ^ ((uint64_t)vert_or_idx_count << 32)) | (uint64_t)vert_or_idx_count;
                uint32_t sub_hash = pipe_node->hash ^ (vert_or_idx_count * 2654435761u);
                char sub_name[96];
                snprintf(sub_name, sizeof(sub_name), "  ↳ Sub-Draw (%u verts/idx) [Pipe 0x%08X]", vert_or_idx_count, pipe_node->hash);
                sub_node = find_or_create_node_locked(
                    CAT_SUB_DRAW,
                    API_VK,
                    sub_key,
                    sub_hash,
                    "vk_sub",
                    sub_name,
                    rp_w,
                    rp_h
                );
                if (sub_node) {
                    sub_node->parent_pipe_key = pipe_key;
                    sub_node->draw_calls_accum++;
                }
            }
        }
    }

    int allow = 1;
    if (pass_node && !pass_node->enabled) allow = 0;
    if (pipe_node && !pipe_node->enabled) allow = 0;
    if (sub_node && !sub_node->enabled) allow = 0;
    if (pipe_node && pipe_node->vs_key != 0 && !is_node_enabled_locked(CAT_VERTEX_SHADER, API_VK, pipe_node->vs_key)) {
        allow = 0;
    }
    if (pipe_node && pipe_node->fs_key != 0 && !is_node_enabled_locked(CAT_FRAGMENT_SHADER, API_VK, pipe_node->fs_key)) {
        allow = 0;
    }

    if (!g_vk_has_present_hook && ++g_vk_draws_since_rollover >= 2048) {
        g_vk_draws_since_rollover = 0;
        end_of_frame_rollover_locked(API_VK);
    }

    pthread_mutex_unlock(&g_mutex);
    return allow;
}

static VKAPI_ATTR VkResult VKAPI_CALL hook_vkCreateShaderModule(
    VkDevice device,
    const VkShaderModuleCreateInfo *pCreateInfo,
    const VkAllocationCallbacks *pAllocator,
    VkShaderModule *pShaderModule
) {
    g_seen_vk = 1;
    VkResult res = real_vkCreateShaderModule
        ? real_vkCreateShaderModule(device, pCreateInfo, pAllocator, pShaderModule)
        : VK_ERROR_INITIALIZATION_FAILED;
    if (res == VK_SUCCESS && pShaderModule && *pShaderModule != VK_NULL_HANDLE &&
        pCreateInfo && pCreateInfo->pCode && pCreateInfo->codeSize > 0) {
        uint32_t spirv_crc = compute_crc32(pCreateInfo->pCode, pCreateInfo->codeSize);
        uint64_t handle_key = (uint64_t)(uintptr_t)(*pShaderModule);
        pthread_mutex_lock(&g_mutex);
        char name[96];
        snprintf(name, sizeof(name), "SPIR-V Shader 0x%08X (%zu B)", spirv_crc, pCreateInfo->codeSize);
        find_or_create_node_locked(
            CAT_FRAGMENT_SHADER, API_VK, handle_key, spirv_crc,
            "vk_sh", name, g_cur_vk_rp_w, g_cur_vk_rp_h
        );
        pthread_mutex_unlock(&g_mutex);
    }
    return res;
}

static VKAPI_ATTR VkResult VKAPI_CALL hook_vkCreateGraphicsPipelines(
    VkDevice device,
    VkPipelineCache pipelineCache,
    uint32_t createInfoCount,
    const VkGraphicsPipelineCreateInfo *pCreateInfos,
    const VkAllocationCallbacks *pAllocator,
    VkPipeline *pPipelines
) {
    g_seen_vk = 1;
    VkResult res = real_vkCreateGraphicsPipelines
        ? real_vkCreateGraphicsPipelines(device, pipelineCache, createInfoCount, pCreateInfos, pAllocator, pPipelines)
        : VK_ERROR_INITIALIZATION_FAILED;
    if (res == VK_SUCCESS && pCreateInfos && pPipelines) {
        pthread_mutex_lock(&g_mutex);
        for (uint32_t c = 0; c < createInfoCount; c++) {
            uint64_t pipe_key = (uint64_t)(uintptr_t)pPipelines[c];
            if (pipe_key == 0) continue;
            uint64_t vs_key = 0;
            uint64_t fs_key = 0;
            for (uint32_t s = 0; s < pCreateInfos[c].stageCount; s++) {
                const VkPipelineShaderStageCreateInfo *st = &pCreateInfos[c].pStages[s];
                uint64_t mod_key = (uint64_t)(uintptr_t)st->module;
                if (mod_key == 0) continue;
                if (st->stage == VK_SHADER_STAGE_VERTEX_BIT) {
                    vs_key = mod_key;
                    for (int i = 0; i < g_node_count; i++) {
                        if (g_nodes[i].in_use && g_nodes[i].api == API_VK && g_nodes[i].handle_key == mod_key) {
                            g_nodes[i].category = CAT_VERTEX_SHADER;
                            snprintf(g_nodes[i].id, sizeof(g_nodes[i].id), "vk_vs_%08X", g_nodes[i].hash);
                            snprintf(g_nodes[i].name, sizeof(g_nodes[i].name), "Vulkan Vertex Shader 0x%08X", g_nodes[i].hash);
                        }
                    }
                } else if (st->stage == VK_SHADER_STAGE_FRAGMENT_BIT) {
                    fs_key = mod_key;
                    for (int i = 0; i < g_node_count; i++) {
                        if (g_nodes[i].in_use && g_nodes[i].api == API_VK && g_nodes[i].handle_key == mod_key) {
                            g_nodes[i].category = CAT_FRAGMENT_SHADER;
                            snprintf(g_nodes[i].id, sizeof(g_nodes[i].id), "vk_fs_%08X", g_nodes[i].hash);
                            snprintf(g_nodes[i].name, sizeof(g_nodes[i].name), "Vulkan Pixel Shader 0x%08X", g_nodes[i].hash);
                        }
                    }
                }
            }
            uint32_t p_hash = mix_hash64(pipe_key);
            char pipe_name[96];
            snprintf(pipe_name, sizeof(pipe_name), "Vulkan GraphicsPipeline 0x%08X", p_hash);
            InspectorNode *pnode = find_or_create_node_locked(
                CAT_PIPELINE, API_VK, pipe_key, p_hash,
                "vk_pipe", pipe_name, g_cur_vk_rp_w, g_cur_vk_rp_h
            );
            if (pnode) {
                pnode->vs_key = vs_key;
                pnode->fs_key = fs_key;
            }
        }
        pthread_mutex_unlock(&g_mutex);
    }
    return res;
}

static VKAPI_ATTR VkResult VKAPI_CALL hook_vkCreateComputePipelines(
    VkDevice device,
    VkPipelineCache pipelineCache,
    uint32_t createInfoCount,
    const VkComputePipelineCreateInfo *pCreateInfos,
    const VkAllocationCallbacks *pAllocator,
    VkPipeline *pPipelines
) {
    g_seen_vk = 1;
    VkResult res = real_vkCreateComputePipelines
        ? real_vkCreateComputePipelines(device, pipelineCache, createInfoCount, pCreateInfos, pAllocator, pPipelines)
        : VK_ERROR_INITIALIZATION_FAILED;
    if (res == VK_SUCCESS && pCreateInfos && pPipelines) {
        pthread_mutex_lock(&g_mutex);
        for (uint32_t c = 0; c < createInfoCount; c++) {
            uint64_t pipe_key = (uint64_t)(uintptr_t)pPipelines[c];
            if (pipe_key == 0) continue;
            uint64_t cs_key = (uint64_t)(uintptr_t)pCreateInfos[c].stage.module;
            uint32_t p_hash = mix_hash64(pipe_key);
            char pipe_name[96];
            snprintf(pipe_name, sizeof(pipe_name), "Vulkan ComputePipeline 0x%08X", p_hash);
            InspectorNode *pnode = find_or_create_node_locked(
                CAT_PIPELINE, API_VK, pipe_key, p_hash,
                "vk_pipe", pipe_name, g_cur_vk_rp_w, g_cur_vk_rp_h
            );
            if (pnode && cs_key != 0) {
                pnode->fs_key = cs_key;
            }
        }
        pthread_mutex_unlock(&g_mutex);
    }
    return res;
}

static void record_vk_begin_render_pass(VkCommandBuffer commandBuffer, const VkRenderPassBeginInfo *pRenderPassBegin) {
    g_seen_vk = 1;
    if (!pRenderPassBegin) return;
    uint64_t rp = (uint64_t)(uintptr_t)pRenderPassBegin->renderPass;
    int w = (int)pRenderPassBegin->renderArea.extent.width;
    int h = (int)pRenderPassBegin->renderArea.extent.height;
    pthread_mutex_lock(&g_mutex);
    g_cur_vk_render_pass = rp;
    if (w > 0 && h > 0) {
        g_cur_vk_rp_w = w;
        g_cur_vk_rp_h = h;
    }
    VkCmdBufState *cs = get_or_alloc_cmd_state_locked(commandBuffer);
    if (cs) {
        cs->render_pass = rp;
        if (w > 0 && h > 0) {
            cs->rp_w = w;
            cs->rp_h = h;
        }
    }
    pthread_mutex_unlock(&g_mutex);
}

static void record_vk_end_render_pass(VkCommandBuffer commandBuffer) {
    pthread_mutex_lock(&g_mutex);
    g_cur_vk_render_pass = 0;
    VkCmdBufState *cs = get_or_alloc_cmd_state_locked(commandBuffer);
    if (cs) {
        cs->render_pass = 0;
    }
    pthread_mutex_unlock(&g_mutex);
}

static void record_vk_begin_rendering(VkCommandBuffer commandBuffer, const void *pRenderingInfo) {
    g_seen_vk = 1;
    if (!pRenderingInfo) return;
    const VkRenderingInfo_Compat *ri = (const VkRenderingInfo_Compat *)pRenderingInfo;
    int w = (int)ri->renderArea.extent.width;
    int h = (int)ri->renderArea.extent.height;
    uint64_t rp_key = ((uint64_t)(uint32_t)w << 32) ^
                      ((uint64_t)(uint32_t)h << 16) ^
                      ((uint64_t)ri->colorAttachmentCount << 8) ^
                      (ri->pDepthAttachment != NULL ? 1ULL : 0ULL) ^
                      0xD9400000ULL;
    pthread_mutex_lock(&g_mutex);
    g_cur_vk_render_pass = rp_key;
    if (w > 0 && h > 0) {
        g_cur_vk_rp_w = w;
        g_cur_vk_rp_h = h;
    }
    VkCmdBufState *cs = get_or_alloc_cmd_state_locked(commandBuffer);
    if (cs) {
        cs->render_pass = rp_key;
        if (w > 0 && h > 0) {
            cs->rp_w = w;
            cs->rp_h = h;
        }
    }
    pthread_mutex_unlock(&g_mutex);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdBeginRenderPass(
    VkCommandBuffer commandBuffer,
    const VkRenderPassBeginInfo *pRenderPassBegin,
    VkSubpassContents contents
) {
    record_vk_begin_render_pass(commandBuffer, pRenderPassBegin);
    if (real_vkCmdBeginRenderPass) real_vkCmdBeginRenderPass(commandBuffer, pRenderPassBegin, contents);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdEndRenderPass(VkCommandBuffer commandBuffer) {
    record_vk_end_render_pass(commandBuffer);
    if (real_vkCmdEndRenderPass) real_vkCmdEndRenderPass(commandBuffer);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdBeginRenderPass2(
    VkCommandBuffer commandBuffer,
    const VkRenderPassBeginInfo *pRenderPassBegin,
    const void *pSubpassBeginInfo
) {
    record_vk_begin_render_pass(commandBuffer, pRenderPassBegin);
    if (real_vkCmdBeginRenderPass2) real_vkCmdBeginRenderPass2(commandBuffer, pRenderPassBegin, pSubpassBeginInfo);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdEndRenderPass2(
    VkCommandBuffer commandBuffer,
    const void *pSubpassEndInfo
) {
    record_vk_end_render_pass(commandBuffer);
    if (real_vkCmdEndRenderPass2) real_vkCmdEndRenderPass2(commandBuffer, pSubpassEndInfo);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdBeginRendering(
    VkCommandBuffer commandBuffer,
    const void *pRenderingInfo
) {
    record_vk_begin_rendering(commandBuffer, pRenderingInfo);
    if (real_vkCmdBeginRendering) real_vkCmdBeginRendering(commandBuffer, pRenderingInfo);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdBeginRenderingKHR(
    VkCommandBuffer commandBuffer,
    const void *pRenderingInfo
) {
    record_vk_begin_rendering(commandBuffer, pRenderingInfo);
    if (real_vkCmdBeginRenderingKHR) {
        real_vkCmdBeginRenderingKHR(commandBuffer, pRenderingInfo);
    } else if (real_vkCmdBeginRendering) {
        real_vkCmdBeginRendering(commandBuffer, pRenderingInfo);
    }
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdEndRendering(VkCommandBuffer commandBuffer) {
    record_vk_end_render_pass(commandBuffer);
    if (real_vkCmdEndRendering) real_vkCmdEndRendering(commandBuffer);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdEndRenderingKHR(VkCommandBuffer commandBuffer) {
    record_vk_end_render_pass(commandBuffer);
    if (real_vkCmdEndRenderingKHR) {
        real_vkCmdEndRenderingKHR(commandBuffer);
    } else if (real_vkCmdEndRendering) {
        real_vkCmdEndRendering(commandBuffer);
    }
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdBindPipeline(
    VkCommandBuffer commandBuffer,
    VkPipelineBindPoint pipelineBindPoint,
    VkPipeline pipeline
) {
    g_seen_vk = 1;
    uint64_t pkey = (uint64_t)(uintptr_t)pipeline;
    pthread_mutex_lock(&g_mutex);
    VkCmdBufState *cs = get_or_alloc_cmd_state_locked(commandBuffer);
    if (pipelineBindPoint == VK_PIPELINE_BIND_POINT_GRAPHICS) {
        g_cur_vk_pipeline = pkey;
        if (cs) cs->gfx_pipeline = pkey;
    } else if (pipelineBindPoint == VK_PIPELINE_BIND_POINT_COMPUTE) {
        g_cur_vk_comp_pipeline = pkey;
        if (cs) cs->comp_pipeline = pkey;
    }
    pthread_mutex_unlock(&g_mutex);
    if (real_vkCmdBindPipeline) real_vkCmdBindPipeline(commandBuffer, pipelineBindPoint, pipeline);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdDraw(
    VkCommandBuffer commandBuffer,
    uint32_t vertexCount,
    uint32_t instanceCount,
    uint32_t firstVertex,
    uint32_t firstInstance
) {
    if (!should_allow_vk_draw_and_record(commandBuffer, 0, vertexCount)) return;
    if (real_vkCmdDraw) real_vkCmdDraw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance);
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdDrawIndexed(
    VkCommandBuffer commandBuffer,
    uint32_t indexCount,
    uint32_t instanceCount,
    uint32_t firstIndex,
    int32_t vertexOffset,
    uint32_t firstInstance
) {
    if (!should_allow_vk_draw_and_record(commandBuffer, 0, indexCount)) return;
    if (real_vkCmdDrawIndexed) {
        real_vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdDrawIndirect(
    VkCommandBuffer commandBuffer,
    VkBuffer buffer,
    VkDeviceSize offset,
    uint32_t drawCount,
    uint32_t stride
) {
    if (!should_allow_vk_draw_and_record(commandBuffer, 0, drawCount)) return;
    if (real_vkCmdDrawIndirect) {
        real_vkCmdDrawIndirect(commandBuffer, buffer, offset, drawCount, stride);
    }
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdDrawIndexedIndirect(
    VkCommandBuffer commandBuffer,
    VkBuffer buffer,
    VkDeviceSize offset,
    uint32_t drawCount,
    uint32_t stride
) {
    if (!should_allow_vk_draw_and_record(commandBuffer, 0, drawCount)) return;
    if (real_vkCmdDrawIndexedIndirect) {
        real_vkCmdDrawIndexedIndirect(commandBuffer, buffer, offset, drawCount, stride);
    }
}

static VKAPI_ATTR void VKAPI_CALL hook_vkCmdDispatch(
    VkCommandBuffer commandBuffer,
    uint32_t groupCountX,
    uint32_t groupCountY,
    uint32_t groupCountZ
) {
    if (!should_allow_vk_draw_and_record(commandBuffer, 1, 0)) return;
    if (real_vkCmdDispatch) {
        real_vkCmdDispatch(commandBuffer, groupCountX, groupCountY, groupCountZ);
    }
}

static VKAPI_ATTR VkResult VKAPI_CALL hook_vkQueueSubmit(
    VkQueue queue,
    uint32_t submitCount,
    const VkSubmitInfo *pSubmits,
    VkFence fence
) {
    g_seen_vk = 1;
    if (!g_vk_has_present_hook && g_vk_draws_since_rollover > 0) {
        g_vk_draws_since_rollover = 0;
        end_of_frame_rollover(API_VK);
    }
    return real_vkQueueSubmit ? real_vkQueueSubmit(queue, submitCount, pSubmits, fence) : VK_SUCCESS;
}

static VKAPI_ATTR VkResult VKAPI_CALL hook_vkQueueSubmit2(
    VkQueue queue,
    uint32_t submitCount,
    const void *pSubmits,
    VkFence fence
) {
    g_seen_vk = 1;
    if (!g_vk_has_present_hook && g_vk_draws_since_rollover > 0) {
        g_vk_draws_since_rollover = 0;
        end_of_frame_rollover(API_VK);
    }
    return real_vkQueueSubmit2 ? real_vkQueueSubmit2(queue, submitCount, pSubmits, fence) : VK_SUCCESS;
}

static VKAPI_ATTR VkResult VKAPI_CALL hook_vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR *pPresentInfo) {
    g_seen_vk = 1;
    g_vk_has_present_hook = 1;
    g_vk_draws_since_rollover = 0;
    end_of_frame_rollover(API_VK);
    return real_vkQueuePresentKHR ? real_vkQueuePresentKHR(queue, pPresentInfo) : VK_SUCCESS;
}

static __eglMustCastToProperFunctionPointerType hook_eglGetProcAddress(const char *procname) {
    __eglMustCastToProperFunctionPointerType orig =
        real_eglGetProcAddress ? real_eglGetProcAddress(procname) : NULL;
    if (!procname) return orig;
    if (strcmp(procname, "glUseProgram") == 0) {
        if (!real_glUseProgram && orig) real_glUseProgram = (PFN_glUseProgram)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glUseProgram;
    }
    if (strcmp(procname, "glBindFramebuffer") == 0) {
        if (!real_glBindFramebuffer && orig) real_glBindFramebuffer = (PFN_glBindFramebuffer)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glBindFramebuffer;
    }
    if (strcmp(procname, "glViewport") == 0) {
        if (!real_glViewport && orig) real_glViewport = (PFN_glViewport)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glViewport;
    }
    if (strcmp(procname, "glCreateShader") == 0) {
        if (!real_glCreateShader && orig) real_glCreateShader = (PFN_glCreateShader)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glCreateShader;
    }
    if (strcmp(procname, "glShaderSource") == 0) {
        if (!real_glShaderSource && orig) real_glShaderSource = (PFN_glShaderSource)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glShaderSource;
    }
    if (strcmp(procname, "glAttachShader") == 0) {
        if (!real_glAttachShader && orig) real_glAttachShader = (PFN_glAttachShader)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glAttachShader;
    }
    if (strcmp(procname, "glDrawArrays") == 0) {
        if (!real_glDrawArrays && orig) real_glDrawArrays = (PFN_glDrawArrays)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glDrawArrays;
    }
    if (strcmp(procname, "glDrawElements") == 0) {
        if (!real_glDrawElements && orig) real_glDrawElements = (PFN_glDrawElements)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glDrawElements;
    }
    if (strcmp(procname, "glDrawArraysInstanced") == 0) {
        if (!real_glDrawArraysInstanced && orig) real_glDrawArraysInstanced = (PFN_glDrawArraysInstanced)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glDrawArraysInstanced;
    }
    if (strcmp(procname, "glDrawElementsInstanced") == 0) {
        if (!real_glDrawElementsInstanced && orig) real_glDrawElementsInstanced = (PFN_glDrawElementsInstanced)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_glDrawElementsInstanced;
    }
    if (strcmp(procname, "eglSwapBuffers") == 0) {
        if (!real_eglSwapBuffers && orig) real_eglSwapBuffers = (PFN_eglSwapBuffers)orig;
        return (__eglMustCastToProperFunctionPointerType)hook_eglSwapBuffers;
    }
    if (strcmp(procname, "eglSwapBuffersWithDamageKHR") == 0) {
        if (!real_eglSwapBuffersWithDamageKHR && orig) {
            real_eglSwapBuffersWithDamageKHR = (PFN_eglSwapBuffersWithDamageKHR)orig;
        }
        return (__eglMustCastToProperFunctionPointerType)hook_eglSwapBuffersWithDamageKHR;
    }
    return orig;
}

/* =========================================================================
 * Android Vulkan Layer Exports (VK_LAYER_MEGINGIARD_INSPECTOR)
 * ========================================================================= */

__attribute__((visibility("default")))
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(VkDevice device, const char *pName) {
    if (!pName) return NULL;
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0) return (PFN_vkVoidFunction)vkGetDeviceProcAddr;
    if (strcmp(pName, "vkCreateShaderModule") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCreateShaderModule) {
                real_vkCreateShaderModule = (PFN_vkCreateShaderModule)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCreateShaderModule;
    }
    if (strcmp(pName, "vkCreateGraphicsPipelines") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCreateGraphicsPipelines) {
                real_vkCreateGraphicsPipelines = (PFN_vkCreateGraphicsPipelines)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCreateGraphicsPipelines;
    }
    if (strcmp(pName, "vkCreateComputePipelines") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCreateComputePipelines) {
                real_vkCreateComputePipelines = (PFN_vkCreateComputePipelines)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCreateComputePipelines;
    }
    if (strcmp(pName, "vkCmdBeginRenderPass") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdBeginRenderPass) {
                real_vkCmdBeginRenderPass = (PFN_vkCmdBeginRenderPass)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdBeginRenderPass;
    }
    if (strcmp(pName, "vkCmdEndRenderPass") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdEndRenderPass) {
                real_vkCmdEndRenderPass = (PFN_vkCmdEndRenderPass)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdEndRenderPass;
    }
    if (strcmp(pName, "vkCmdBeginRenderPass2") == 0 || strcmp(pName, "vkCmdBeginRenderPass2KHR") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdBeginRenderPass2) {
                real_vkCmdBeginRenderPass2 = (PFN_vkCmdBeginRenderPass2_Compat)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdBeginRenderPass2;
    }
    if (strcmp(pName, "vkCmdEndRenderPass2") == 0 || strcmp(pName, "vkCmdEndRenderPass2KHR") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdEndRenderPass2) {
                real_vkCmdEndRenderPass2 = (PFN_vkCmdEndRenderPass2_Compat)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdEndRenderPass2;
    }
    if (strcmp(pName, "vkCmdBeginRendering") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdBeginRendering) {
                real_vkCmdBeginRendering = (PFN_vkCmdBeginRendering_Compat)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdBeginRendering;
    }
    if (strcmp(pName, "vkCmdBeginRenderingKHR") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdBeginRenderingKHR) {
                real_vkCmdBeginRenderingKHR = (PFN_vkCmdBeginRendering_Compat)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdBeginRenderingKHR;
    }
    if (strcmp(pName, "vkCmdEndRendering") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdEndRendering) {
                real_vkCmdEndRendering = (PFN_vkCmdEndRendering_Compat)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdEndRendering;
    }
    if (strcmp(pName, "vkCmdEndRenderingKHR") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdEndRenderingKHR) {
                real_vkCmdEndRenderingKHR = (PFN_vkCmdEndRendering_Compat)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdEndRenderingKHR;
    }
    if (strcmp(pName, "vkCmdBindPipeline") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdBindPipeline) {
                real_vkCmdBindPipeline = (PFN_vkCmdBindPipeline)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdBindPipeline;
    }
    if (strcmp(pName, "vkCmdDraw") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdDraw) {
                real_vkCmdDraw = (PFN_vkCmdDraw)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdDraw;
    }
    if (strcmp(pName, "vkCmdDrawIndexed") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdDrawIndexed) {
                real_vkCmdDrawIndexed = (PFN_vkCmdDrawIndexed)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdDrawIndexed;
    }
    if (strcmp(pName, "vkCmdDrawIndirect") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdDrawIndirect) {
                real_vkCmdDrawIndirect = (PFN_vkCmdDrawIndirect)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdDrawIndirect;
    }
    if (strcmp(pName, "vkCmdDrawIndexedIndirect") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdDrawIndexedIndirect) {
                real_vkCmdDrawIndexedIndirect = (PFN_vkCmdDrawIndexedIndirect)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdDrawIndexedIndirect;
    }
    if (strcmp(pName, "vkCmdDispatch") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkCmdDispatch) {
                real_vkCmdDispatch = (PFN_vkCmdDispatch)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkCmdDispatch;
    }
    if (strcmp(pName, "vkQueueSubmit") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkQueueSubmit) {
                real_vkQueueSubmit = (PFN_vkQueueSubmit)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkQueueSubmit;
    }
    if (strcmp(pName, "vkQueueSubmit2") == 0 || strcmp(pName, "vkQueueSubmit2KHR") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkQueueSubmit2) {
                real_vkQueueSubmit2 = (PFN_vkQueueSubmit2_Compat)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkQueueSubmit2;
    }
    if (strcmp(pName, "vkQueuePresentKHR") == 0) {
        if (real_vkGetDeviceProcAddr && device) {
            PFN_vkVoidFunction down = real_vkGetDeviceProcAddr(device, pName);
            if (down && down != (PFN_vkVoidFunction)hook_vkQueuePresentKHR) {
                real_vkQueuePresentKHR = (PFN_vkQueuePresentKHR)down;
            }
        }
        return (PFN_vkVoidFunction)hook_vkQueuePresentKHR;
    }
    return real_vkGetDeviceProcAddr ? real_vkGetDeviceProcAddr(device, pName) : NULL;
}

__attribute__((visibility("default")))
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char *pName) {
    if (!pName) return NULL;
    if (strcmp(pName, "vkGetInstanceProcAddr") == 0) return (PFN_vkVoidFunction)vkGetInstanceProcAddr;
    if (strcmp(pName, "vkGetDeviceProcAddr") == 0) return (PFN_vkVoidFunction)vkGetDeviceProcAddr;
    return real_vkGetInstanceProcAddr ? real_vkGetInstanceProcAddr(instance, pName) : NULL;
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceLayerProperties(
    uint32_t *pPropertyCount,
    VkLayerProperties *pProperties
) {
    if (!pPropertyCount) return VK_ERROR_INITIALIZATION_FAILED;
    if (!pProperties) {
        *pPropertyCount = 1;
        return VK_SUCCESS;
    }
    if (*pPropertyCount < 1) return VK_INCOMPLETE;
    memset(&pProperties[0], 0, sizeof(VkLayerProperties));
    strncpy(pProperties[0].layerName, "VK_LAYER_MEGINGIARD_INSPECTOR", VK_MAX_EXTENSION_NAME_SIZE - 1);
    pProperties[0].specVersion = VK_API_VERSION_1_1;
    pProperties[0].implementationVersion = 1;
    strncpy(pProperties[0].description, "Megingiard Universal Graphics UI & Shader Inspector", VK_MAX_DESCRIPTION_SIZE - 1);
    *pPropertyCount = 1;
    return VK_SUCCESS;
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceLayerProperties(
    VkPhysicalDevice physicalDevice,
    uint32_t *pPropertyCount,
    VkLayerProperties *pProperties
) {
    (void)physicalDevice;
    return vkEnumerateInstanceLayerProperties(pPropertyCount, pProperties);
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceExtensionProperties(
    const char *pLayerName,
    uint32_t *pPropertyCount,
    VkExtensionProperties *pProperties
) {
    (void)pProperties;
    if (pLayerName && strcmp(pLayerName, "VK_LAYER_MEGINGIARD_INSPECTOR") == 0) {
        if (pPropertyCount) *pPropertyCount = 0;
        return VK_SUCCESS;
    }
    return VK_ERROR_LAYER_NOT_PRESENT;
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceExtensionProperties(
    VkPhysicalDevice physicalDevice,
    const char *pLayerName,
    uint32_t *pPropertyCount,
    VkExtensionProperties *pProperties
) {
    (void)physicalDevice;
    return vkEnumerateInstanceExtensionProperties(pLayerName, pPropertyCount, pProperties);
}

/* =========================================================================
 * ELF PLT/GOT, .bss Dispatch & Heap Table Patcher via dl_iterate_phdr
 * ========================================================================= */

#define PATCH_MODE_HEAP_VK_TABLE 0
#define PATCH_MODE_APP_DATA      1
#define PATCH_MODE_LIBEGL_DATA   2

typedef struct {
    const char *symbol_name;
    void *hook_fn;
    void **orig_fn_slot;
    void *orig_candidates[MAX_ORIG_CANDIDATES];
    int vk_dispatch_offset;
    int has_vendor_real;
} HookEntry;

static HookEntry g_hooks[] = {
    { "eglSwapBuffers", (void *)hook_eglSwapBuffers, (void **)&real_eglSwapBuffers, {NULL}, -1, 0 },
    { "eglSwapBuffersWithDamageKHR", (void *)hook_eglSwapBuffersWithDamageKHR, (void **)&real_eglSwapBuffersWithDamageKHR, {NULL}, -1, 0 },
    { "eglGetProcAddress", (void *)hook_eglGetProcAddress, (void **)&real_eglGetProcAddress, {NULL}, -1, 0 },
    { "glUseProgram", (void *)hook_glUseProgram, (void **)&real_glUseProgram, {NULL}, -1, 0 },
    { "glBindFramebuffer", (void *)hook_glBindFramebuffer, (void **)&real_glBindFramebuffer, {NULL}, -1, 0 },
    { "glViewport", (void *)hook_glViewport, (void **)&real_glViewport, {NULL}, -1, 0 },
    { "glCreateShader", (void *)hook_glCreateShader, (void **)&real_glCreateShader, {NULL}, -1, 0 },
    { "glShaderSource", (void *)hook_glShaderSource, (void **)&real_glShaderSource, {NULL}, -1, 0 },
    { "glAttachShader", (void *)hook_glAttachShader, (void **)&real_glAttachShader, {NULL}, -1, 0 },
    { "glDrawArrays", (void *)hook_glDrawArrays, (void **)&real_glDrawArrays, {NULL}, -1, 0 },
    { "glDrawElements", (void *)hook_glDrawElements, (void **)&real_glDrawElements, {NULL}, -1, 0 },
    { "glDrawArraysInstanced", (void *)hook_glDrawArraysInstanced, (void **)&real_glDrawArraysInstanced, {NULL}, -1, 0 },
    { "glDrawElementsInstanced", (void *)hook_glDrawElementsInstanced, (void **)&real_glDrawElementsInstanced, {NULL}, -1, 0 },
    { "vkGetInstanceProcAddr", (void *)vkGetInstanceProcAddr, (void **)&real_vkGetInstanceProcAddr, {NULL}, -1, 0 },
    { "vkGetDeviceProcAddr", (void *)vkGetDeviceProcAddr, (void **)&real_vkGetDeviceProcAddr, {NULL}, -1, 0 },
    { "vkCreateShaderModule", (void *)hook_vkCreateShaderModule, (void **)&real_vkCreateShaderModule, {NULL}, -1, 0 },
    { "vkCreateGraphicsPipelines", (void *)hook_vkCreateGraphicsPipelines, (void **)&real_vkCreateGraphicsPipelines, {NULL}, -1, 0 },
    { "vkCreateComputePipelines", (void *)hook_vkCreateComputePipelines, (void **)&real_vkCreateComputePipelines, {NULL}, -1, 0 },
    { "vkCmdBeginRenderPass", (void *)hook_vkCmdBeginRenderPass, (void **)&real_vkCmdBeginRenderPass, {NULL}, -1, 0 },
    { "vkCmdEndRenderPass", (void *)hook_vkCmdEndRenderPass, (void **)&real_vkCmdEndRenderPass, {NULL}, -1, 0 },
    { "vkCmdBeginRenderPass2", (void *)hook_vkCmdBeginRenderPass2, (void **)&real_vkCmdBeginRenderPass2, {NULL}, -1, 0 },
    { "vkCmdBeginRenderPass2KHR", (void *)hook_vkCmdBeginRenderPass2, (void **)&real_vkCmdBeginRenderPass2, {NULL}, -1, 0 },
    { "vkCmdEndRenderPass2", (void *)hook_vkCmdEndRenderPass2, (void **)&real_vkCmdEndRenderPass2, {NULL}, -1, 0 },
    { "vkCmdEndRenderPass2KHR", (void *)hook_vkCmdEndRenderPass2, (void **)&real_vkCmdEndRenderPass2, {NULL}, -1, 0 },
    { "vkCmdBeginRendering", (void *)hook_vkCmdBeginRendering, (void **)&real_vkCmdBeginRendering, {NULL}, -1, 0 },
    { "vkCmdBeginRenderingKHR", (void *)hook_vkCmdBeginRenderingKHR, (void **)&real_vkCmdBeginRenderingKHR, {NULL}, -1, 0 },
    { "vkCmdEndRendering", (void *)hook_vkCmdEndRendering, (void **)&real_vkCmdEndRendering, {NULL}, -1, 0 },
    { "vkCmdEndRenderingKHR", (void *)hook_vkCmdEndRenderingKHR, (void **)&real_vkCmdEndRenderingKHR, {NULL}, -1, 0 },
    { "vkCmdBindPipeline", (void *)hook_vkCmdBindPipeline, (void **)&real_vkCmdBindPipeline, {NULL}, -1, 0 },
    { "vkCmdDraw", (void *)hook_vkCmdDraw, (void **)&real_vkCmdDraw, {NULL}, -1, 0 },
    { "vkCmdDrawIndexed", (void *)hook_vkCmdDrawIndexed, (void **)&real_vkCmdDrawIndexed, {NULL}, -1, 0 },
    { "vkCmdDrawIndirect", (void *)hook_vkCmdDrawIndirect, (void **)&real_vkCmdDrawIndirect, {NULL}, -1, 0 },
    { "vkCmdDrawIndexedIndirect", (void *)hook_vkCmdDrawIndexedIndirect, (void **)&real_vkCmdDrawIndexedIndirect, {NULL}, -1, 0 },
    { "vkCmdDispatch", (void *)hook_vkCmdDispatch, (void **)&real_vkCmdDispatch, {NULL}, -1, 0 },
    { "vkQueueSubmit", (void *)hook_vkQueueSubmit, (void **)&real_vkQueueSubmit, {NULL}, -1, 0 },
    { "vkQueueSubmit2", (void *)hook_vkQueueSubmit2, (void **)&real_vkQueueSubmit2, {NULL}, -1, 0 },
    { "vkQueueSubmit2KHR", (void *)hook_vkQueueSubmit2, (void **)&real_vkQueueSubmit2, {NULL}, -1, 0 },
    { "vkQueuePresentKHR", (void *)hook_vkQueuePresentKHR, (void **)&real_vkQueuePresentKHR, {NULL}, -1, 0 },
};

static void add_hook_candidate(HookEntry *entry, void *fn_ptr, int is_vendor_real) {
    if (!entry || !fn_ptr || fn_ptr == entry->hook_fn) return;
    if (is_vendor_real) {
        *(entry->orig_fn_slot) = fn_ptr;
        entry->has_vendor_real = 1;
    } else if (*(entry->orig_fn_slot) == NULL) {
        *(entry->orig_fn_slot) = fn_ptr;
    }
    for (int i = 0; i < MAX_ORIG_CANDIDATES; i++) {
        if (entry->orig_candidates[i] == fn_ptr) return;
        if (entry->orig_candidates[i] == NULL) {
            entry->orig_candidates[i] = fn_ptr;
            return;
        }
    }
}

/*
 * Decodes the byte offset inside `DeviceDispatchTable` (`*(void **)handle`)
 * from an exported `libvulkan.so` trampoline:
 *   ldr xR, [x0]          -> (insn & 0xffffffe0) == 0xf9400000
 *   ldr xTarget, [xR, #N] -> (insn & 0xffc003e0) == (0xf9400000 | (R << 5))
 */
static int decode_vk_dispatch_offset(const void *fn_ptr) {
    if (!fn_ptr) return -1;
    const uint32_t *insns = (const uint32_t *)fn_ptr;
    int reg_table = -1;
    for (int i = 0; i < 12; i++) {
        uint32_t insn = insns[i];
        if ((insn & 0xffffffe0U) == 0xf9400000U) {
            reg_table = (int)(insn & 0x1fU);
        } else if (reg_table > 0 && (insn & 0xffc003e0U) == (0xf9400000U | ((uint32_t)reg_table << 5))) {
            uint32_t off = ((insn >> 10) & 0xfffU) * 8U;
            if (off > 0) return (int)off;
        }
    }
    return -1;
}

static size_t compute_dynsym_count(const uint32_t *sysv_hash, const uint32_t *gnu_hash) {
    if (sysv_hash != NULL) {
        return (size_t)sysv_hash[1];
    }
    if (gnu_hash != NULL) {
        uint32_t nbuckets = gnu_hash[0];
        uint32_t symoffset = gnu_hash[1];
        uint32_t bloom_size = gnu_hash[2];
        const uint64_t *bloom = (const uint64_t *)(gnu_hash + 4);
        const uint32_t *buckets = (const uint32_t *)(bloom + bloom_size);
        const uint32_t *chains = buckets + nbuckets;
        uint32_t max_sym = 0;
        for (uint32_t b = 0; b < nbuckets; b++) {
            if (buckets[b] > max_sym) max_sym = buckets[b];
        }
        if (max_sym >= symoffset) {
            while ((chains[max_sym - symoffset] & 1U) == 0U) {
                max_sym++;
            }
            return (size_t)(max_sym + 1U);
        }
        return (size_t)symoffset;
    }
    return 0;
}

static int matches_driver_symbol(const char *sym_name, const char *hook_name) {
    if (strcmp(sym_name, hook_name) == 0) return 1;
    if (strncmp(hook_name, "vk", 2) == 0) {
        char qgl_prefix[96];
        snprintf(qgl_prefix, sizeof(qgl_prefix), "_ZN11qglinternal%zu%sE", strlen(hook_name), hook_name);
        if (strncmp(sym_name, qgl_prefix, strlen(qgl_prefix)) == 0) return 1;

        char tu_name[96];
        snprintf(tu_name, sizeof(tu_name), "tu_%s", hook_name + 2);
        if (strcmp(sym_name, tu_name) == 0) return 1;
    }
    return 0;
}

static int is_vendor_gles_driver_module(const char *name) {
    if (!name || name[0] == '\0') return 0;
    if (strstr(name, "libGLESv1") != NULL ||
        strstr(name, "libEGL") != NULL ||
        strstr(name, "VkLayer") != NULL ||
        strstr(name, "q3dtools") != NULL ||
        strstr(name, "qprofiler") != NULL ||
        strstr(name, "SubDriver") != NULL) {
        return 0;
    }
    if (strstr(name, "libGLESv2_") != NULL ||
        strstr(name, "libGLESv3_") != NULL ||
        strstr(name, "libGLES_mali") != NULL ||
        (strstr(name, "/vendor/") != NULL && strstr(name, "libGLES") != NULL)) {
        return 1;
    }
    return 0;
}

static int is_vendor_vk_driver_module(const char *name) {
    if (!name || name[0] == '\0') return 0;
    if (strstr(name, "libGLES") != NULL ||
        strstr(name, "libEGL") != NULL ||
        strstr(name, "VkLayer") != NULL ||
        strstr(name, "q3dtools") != NULL ||
        strstr(name, "qprofiler") != NULL ||
        strstr(name, "SubDriver") != NULL) {
        return 0;
    }
    if (strstr(name, "vulkan.") != NULL ||
        strstr(name, "libvulkan_") != NULL ||
        strstr(name, "freedreno") != NULL ||
        strstr(name, "turnip") != NULL) {
        return 1;
    }
    return 0;
}

static void harvest_module_symbols(
    uintptr_t load_bias,
    const Elf64_Sym *symtab,
    size_t sym_count,
    const char *strtab,
    int is_vendor_gles,
    int is_vendor_vk,
    int is_libvulkan_loader
) {
    size_t num_hooks = sizeof(g_hooks) / sizeof(g_hooks[0]);
    for (size_t i = 1; i < sym_count; i++) {
        if (symtab[i].st_shndx == SHN_UNDEF || symtab[i].st_value == 0) continue;
        uint8_t st_type = ELF64_ST_TYPE(symtab[i].st_info);
        if (st_type != STT_FUNC && st_type != STT_GNU_IFUNC) continue;
        const char *sym_name = strtab + symtab[i].st_name;
        if (!sym_name || sym_name[0] == '\0') continue;
        void *fn_addr = (void *)(load_bias + symtab[i].st_value);

        for (size_t h = 0; h < num_hooks; h++) {
            /* Never harvest egl* from vendor modules; eglSwapBuffers must stay libEGL.so's wrapper */
            if (strncmp(g_hooks[h].symbol_name, "egl", 3) == 0) continue;
            if (strncmp(g_hooks[h].symbol_name, "gl", 2) == 0) {
                if (is_vendor_vk || is_libvulkan_loader) continue;
                if (matches_driver_symbol(sym_name, g_hooks[h].symbol_name)) {
                    add_hook_candidate(&g_hooks[h], fn_addr, is_vendor_gles);
                }
            } else if (strncmp(g_hooks[h].symbol_name, "vk", 2) == 0) {
                if (!is_vendor_vk && !is_libvulkan_loader) continue;
                if (matches_driver_symbol(sym_name, g_hooks[h].symbol_name)) {
                    add_hook_candidate(&g_hooks[h], fn_addr, is_vendor_vk);
                    if (is_libvulkan_loader && strcmp(sym_name, g_hooks[h].symbol_name) == 0) {
                        int off = decode_vk_dispatch_offset(fn_addr);
                        if (off > 0) {
                            g_hooks[h].vk_dispatch_offset = off;
                        }
                    }
                }
            }
        }

        if (is_vendor_gles && strcmp(sym_name, "glGetIntegerv") == 0) {
            real_glGetIntegerv = (PFN_glGetIntegerv)fn_addr;
        }
    }
}

static void resolve_default_symbols(void) {
    void *libegl = dlopen("libEGL.so", RTLD_NOW | RTLD_NOLOAD);
    if (!libegl) libegl = dlopen("libEGL.so", RTLD_LAZY);
    if (libegl) {
        add_hook_candidate(&g_hooks[0], dlsym(libegl, "eglSwapBuffers"), 0);
        add_hook_candidate(&g_hooks[1], dlsym(libegl, "eglSwapBuffersWithDamageKHR"), 0);
        add_hook_candidate(&g_hooks[2], dlsym(libegl, "eglGetProcAddress"), 0);
    }

    void *libgles = dlopen("libGLESv2.so", RTLD_NOW | RTLD_NOLOAD);
    if (!libgles) libgles = dlopen("libGLESv3.so", RTLD_NOW | RTLD_NOLOAD);
    if (!libgles) libgles = dlopen("libGLESv2.so", RTLD_LAZY);
    if (libgles) {
        size_t num_hooks = sizeof(g_hooks) / sizeof(g_hooks[0]);
        for (size_t h = 0; h < num_hooks; h++) {
            if (strncmp(g_hooks[h].symbol_name, "gl", 2) == 0) {
                add_hook_candidate(&g_hooks[h], dlsym(libgles, g_hooks[h].symbol_name), 0);
            }
        }
        if (!real_glGetIntegerv) real_glGetIntegerv = (PFN_glGetIntegerv)dlsym(libgles, "glGetIntegerv");
    }

    void *libvk = dlopen("libvulkan.so", RTLD_NOW | RTLD_NOLOAD);
    if (!libvk) libvk = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_NOLOAD);
    if (libvk) {
        size_t num_hooks = sizeof(g_hooks) / sizeof(g_hooks[0]);
        for (size_t h = 0; h < num_hooks; h++) {
            if (strncmp(g_hooks[h].symbol_name, "vk", 2) == 0) {
                void *fn = dlsym(libvk, g_hooks[h].symbol_name);
                if (fn) {
                    add_hook_candidate(&g_hooks[h], fn, 0);
                    int off = decode_vk_dispatch_offset(fn);
                    if (off > 0) {
                        g_hooks[h].vk_dispatch_offset = off;
                    }
                }
            }
        }
    }
}

static void patch_relocation_table(
    uintptr_t load_bias,
    const Elf64_Rela *rela,
    size_t rela_count,
    const Elf64_Sym *symtab,
    const char *strtab
) {
    size_t num_hooks = sizeof(g_hooks) / sizeof(g_hooks[0]);
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;

    for (size_t i = 0; i < rela_count; i++) {
        uint32_t sym_idx = ELF64_R_SYM(rela[i].r_info);
        uint32_t rel_type = ELF64_R_TYPE(rela[i].r_info);
        if (sym_idx == 0) continue;
        /* R_AARCH64_JUMP_SLOT = 1026, R_AARCH64_GLOB_DAT = 1025, R_AARCH64_ABS64 = 257 */
        if (rel_type != 1026 && rel_type != 1025 && rel_type != 257) continue;

        const char *sym_name = strtab + symtab[sym_idx].st_name;
        if (!sym_name || sym_name[0] == '\0') continue;

        for (size_t h = 0; h < num_hooks; h++) {
            if (strcmp(sym_name, g_hooks[h].symbol_name) == 0) {
                void **got_slot = (void **)(load_bias + rela[i].r_offset);
                uintptr_t page_start = ((uintptr_t)got_slot) & ~((uintptr_t)page_size - 1);
                if (mprotect((void *)page_start, (size_t)page_size, PROT_READ | PROT_WRITE) == 0) {
                    if (*got_slot != NULL && *got_slot != g_hooks[h].hook_fn) {
                        add_hook_candidate(&g_hooks[h], *got_slot, 0);
                    }
                    *got_slot = g_hooks[h].hook_fn;
                }
                break;
            }
        }
    }
}

static int matches_any_candidate(const HookEntry *entry, void *val) {
    if (!val || val == entry->hook_fn) return 0;
    if (*(entry->orig_fn_slot) == val) return 1;
    for (int i = 0; i < MAX_ORIG_CANDIDATES; i++) {
        if (entry->orig_candidates[i] == val) return 1;
    }
    return 0;
}

static void inspect_and_patch_memory_range(uintptr_t range_start, size_t range_len, int patch_mode) {
    size_t num_hooks = sizeof(g_hooks) / sizeof(g_hooks[0]);

    /* Find hook entries for vkCmdDraw and vkCmdDrawIndexed to detect DeviceDispatchTable instances */
    HookEntry *draw_entry = NULL;
    HookEntry *draw_idx_entry = NULL;
    for (size_t h = 0; h < num_hooks; h++) {
        if (strcmp(g_hooks[h].symbol_name, "vkCmdDraw") == 0) draw_entry = &g_hooks[h];
        else if (strcmp(g_hooks[h].symbol_name, "vkCmdDrawIndexed") == 0) draw_idx_entry = &g_hooks[h];
    }

    uintptr_t aligned_start = (range_start + 7UL) & ~7UL;
    uintptr_t range_end = range_start + range_len;

    for (uintptr_t addr = aligned_start; addr + sizeof(void *) <= range_end; addr += sizeof(void *)) {
        void **slot = (void **)addr;
        void *val = *slot;
        if (val == NULL) continue;

        /* Check if `addr` is the `vkCmdDraw` slot inside a live Vulkan `DeviceDispatchTable` */
        if (patch_mode != PATCH_MODE_LIBEGL_DATA &&
            draw_entry && draw_idx_entry &&
            draw_entry->vk_dispatch_offset > 0 &&
            draw_idx_entry->vk_dispatch_offset > 0 &&
            matches_any_candidate(draw_entry, val)) {
            uintptr_t table_base = addr - (uintptr_t)draw_entry->vk_dispatch_offset;
            uintptr_t idx_slot_addr = table_base + (uintptr_t)draw_idx_entry->vk_dispatch_offset;
            if (table_base >= range_start && idx_slot_addr + sizeof(void *) <= range_end) {
                void *idx_val = *(void **)idx_slot_addr;
                if (matches_any_candidate(draw_idx_entry, idx_val) || idx_val == draw_idx_entry->hook_fn) {
                    g_seen_vk = 1;
                    /* Extract and patch all DeviceDispatchTable slots (including internal QueuePresentKHR) */
                    for (size_t h = 0; h < num_hooks; h++) {
                        if (g_hooks[h].vk_dispatch_offset <= 0) continue;
                        uintptr_t s_addr = table_base + (uintptr_t)g_hooks[h].vk_dispatch_offset;
                        if (s_addr < range_start || s_addr + sizeof(void *) > range_end) continue;
                        void **t_slot = (void **)s_addr;
                        void *t_val = *t_slot;
                        if (t_val != NULL && t_val != g_hooks[h].hook_fn) {
                            add_hook_candidate(&g_hooks[h], t_val, 1);
                            *t_slot = g_hooks[h].hook_fn;
                        }
                    }
                }
            }
        }

        if (patch_mode == PATCH_MODE_HEAP_VK_TABLE) {
            /* Never patch lone pointers on the heap outside a verified DeviceDispatchTable */
            continue;
        }

        for (size_t h = 0; h < num_hooks; h++) {
            if (patch_mode == PATCH_MODE_LIBEGL_DATA) {
                /* Inside libEGL.so's .bss (gEGLImpl), only patch GLESv2/v3 vendor pointers */
                if (strncmp(g_hooks[h].symbol_name, "gl", 2) != 0) continue;
                if (!g_hooks[h].has_vendor_real) continue;
                if (val == *(g_hooks[h].orig_fn_slot) && val != g_hooks[h].hook_fn) {
                    *slot = g_hooks[h].hook_fn;
                    break;
                }
            } else if (patch_mode == PATCH_MODE_APP_DATA) {
                if (matches_any_candidate(&g_hooks[h], val)) {
                    *slot = g_hooks[h].hook_fn;
                    break;
                }
            }
        }
    }
}

static int phdr_harvest_cb(struct dl_phdr_info *info, size_t size, void *data) {
    (void)size;
    (void)data;
    const char *name = info->dlpi_name ? info->dlpi_name : "";
    if (strstr(name, "libVkLayer_MegingiardInspector.so") ||
        strstr(name, "libGLESv1") ||
        strstr(name, "/linker64") ||
        strstr(name, "/libc.so") ||
        strstr(name, "/libdl.so") ||
        strstr(name, "/libm.so")) {
        return 0;
    }

    int is_vk_loader = (strstr(name, "libvulkan.so") != NULL);
    int is_vendor_gles = is_vendor_gles_driver_module(name);
    int is_vendor_vk = is_vendor_vk_driver_module(name);
    int is_sys_gles = (strstr(name, "libGLESv2.so") != NULL || strstr(name, "libGLESv3.so") != NULL);
    if (!is_vendor_gles && !is_vendor_vk && !is_vk_loader && !is_sys_gles) {
        return 0;
    }

    uintptr_t load_bias = (uintptr_t)info->dlpi_addr;
    const Elf64_Dyn *dyn = NULL;
    for (int i = 0; i < info->dlpi_phnum; i++) {
        if (info->dlpi_phdr[i].p_type == PT_DYNAMIC) {
            dyn = (const Elf64_Dyn *)(load_bias + info->dlpi_phdr[i].p_vaddr);
            break;
        }
    }
    if (!dyn) return 0;

    const Elf64_Sym *symtab = NULL;
    const char *strtab = NULL;
    const uint32_t *sysv_hash = NULL;
    const uint32_t *gnu_hash = NULL;

    for (; dyn->d_tag != DT_NULL; dyn++) {
        uintptr_t ptr = dyn->d_un.d_ptr > load_bias ? dyn->d_un.d_ptr : (load_bias + dyn->d_un.d_ptr);
        if (dyn->d_tag == DT_SYMTAB) symtab = (const Elf64_Sym *)ptr;
        else if (dyn->d_tag == DT_STRTAB) strtab = (const char *)ptr;
        else if (dyn->d_tag == DT_HASH) sysv_hash = (const uint32_t *)ptr;
        else if (dyn->d_tag == DT_GNU_HASH) gnu_hash = (const uint32_t *)ptr;
    }

    size_t sym_count = compute_dynsym_count(sysv_hash, gnu_hash);
    if (symtab && strtab && sym_count > 0 && sym_count < 65536) {
        harvest_module_symbols(load_bias, symtab, sym_count, strtab, is_vendor_gles, is_vendor_vk, is_vk_loader);
    }
    return 0;
}

static int phdr_patch_cb(struct dl_phdr_info *info, size_t size, void *data) {
    (void)size;
    (void)data;
    const char *name = info->dlpi_name ? info->dlpi_name : "";

    if (strstr(name, "libVkLayer_MegingiardInspector.so") ||
        strncmp(name, "/vendor/", 8) == 0 ||
        strstr(name, "/linker64") ||
        strstr(name, "/libc.so") ||
        strstr(name, "/libdl.so") ||
        strstr(name, "/libm.so") ||
        strstr(name, "libGLESv2.so") ||
        strstr(name, "libGLESv3.so") ||
        strstr(name, "libGLESv1") ||
        is_vendor_gles_driver_module(name) ||
        is_vendor_vk_driver_module(name)) {
        return 0;
    }

    uintptr_t load_bias = (uintptr_t)info->dlpi_addr;
    const Elf64_Dyn *dyn = NULL;
    uintptr_t relro_start = 0;
    uintptr_t relro_end = 0;
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;

    for (int i = 0; i < info->dlpi_phnum; i++) {
        if (info->dlpi_phdr[i].p_type == PT_DYNAMIC) {
            dyn = (const Elf64_Dyn *)(load_bias + info->dlpi_phdr[i].p_vaddr);
        } else if (info->dlpi_phdr[i].p_type == 0x6474e552U /* PT_GNU_RELRO */) {
            relro_start = (load_bias + info->dlpi_phdr[i].p_vaddr) & ~((uintptr_t)page_size - 1);
            relro_end = (load_bias + info->dlpi_phdr[i].p_vaddr + info->dlpi_phdr[i].p_memsz + (uintptr_t)page_size - 1) &
                        ~((uintptr_t)page_size - 1);
        }
    }
    if (!dyn) return 0;

    const Elf64_Sym *symtab = NULL;
    const char *strtab = NULL;
    const Elf64_Rela *jmprel = NULL;
    size_t jmprel_sz = 0;
    const Elf64_Rela *rela = NULL;
    size_t rela_sz = 0;

    for (; dyn->d_tag != DT_NULL; dyn++) {
        switch (dyn->d_tag) {
            case DT_SYMTAB:
                symtab = (const Elf64_Sym *)(dyn->d_un.d_ptr > load_bias ? dyn->d_un.d_ptr : load_bias + dyn->d_un.d_ptr);
                break;
            case DT_STRTAB:
                strtab = (const char *)(dyn->d_un.d_ptr > load_bias ? dyn->d_un.d_ptr : load_bias + dyn->d_un.d_ptr);
                break;
            case DT_JMPREL:
                jmprel = (const Elf64_Rela *)(dyn->d_un.d_ptr > load_bias ? dyn->d_un.d_ptr : load_bias + dyn->d_un.d_ptr);
                break;
            case DT_PLTRELSZ:
                jmprel_sz = (size_t)dyn->d_un.d_val;
                break;
            case DT_RELA:
                rela = (const Elf64_Rela *)(dyn->d_un.d_ptr > load_bias ? dyn->d_un.d_ptr : load_bias + dyn->d_un.d_ptr);
                break;
            case DT_RELASZ:
                rela_sz = (size_t)dyn->d_un.d_val;
                break;
            default:
                break;
        }
    }

    int is_libegl = (strstr(name, "libEGL.so") != NULL);
    if (symtab && strtab && !is_libegl && strstr(name, "libvulkan.so") == NULL) {
        if (jmprel && jmprel_sz > 0) {
            patch_relocation_table(load_bias, jmprel, jmprel_sz / sizeof(Elf64_Rela), symtab, strtab);
        }
        if (rela && rela_sz > 0) {
            patch_relocation_table(load_bias, rela, rela_sz / sizeof(Elf64_Rela), symtab, strtab);
        }
    }

    /* Patch cached function pointer tables in writable non-RELRO .data/.bss */
    if (strncmp(name, "/data/", 6) == 0 || is_libegl) {
        int mode = is_libegl ? PATCH_MODE_LIBEGL_DATA : PATCH_MODE_APP_DATA;
        for (int i = 0; i < info->dlpi_phnum; i++) {
            const Elf64_Phdr *ph = &info->dlpi_phdr[i];
            if (ph->p_type == PT_LOAD &&
                (ph->p_flags & (PF_R | PF_W)) == (PF_R | PF_W) &&
                !(ph->p_flags & PF_X) &&
                ph->p_memsz > 0 &&
                ph->p_memsz <= (32 * 1024 * 1024)) {
                uintptr_t seg_start = load_bias + ph->p_vaddr;
                uintptr_t seg_end = seg_start + (size_t)ph->p_memsz;
                if (relro_end > seg_start && relro_start <= seg_start) {
                    seg_start = relro_end;
                }
                if (seg_end > seg_start) {
                    inspect_and_patch_memory_range(seg_start, seg_end - seg_start, mode);
                }
            }
        }
    }

    return 0;
}

static void scan_and_patch_heap_dispatch_tables(void) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return;

    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;

    uintptr_t self_addr = (uintptr_t)&g_hooks;
    char line[512];
    size_t total_scanned = 0;
    const size_t max_heap_scan = 96 * 1024 * 1024;

    while (fgets(line, sizeof(line), fp)) {
        if (total_scanned >= max_heap_scan) break;
        uintptr_t start = 0, end = 0;
        char perms[8] = {0};
        if (sscanf(line, "%lx-%lx %7s", &start, &end, perms) != 3) continue;
        if (strcmp(perms, "rw-p") != 0) continue;
        if (self_addr >= start && self_addr < end) continue;
        if (end <= start || (end - start) > (32 * 1024 * 1024)) continue;

        int is_native_heap = (strstr(line, "[heap]") != NULL ||
                              strstr(line, "[anon:scudo:") != NULL ||
                              strstr(line, "[anon:libc_malloc]") != NULL ||
                              strstr(line, "[anon:GWP-ASan") != NULL);
        if (!is_native_heap) continue;

        for (uintptr_t page = start; page + (uintptr_t)page_size <= end; page += (uintptr_t)page_size) {
            unsigned char vec = 0;
            if (mincore((void *)page, (size_t)page_size, &vec) == 0 && (vec & 1U) != 0U) {
                inspect_and_patch_memory_range(page, (size_t)page_size, PATCH_MODE_HEAP_VK_TABLE);
                total_scanned += (size_t)page_size;
                if (total_scanned >= max_heap_scan) break;
            }
        }
    }
    fclose(fp);
}

static void scan_and_patch_loaded_modules(void) {
    resolve_default_symbols();
    dl_iterate_phdr(phdr_harvest_cb, NULL);
    /* Run heap scan first so DeviceDispatchTable yields internal pointers (e.g. QueuePresentKHR),
     * then patch ELF modules and re-scan heap so all copies of extracted pointers are hooked. */
    scan_and_patch_heap_dispatch_tables();
    dl_iterate_phdr(phdr_patch_cb, NULL);
    scan_and_patch_heap_dispatch_tables();
}

/* =========================================================================
 * Control Server: Abstract Unix Socket (\0megingiard_gfx_<pid>) + TCP 51255
 * ========================================================================= */

static void handle_client_command(int client_fd, const char *cmd) {
    if (strcmp(cmd, "PING") == 0) {
        const char *pong = "PONG\n";
        (void)write(client_fd, pong, strlen(pong));
        return;
    }

    if (strcmp(cmd, "LIST") == 0) {
        static int g_rescanned_on_list = 0;
        if (g_node_count == 0 && !g_rescanned_on_list) {
            g_rescanned_on_list = 1;
            scan_and_patch_loaded_modules();
        }
        pthread_mutex_lock(&g_mutex);
        const char *api_hdr = "API|UNKNOWN\n";
        if (g_seen_vk && g_seen_gles) api_hdr = "API|HYBRID\n";
        else if (g_seen_vk) api_hdr = "API|VULKAN\n";
        else if (g_seen_gles) api_hdr = "API|OPENGL_ES\n";
        (void)write(client_fd, api_hdr, strlen(api_hdr));

        char line[384];
        for (int i = 0; i < g_node_count; i++) {
            if (!g_nodes[i].in_use) continue;
            int draws = g_nodes[i].draw_calls_last_frame > 0
                ? g_nodes[i].draw_calls_last_frame
                : g_nodes[i].draw_calls_accum;
            int n = snprintf(
                line,
                sizeof(line),
                "NODE|%s|%s|%s|%08X|%dx%d|%d|%d|%s\n",
                g_nodes[i].id,
                category_to_str(g_nodes[i].category),
                api_to_str(g_nodes[i].api),
                g_nodes[i].hash,
                g_nodes[i].width,
                g_nodes[i].height,
                draws,
                g_nodes[i].enabled ? 1 : 0,
                g_nodes[i].name
            );
            if (n > 0) {
                (void)write(client_fd, line, (size_t)n);
            }
        }
        pthread_mutex_unlock(&g_mutex);
        (void)write(client_fd, "END\n", 4);
        return;
    }

    if (strncmp(cmd, "SET_ALL ", 8) == 0) {
        int val = atoi(cmd + 8) ? 1 : 0;
        pthread_mutex_lock(&g_mutex);
        for (int i = 0; i < g_node_count; i++) {
            if (!g_nodes[i].in_use) continue;
            g_nodes[i].enabled = val;
            if (val == 1) {
                g_nodes[i].split_enabled = 0;
                if (g_nodes[i].category == CAT_SUB_DRAW) {
                    g_nodes[i].in_use = 0;
                }
            }
        }
        pthread_mutex_unlock(&g_mutex);
        (void)write(client_fd, "OK\n", 3);
        return;
    }

    if (strcmp(cmd, "INVERT") == 0) {
        pthread_mutex_lock(&g_mutex);
        for (int i = 0; i < g_node_count; i++) {
            if (g_nodes[i].in_use) g_nodes[i].enabled = g_nodes[i].enabled ? 0 : 1;
        }
        pthread_mutex_unlock(&g_mutex);
        (void)write(client_fd, "OK\n", 3);
        return;
    }

    if (strncmp(cmd, "SPLIT ", 6) == 0) {
        char target_id[64] = {0};
        int val = 0;
        if (sscanf(cmd + 6, "%63s %d", target_id, &val) == 2) {
            int found = 0;
            pthread_mutex_lock(&g_mutex);
            for (int i = 0; i < g_node_count; i++) {
                if (g_nodes[i].in_use && strcmp(g_nodes[i].id, target_id) == 0) {
                    g_nodes[i].split_enabled = val ? 1 : 0;
                    if (!val) {
                        uint64_t parent_key = g_nodes[i].handle_key;
                        NodeApi parent_api = g_nodes[i].api;
                        for (int j = 0; j < g_node_count; j++) {
                            if (g_nodes[j].in_use &&
                                g_nodes[j].category == CAT_SUB_DRAW &&
                                g_nodes[j].api == parent_api &&
                                g_nodes[j].parent_pipe_key == parent_key) {
                                g_nodes[j].enabled = 1;
                                g_nodes[j].in_use = 0;
                            }
                        }
                    }
                    found = 1;
                    break;
                }
            }
            pthread_mutex_unlock(&g_mutex);
            if (found) {
                (void)write(client_fd, "OK\n", 3);
            } else {
                (void)write(client_fd, "ERR NOT_FOUND\n", 14);
            }
            return;
        }
    }

    if (strncmp(cmd, "SET ", 4) == 0) {
        char target_id[64] = {0};
        int val = 1;
        if (sscanf(cmd + 4, "%63s %d", target_id, &val) == 2) {
            int found = 0;
            pthread_mutex_lock(&g_mutex);
            for (int i = 0; i < g_node_count; i++) {
                if (g_nodes[i].in_use && strcmp(g_nodes[i].id, target_id) == 0) {
                    g_nodes[i].enabled = val ? 1 : 0;
                    found = 1;
                    break;
                }
            }
            pthread_mutex_unlock(&g_mutex);
            if (found) {
                (void)write(client_fd, "OK\n", 3);
            } else {
                (void)write(client_fd, "ERR NOT_FOUND\n", 14);
            }
            return;
        }
    }

    (void)write(client_fd, "ERR UNKNOWN\n", 12);
}

static void serve_accept_loop(int srv) {
    while (1) {
        int client = accept(srv, NULL, NULL);
        if (client < 0) continue;

        char buf[256];
        ssize_t n = read(client, buf, sizeof(buf) - 1);
        if (n > 0) {
            buf[n] = '\0';
            while (n > 0 && (buf[n - 1] == '\n' || buf[n - 1] == '\r')) {
                buf[--n] = '\0';
            }
            handle_client_command(client, buf);
        }
        close(client);
    }
}

static void *inspector_tcp_server_thread(void *arg) {
    (void)arg;
    int srv = socket(AF_INET, SOCK_STREAM, 0);
    if (srv < 0) return NULL;

    int opt = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(INSPECTOR_PORT);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

    if (bind(srv, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        close(srv);
        return NULL;
    }
    if (listen(srv, 4) < 0) {
        close(srv);
        return NULL;
    }

    serve_accept_loop(srv);
    return NULL;
}

static void *inspector_unix_server_thread(void *arg) {
    (void)arg;
    scan_and_patch_loaded_modules();

    int srv = socket(AF_UNIX, SOCK_STREAM, 0);
    if (srv < 0) return NULL;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    int name_len = snprintf(addr.sun_path + 1, sizeof(addr.sun_path) - 2, "megingiard_gfx_%d", (int)getpid());
    socklen_t sock_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + (size_t)name_len);

    if (bind(srv, (struct sockaddr *)&addr, sock_len) < 0) {
        close(srv);
        return NULL;
    }
    if (listen(srv, 4) < 0) {
        close(srv);
        return NULL;
    }

    serve_accept_loop(srv);
    return NULL;
}

__attribute__((constructor))
static void megingiard_gfx_layer_init(void) {
    if (__sync_bool_compare_and_swap(&g_server_started, 0, 1)) {
        pthread_t unix_tid;
        if (pthread_create(&unix_tid, NULL, inspector_unix_server_thread, NULL) == 0) {
            pthread_detach(unix_tid);
        }
        pthread_t tcp_tid;
        if (pthread_create(&tcp_tid, NULL, inspector_tcp_server_thread, NULL) == 0) {
            pthread_detach(tcp_tid);
        }
    }
}
