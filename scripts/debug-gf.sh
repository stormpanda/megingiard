#!/usr/bin/env zsh

# Megingiard Game Focus Debug Workflow Automation Script
#
# Commands:
#   build          Compiles the Game Focus debug APK and copies it to app/debug-gf/
#   install        Installs the built Game Focus debug APK on a connected device via ADB
#   build-install  Builds and installs the Game Focus debug APK in a single step
#
set -e

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="$SCRIPT_DIR/.."
GRADLE_FILE="$PROJECT_ROOT/gamefocus/build.gradle.kts"

# Ensure running from project root
cd "$PROJECT_ROOT"

log_info() {
    echo -e "\033[1;34m[INFO]\033[0m $1"
}

log_warn() {
    echo -e "\033[1;33m[WARN]\033[0m $1"
}

log_success() {
    echo -e "\033[1;32m[SUCCESS]\033[0m $1"
}

log_error() {
    echo -e "\033[1;31m[ERROR]\033[0m $1" >&2
}

build_debug_apk() {
    version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
    debug_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

    log_info "Building Game Focus debug APK for version $debug_version..."
    ./gradlew :gamefocus:assembleDebug

    generated_apk="gamefocus/build/outputs/apk/debug/gamefocus-debug.apk"
    if [[ ! -f "$generated_apk" ]]; then
        log_error "Generated Game Focus debug APK not found at $generated_apk"
        exit 1
    fi

    rm -f app/debug-gf/*.apk(N)
    mkdir -p app/debug-gf
    copied_apk="app/debug-gf/Megingiard-GameFocus-v${debug_version}-debug.apk"
    cp "$generated_apk" "$copied_apk"
    log_success "Game Focus debug APK successfully created at $copied_apk"
}

install_debug_apk() {
    strict_mode="${1:-true}"
    version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
    debug_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
    debug_apk="app/debug-gf/Megingiard-GameFocus-v${debug_version}-debug.apk"

    if [[ ! -f "$debug_apk" ]]; then
        debug_apk=$(ls app/debug-gf/*.apk 2>/dev/null | head -n 1 || true)
    fi

    if [[ -z "$debug_apk" || ! -f "$debug_apk" ]]; then
        log_error "Game Focus debug APK not found in app/debug-gf/. Please run 'scripts/debug-gf.sh build' first."
        exit 1
    fi

    ADB="${ADB:-$(command -v adb 2>/dev/null || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
    DEVICE="${DEVICE:-}"

    if command -v "$ADB" >/dev/null 2>&1 || [[ -x "$ADB" ]]; then
        if [[ -z "$DEVICE" ]]; then
            local raw_devices=(${(f)"$("$ADB" devices 2>/dev/null | grep -v "List of devices" | grep -E '[[:space:]]device$' | awk '{print $1}')"})
            local devices=(${raw_devices:#})
            if (( ${#devices} == 0 )); then
                if [[ "$strict_mode" == "true" ]]; then
                    log_error "No connected Thor/Android device found via ADB."
                    exit 1
                else
                    log_info "No connected Thor/Android device found via ADB. Skipping device install."
                    return 0
                fi
            elif (( ${#devices} == 1 )); then
                DEVICE="${devices[1]}"
                log_info "Thor/Android device detected via ADB ($DEVICE)."
            else
                DEVICE="${devices[1]}"
                log_warn "Multiple ADB devices detected (${(j:, :)devices}). Using first device: $DEVICE"
            fi
        else
            log_info "Using explicitly targeted ADB device ($DEVICE)."
        fi

        ADB_CMD=("$ADB" "-s" "$DEVICE")
        log_info "Installing Game Focus debug APK on device ($debug_apk)..."
        "${ADB_CMD[@]}" install -r "$debug_apk"
        log_success "Successfully installed Game Focus debug APK on device."
    else
        if [[ "$strict_mode" == "true" ]]; then
            log_error "ADB executable not found at '$ADB'."
            exit 1
        else
            log_info "ADB executable not found at '$ADB'. Skipping device install."
        fi
    fi
}

case "$1" in
    build)
        build_debug_apk
        ;;
    install)
        install_debug_apk true
        ;;
    build-install|build_and_install|all)
        build_debug_apk
        install_debug_apk true
        ;;
    *)
        echo "Usage: $0 {build|install|build-install}"
        exit 1
        ;;
esac
