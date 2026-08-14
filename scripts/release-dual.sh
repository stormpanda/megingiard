#!/usr/bin/env zsh

# Megingiard Dual App (Companion + Game Focus) Release Workflow Automation Script
#
# Commands:
#   build          Compiles both companion & Game Focus release APKs, signs them, and copies them to output dirs
#   install        Installs both built release APKs on a connected device via ADB
#   build-install  Builds and installs both release APKs in a single step
#
set -e

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="$SCRIPT_DIR/.."
APP_GRADLE_FILE="$PROJECT_ROOT/companion/ui/build.gradle.kts"
GF_GRADLE_FILE="$PROJECT_ROOT/gamefocus/ui/build.gradle.kts"
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

build_release_apks() {
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

    app_version_line=$(grep -E 'versionName[[:space:]]*=' "$APP_GRADLE_FILE")
    app_version=$(echo "$app_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

    gf_version_line=$(grep -E 'versionName[[:space:]]*=' "$GF_GRADLE_FILE")
    gf_version=$(echo "$gf_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

    log_info "Building Megingiard companion ($app_version) & Game Focus ($gf_version) release APKs..."
    ./gradlew :companion:ui:assembleRelease :gamefocus:ui:assembleRelease

    generated_app_apk="companion/ui/build/outputs/apk/release/Megingiard-v${app_version}.apk"
    if [[ ! -f "$generated_app_apk" ]]; then
        generated_app_apk=$(ls companion/ui/build/outputs/apk/release/*.apk 2>/dev/null | head -n 1 || true)
    fi

    generated_gf_apk="gamefocus/ui/build/outputs/apk/release/Megingiard-GameFocus-v${gf_version}.apk"
    if [[ ! -f "$generated_gf_apk" ]]; then
        generated_gf_apk=$(ls gamefocus/ui/build/outputs/apk/release/*.apk 2>/dev/null | head -n 1 || true)
    fi

    if [[ ! -f "$generated_app_apk" ]]; then
        log_error "Generated Megingiard companion release APK not found at companion/ui/build/outputs/apk/release/"
        exit 1
    fi

    if [[ ! -f "$generated_gf_apk" ]]; then
        log_error "Generated Game Focus release APK not found at gamefocus/ui/build/outputs/apk/release/"
        exit 1
    fi

    # Clean legacy/old release outputs
    rm -f app/release/*.apk(N) app/release/*-checksum-*.txt(N)
    mkdir -p app/release
    copied_app_apk="app/release/Megingiard-v${app_version}.apk"
    cp "$generated_app_apk" "$copied_app_apk"
    log_success "Megingiard companion release APK created at $copied_app_apk"

    rm -f app/release-gf/*.apk(N) app/release-gf/*-checksum-*.txt(N)
    mkdir -p app/release-gf
    copied_gf_apk="app/release-gf/Megingiard-GameFocus-v${gf_version}.apk"
    cp "$generated_gf_apk" "$copied_gf_apk"
    log_success "Game Focus launcher release APK created at $copied_gf_apk"

    # Generate Checksums
    log_info "Generating SHA-256 checksums..."
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$copied_app_apk" | awk '{ print $1 }' > "${copied_app_apk%.apk}-checksum-sha256.txt"
        shasum -a 256 "$copied_gf_apk" | awk '{ print $1 }' > "${copied_gf_apk%.apk}-checksum-sha256.txt"
    elif command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$copied_app_apk" | awk '{ print $1 }' > "${copied_app_apk%.apk}-checksum-sha256.txt"
        sha256sum "$copied_gf_apk" | awk '{ print $1 }' > "${copied_gf_apk%.apk}-checksum-sha256.txt"
    else
        log_error "Neither 'shasum' nor 'sha256sum' found — cannot generate checksums"
        exit 1
    fi
    log_success "Checksums successfully generated."
}

install_release_apks() {
    strict_mode="${1:-true}"
    app_version_line=$(grep -E 'versionName[[:space:]]*=' "$APP_GRADLE_FILE")
    app_version=$(echo "$app_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
    app_apk="app/release/Megingiard-v${app_version}.apk"

    if [[ ! -f "$app_apk" ]]; then
        app_apk=$(ls app/release/*.apk 2>/dev/null | head -n 1 || true)
    fi

    gf_version_line=$(grep -E 'versionName[[:space:]]*=' "$GF_GRADLE_FILE")
    gf_version=$(echo "$gf_version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
    gf_apk="app/release-gf/Megingiard-GameFocus-v${gf_version}.apk"

    if [[ ! -f "$gf_apk" ]]; then
        gf_apk=$(ls app/release-gf/*.apk 2>/dev/null | head -n 1 || true)
    fi

    if [[ -z "$app_apk" || ! -f "$app_apk" ]]; then
        log_error "Megingiard companion release APK not found in app/release/. Please run 'scripts/release-dual.sh build' first."
        exit 1
    fi

    if [[ -z "$gf_apk" || ! -f "$gf_apk" ]]; then
        log_error "Game Focus launcher release APK not found in app/release-gf/. Please run 'scripts/release-dual.sh build' first."
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
        log_info "Installing Megingiard companion release APK ($app_apk)..."
        "${ADB_CMD[@]}" install -r "$app_apk"
        log_success "Successfully installed Megingiard companion release APK."

        log_info "Installing Game Focus launcher release APK ($gf_apk)..."
        "${ADB_CMD[@]}" install -r "$gf_apk"
        log_success "Successfully installed Game Focus launcher release APK."
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
        build_release_apks
        ;;
    install)
        install_release_apks true
        ;;
    build-install|build_and_install|all)
        build_release_apks
        install_release_apks true
        ;;
    *)
        echo "Usage: $0 {build|install|build-install}"
        exit 1
        ;;
esac
