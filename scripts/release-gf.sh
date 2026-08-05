#!/usr/bin/env zsh

# Megingiard Game Focus Release Workflow Automation Script
#
# Commands:
#   build          Compiles the Game Focus release APK, signs it, and copies it to app/release-gf/
#   install        Installs the built Game Focus release APK on a connected device via ADB
#   build-install  Builds and installs the Game Focus release APK in a single step
#
set -e

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="$SCRIPT_DIR/.."
GRADLE_FILE="$PROJECT_ROOT/gamefocus/build.gradle.kts"
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"

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

build_release_apk() {
    # Verify local.properties exists and has keystore details
    if [[ ! -f "$LOCAL_PROPERTIES" ]]; then
        log_error "local.properties not found at $LOCAL_PROPERTIES"
        exit 1
    fi

    if ! grep -q "megingiard.keystore.password" "$LOCAL_PROPERTIES" || \
       ! grep -q "megingiard.keystore.key.password" "$LOCAL_PROPERTIES" || \
       ! grep -q "megingiard.keystore.alias" "$LOCAL_PROPERTIES"; then
        log_error "Keystore credentials missing in local.properties. Ensure megingiard.keystore.password, megingiard.keystore.key.password, and megingiard.keystore.alias are present."
        exit 1
    fi

    version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
    release_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

    log_info "Building Game Focus release APK for version $release_version..."
    ./gradlew :gamefocus:assembleRelease

    generated_apk="gamefocus/build/outputs/apk/release/Megingiard-GameFocus-v${release_version}.apk"
    if [[ ! -f "$generated_apk" ]]; then
        log_error "Generated Game Focus release APK not found at $generated_apk"
        exit 1
    fi

    rm -f app/release-gf/*.apk(N) app/release-gf/*-checksum-*.txt(N)
    mkdir -p app/release-gf
    copied_apk="app/release-gf/Megingiard-GameFocus-v${release_version}.apk"
    cp "$generated_apk" "$copied_apk"
    log_success "Game Focus release APK successfully created at $copied_apk"

    # Generate checksum
    log_info "Generating SHA-256 checksum..."
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$copied_apk" | awk '{ print $1 }' > "${copied_apk%.apk}-checksum-sha256.txt"
    elif command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$copied_apk" | awk '{ print $1 }' > "${copied_apk%.apk}-checksum-sha256.txt"
    else
        log_error "Neither 'shasum' nor 'sha256sum' found — cannot generate checksum"
        exit 1
    fi
    log_success "SHA-256 checksum created at ${copied_apk%.apk}-checksum-sha256.txt"
}

install_release_apk() {
    strict_mode="${1:-true}"
    version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
    release_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
    release_apk="app/release-gf/Megingiard-GameFocus-v${release_version}.apk"

    if [[ ! -f "$release_apk" ]]; then
        release_apk=$(ls app/release-gf/*.apk 2>/dev/null | head -n 1 || true)
    fi

    if [[ -z "$release_apk" || ! -f "$release_apk" ]]; then
        log_error "Game Focus release APK not found in app/release-gf/. Please run 'scripts/release-gf.sh build' first."
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
        log_info "Installing Game Focus release APK on device ($release_apk)..."
        "${ADB_CMD[@]}" install -r "$release_apk"
        log_success "Successfully installed Game Focus release APK on device."
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
        build_release_apk
        ;;
    install)
        install_release_apk true
        ;;
    build-install|build_and_install|all)
        build_release_apk
        install_release_apk true
        ;;
    *)
        echo "Usage: $0 {build|install|build-install}"
        exit 1
        ;;
esac
