#!/usr/bin/env zsh

# Megingiard Release Automation Script
#
# This script handles the automated stages of the Megingiard release workflow:
# 1. prepare: Creates a release branch (release/X.Y.Z), sets release versionName, tags, and pushes branch & tag.
# 2. build: Compiles the release APK, signs it securely, and generates its checksum.
# 3. publish <changelog-file>: Creates a GitHub release draft attaching the APK and checksum.
# 4. finish: Merges the release branch back into main (local only, does NOT push main).
# 5. bump: Increments versionCode and bumps versionName on main / release branch (local only for main).
#
# Fail fast on any error
set -e

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="$SCRIPT_DIR/.."
GRADLE_FILE="$PROJECT_ROOT/companion/ui/build.gradle.kts"
GF_GRADLE_FILE="$PROJECT_ROOT/gamefocus/ui/build.gradle.kts"
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"

# Ensure we are running from project root
cd "$PROJECT_ROOT"

# Helper to print colored output
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

# Verify clean git status (ignoring untracked files)
check_clean_git() {
    if [[ -n "$(git status --porcelain -uno)" ]]; then
        log_error "Git workspace has uncommitted tracked changes. Please commit or stash changes first."
        exit 1
    fi
}

# Helper function to build and sign release APK
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

    # Find version name to build
    version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
    release_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

    log_info "Building release APKs for version $release_version..."
    ./gradlew :companion:ui:assembleRelease :gamefocus:ui:assembleRelease

    # Ensure output dir exists, clean old artifacts, and copy the APKs
    rm -f app/release/*.apk(N) app/release/*-checksum-*.txt(N)
    mkdir -p app/release
    generated_apk="companion/ui/build/outputs/apk/release/Megingiard-v${release_version}.apk"
    copied_apk="app/release/Megingiard-v${release_version}.apk"

    if [[ ! -f "$generated_apk" ]]; then
        log_error "Generated APK not found at $generated_apk"
        exit 1
    fi

    cp "$generated_apk" "$copied_apk"
    log_info "Copied APK to $copied_apk"

    gf_generated_apk="gamefocus/ui/build/outputs/apk/release/Megingiard-GameFocus-v${release_version}.apk"
    if [[ -f "$gf_generated_apk" ]]; then
        gf_copied_apk="app/release/Megingiard-GameFocus-v${release_version}.apk"
        cp "$gf_generated_apk" "$gf_copied_apk"
        log_info "Copied Game Focus APK to $gf_copied_apk"
    fi

    # Run checksum script
    log_info "Generating SHA-256 checksum..."
    scripts/generate_checksum.sh

    log_success "Release Build $release_version APKs successfully created and signed."
}

# Helper function to install built release APK on connected Thor/Android device
install_release_apk() {
    strict_mode="${1:-false}"
    version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
    release_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
    copied_apk="app/release/Megingiard-v${release_version}.apk"

    if [[ ! -f "$copied_apk" ]]; then
        log_error "Release APK not found at $copied_apk. Please run 'scripts/release.sh build' first."
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

        remote_download_dir="/sdcard/Download"
        remote_apk_path="${remote_download_dir}/$(basename "$copied_apk")"

        log_info "Copying APK to Thor's Download folder ($remote_apk_path)..."
        "${ADB_CMD[@]}" push "$copied_apk" "$remote_apk_path"

        log_info "Installing APK on Thor via ADB ($copied_apk)..."
        "${ADB_CMD[@]}" install -r "$copied_apk"

        log_success "Successfully installed $copied_apk on Thor."
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
    prepare)
        check_clean_git

        start_branch=$(git branch --show-current)
        log_info "Initiating release preparation starting from branch '$start_branch'..."

        # Extract current version name from build.gradle.kts
        version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
        current_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

        if [[ "$start_branch" == "main" ]]; then
            # Standard release flow: drop -SNAPSHOT if present
            release_version="${current_version%-SNAPSHOT}"
            log_info "Standard release from main: target version $release_version"
        elif [[ "$start_branch" =~ ^release/ ]]; then
            # Check if current branch name matches current_version (resuming/re-pushing release)
            branch_version="${start_branch#release/}"
            if [[ "$current_version" == "$branch_version" ]]; then
                release_version="$current_version"
                log_info "Resuming release preparation on '$start_branch': target version $release_version"
            elif [[ "$current_version" =~ "-SNAPSHOT$" ]]; then
                release_version="${current_version%-SNAPSHOT}"
                log_info "Hotfix release from '$start_branch': target version $release_version"
            else
                IFS='.' read -r major minor patch <<< "$current_version"
                next_patch=$((patch + 1))
                release_version="${major}.${minor}.${next_patch}"
                log_info "Hotfix release from '$start_branch': target version $release_version"
            fi
        else
            log_error "Releases can only be initiated from 'main' or a 'release/*' branch (currently on '$start_branch')."
            exit 1
        fi

        release_branch="release/$release_version"

        if [[ "$start_branch" != "$release_branch" ]]; then
            # Check if release branch already exists locally
            if git show-ref --verify --quiet "refs/heads/$release_branch"; then
                log_info "Branch '$release_branch' already exists. Switching to it..."
                git checkout "$release_branch"
            else
                log_info "Creating and checking out new release branch '$release_branch'..."
                git checkout -b "$release_branch"
            fi
        fi

        # Update build.gradle.kts versionName on the release branch
        sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$release_version\"/" "$GRADLE_FILE"
        if [[ -f "$GF_GRADLE_FILE" ]]; then
            sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$release_version\"/" "$GF_GRADLE_FILE"
        fi

        # Commit release version change if modified
        if [[ -n "$(git status --porcelain "$GRADLE_FILE" "$GF_GRADLE_FILE")" ]]; then
            git add "$GRADLE_FILE" "$GF_GRADLE_FILE"
            git commit -m "chore(release): set version name to $release_version for release"
            log_info "Committed release version bump on $release_branch."
        fi

        # Tag commit if tag does not already exist
        if ! git rev-parse "$release_version" >/dev/null 2>&1; then
            git tag "$release_version"
            log_info "Created git tag $release_version."
        fi

        # Push branch and tag to remote
        log_info "Pushing branch '$release_branch' and tag '$release_version' to GitHub..."
        git push origin "$release_branch"
        git push origin "$release_version"

        log_success "Release version $release_version successfully prepared and tagged on branch $release_branch."
        ;;

    build)
        build_release_apk
        ;;

    install)
        install_release_apk true
        ;;

    build-install)
        build_release_apk
        install_release_apk false
        ;;

    publish)
        changelog_file="$2"
        if [[ -z "$changelog_file" || ! -f "$changelog_file" ]]; then
            log_error "Usage: scripts/release.sh publish <path-to-changelog-file>"
            exit 1
        fi

        # Find version name
        version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
        release_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

        apk_path="app/release/Megingiard-v${release_version}.apk"
        checksum_path="app/release/Megingiard-v${release_version}-checksum-sha256.txt"
        gf_apk_path="app/release/Megingiard-GameFocus-v${release_version}.apk"
        gf_checksum_path="app/release/Megingiard-GameFocus-v${release_version}-checksum-sha256.txt"

        if [[ ! -f "$apk_path" || ! -f "$checksum_path" ]]; then
            log_error "Release artifacts not found. Please run 'scripts/release.sh build' first."
            exit 1
        fi

        log_info "Creating GitHub Release Draft for version $release_version..."

        # Verify gh CLI is installed
        if ! command -v gh >/dev/null 2>&1; then
            log_error "GitHub CLI 'gh' is not installed or not in PATH."
            exit 1
        fi

        artifacts=("$apk_path" "$checksum_path")
        if [[ -f "$gf_apk_path" && -f "$gf_checksum_path" ]]; then
            artifacts+=("$gf_apk_path" "$gf_checksum_path")
        fi

        gh release create "$release_version" \
            --draft \
            --title "Megingiard-v$release_version" \
            --notes-file "$changelog_file" \
            "${artifacts[@]}"

        log_success "Release draft Megingiard-v$release_version successfully uploaded with APKs and checksums."
        ;;

    finish)
        check_clean_git

        current_branch=$(git branch --show-current)
        if [[ ! "$current_branch" =~ ^release/ ]]; then
            log_error "Command 'finish' must be executed from a release branch (currently on '$current_branch')."
            exit 1
        fi

        release_branch="$current_branch"
        log_info "Merging release branch '$release_branch' into main locally..."

        git checkout main

        # Attempt no-ff merge
        if ! git merge --no-ff "$release_branch" -m "chore(release): merge $release_branch into main"; then
            log_info "Merge conflict detected during merge of $release_branch into main."
            if git status --porcelain | grep -q "companion/ui/build.gradle.kts"; then
                log_info "Resolving companion/ui/build.gradle.kts conflict by favoring main's version configuration..."
                git checkout --ours companion/ui/build.gradle.kts
                git add companion/ui/build.gradle.kts
                git commit -m "chore(release): merge $release_branch into main (resolved build.gradle.kts)"
            else
                log_error "Merge conflict could not be automatically resolved. Please resolve conflicts manually."
                exit 1
            fi
        fi

        log_success "Successfully merged $release_branch into main locally."
        log_info "NOTE: 'main' was NOT pushed to remote. You can push main manually when ready."
        ;;

    bump)
        check_clean_git

        current_branch=$(git branch --show-current)

        # Extract current versionName and versionCode from Gradle file
        version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
        current_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

        code_line=$(grep -E 'versionCode[[:space:]]*=' "$GRADLE_FILE")
        current_code=$(echo "$code_line" | sed -E 's/.*versionCode[[:space:]]*=[[:space:]]*([0-9]*).*/\1/')

        next_code=$((current_code + 1))

        if [[ "$current_branch" == "main" ]]; then
            if [[ "$current_version" =~ "-SNAPSHOT$" ]]; then
                # main is already on a SNAPSHOT (e.g. 0.8.0-SNAPSHOT after merging a hotfix)
                log_info "Main is on $current_version. Incrementing versionCode to $next_code..."
                sed -i '' -E "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = $next_code/" "$GRADLE_FILE"
                if [[ -f "$GF_GRADLE_FILE" ]]; then
                    sed -i '' -E "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = $next_code/" "$GF_GRADLE_FILE"
                    git add "$GF_GRADLE_FILE"
                fi
                git add "$GRADLE_FILE"
                git commit -m "chore(release): set version code to $next_code for development"
                log_success "Successfully updated versionCode to $next_code on main (not pushed)."
            else
                # main is on release version (e.g. 0.7.0 after merging minor release)
                IFS='.' read -r major minor patch <<< "$current_version"
                next_minor=$((minor + 1))
                next_version="${major}.${next_minor}.0-SNAPSHOT"

                log_info "Upgrading main configuration for minor development..."
                log_info "Next Version Code: $next_code (was $current_code)"
                log_info "Next Version Name: $next_version (was $current_version)"

                sed -i '' -E "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = $next_code/" "$GRADLE_FILE"
                sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$next_version\"/" "$GRADLE_FILE"
                if [[ -f "$GF_GRADLE_FILE" ]]; then
                    sed -i '' -E "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = $next_code/" "$GF_GRADLE_FILE"
                    sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$next_version\"/" "$GF_GRADLE_FILE"
                    git add "$GF_GRADLE_FILE"
                fi

                git add "$GRADLE_FILE"
                git commit -m "chore(release): set version code to $next_code and version name to $next_version for development"
                log_success "Successfully bumped development version to $next_version (code: $next_code) on main (not pushed)."
            fi
        elif [[ "$current_branch" =~ ^release/ ]]; then
            # Bumping on a release branch for patch development
            IFS='.' read -r major minor patch <<< "${current_version%-SNAPSHOT}"
            next_patch=$((patch + 1))
            next_version="${major}.${minor}.${next_patch}-SNAPSHOT"

            log_info "Upgrading release branch '$current_branch' for patch development..."
            log_info "Next Version Code: $next_code (was $current_code)"
            log_info "Next Version Name: $next_version (was $current_version)"

            sed -i '' -E "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = $next_code/" "$GRADLE_FILE"
            sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$next_version\"/" "$GRADLE_FILE"
            if [[ -f "$GF_GRADLE_FILE" ]]; then
                sed -i '' -E "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = $next_code/" "$GF_GRADLE_FILE"
                sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$next_version\"/" "$GF_GRADLE_FILE"
                git add "$GF_GRADLE_FILE"
            fi

            git add "$GRADLE_FILE"
            git commit -m "chore(release): set version code to $next_code and version name to $next_version for development"

            log_info "Pushing developmental bump on '$current_branch' to GitHub..."
            git push origin "$current_branch"

            log_success "Successfully bumped release branch version to $next_version (code: $next_code)."
        fi
        ;;

    *)
        log_error "Unknown command. Usage: scripts/release.sh {prepare|build|install|build-install|publish|finish|bump}"
        exit 1
        ;;
esac

