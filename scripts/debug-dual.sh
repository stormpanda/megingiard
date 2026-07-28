#!/usr/bin/env zsh

# Megingiard Dual App (Companion + Game Focus) Debug Workflow Automation Script
#
# Commands:
#   build          Compiles Megingiard (app/debug/) & Game Focus (app/debug-gf/) debug APKs
#   install        Installs both built debug APKs on a connected device via ADB
#   build-install  Builds and installs both debug APKs in a single step
#
set -e

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="$SCRIPT_DIR/.."
APP_GRADLE_FILE="$PROJECT_ROOT/app/build.gradle.kts"
GF_GRADLE_FILE="$PROJECT_ROOT/gamefocus/build.gradle.kts"

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

build_debug_apks() {
    app_version_line=$(grep -E 'versionName[[:space:]]*=' "$APP_GRADLE_FILE")
    app_version=$(echo "$app_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

    gf_version_line=$(grep -E 'versionName[[:space:]]*=' "$GF_GRADLE_FILE")
    gf_version=$(echo "$gf_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

    log_info "Building Megingiard companion ($app_version) & Game Focus ($gf_version) debug APKs..."
    ./gradlew :app:assembleDebug :gamefocus:assembleDebug

    generated_app_apk="app/build/outputs/apk/debug/app-debug.apk"
    if [[ ! -f "$generated_app_apk" ]]; then
        generated_app_apk=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1 || true)
    fi

    generated_gf_apk="gamefocus/build/outputs/apk/debug/gamefocus-debug.apk"
    if [[ ! -f "$generated_gf_apk" ]]; then
        generated_gf_apk=$(ls gamefocus/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1 || true)
    fi

    if [[ ! -f "$generated_app_apk" ]]; then
        log_error "Generated Megingiard companion debug APK not found at app/build/outputs/apk/debug/"
        exit 1
    fi

    if [[ ! -f "$generated_gf_apk" ]]; then
        log_error "Generated Game Focus debug APK not found at gamefocus/build/outputs/apk/debug/"
        exit 1
    fi

    # Clean legacy debug-dual directory if it exists
    rm -rf app/debug-dual 2>/dev/null || true

    rm -f app/debug/*.apk(N) 2>/dev/null || true
    mkdir -p app/debug
    copied_app_apk="app/debug/Megingiard-v${app_version}-debug.apk"
    cp "$generated_app_apk" "$copied_app_apk"
    log_success "Megingiard companion debug APK created at $copied_app_apk"

    rm -f app/debug-gf/*.apk(N) 2>/dev/null || true
    mkdir -p app/debug-gf
    copied_gf_apk="app/debug-gf/Megingiard-GameFocus-v${gf_version}-debug.apk"
    cp "$generated_gf_apk" "$copied_gf_apk"
    log_success "Game Focus launcher debug APK created at $copied_gf_apk"
}

install_debug_apks() {
    strict_mode="${1:-true}"
    app_version_line=$(grep -E 'versionName[[:space:]]*=' "$APP_GRADLE_FILE")
    app_version=$(echo "$app_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
    app_apk="app/debug/Megingiard-v${app_version}-debug.apk"

    if [[ ! -f "$app_apk" ]]; then
        app_apk=$(ls app/debug/*.apk 2>/dev/null | head -n 1 || true)
    fi

    gf_version_line=$(grep -E 'versionName[[:space:]]*=' "$GF_GRADLE_FILE")
    gf_version=$(echo "$gf_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
    gf_apk="app/debug-gf/Megingiard-GameFocus-v${gf_version}-debug.apk"

    if [[ ! -f "$gf_apk" ]]; then
        gf_apk=$(ls app/debug-gf/*.apk 2>/dev/null | head -n 1 || true)
    fi

    if [[ -z "$app_apk" || ! -f "$app_apk" ]]; then
        log_error "Megingiard companion debug APK not found in app/debug/. Please run 'scripts/debug-dual.sh build' first."
        exit 1
    fi

    if [[ -z "$gf_apk" || ! -f "$gf_apk" ]]; then
        log_error "Game Focus launcher debug APK not found in app/debug-gf/. Please run 'scripts/debug-dual.sh build' first."
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
        log_info "Installing Megingiard companion debug APK ($app_apk)..."
        "${ADB_CMD[@]}" install -r "$app_apk"
        log_success "Successfully installed Megingiard companion debug APK."

        log_info "Installing Game Focus launcher debug APK ($gf_apk)..."
        "${ADB_CMD[@]}" install -r "$gf_apk"
        log_success "Successfully installed Game Focus launcher debug APK."
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
        build_debug_apks
        ;;
    install)
        install_debug_apks true
        ;;
    build-install|build_and_install|all)
        build_debug_apks
        install_debug_apks true
        ;;
    *)
        echo "Usage: $0 {build|install|build-install}"
        exit 1
        ;;
esac
