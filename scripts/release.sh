#!/usr/bin/env zsh

# Megingiard Release Automation Script
#
# This script handles the automated stages of the Megingiard release workflow:
# 1. prepare: Removes '-SNAPSHOT' from versionName, commits, tags, and pushes tag.
# 2. build: Compiles the release APK, signs it securely, and generates its checksum.
# 3. publish <changelog-file>: Creates a GitHub release draft attaching the APK and checksum.
# 4. bump: Increments versionCode, bumps versionName to next minor-SNAPSHOT, commits, and pushes.
#
# Fail fast on any error
set -e

SCRIPT_DIR="${0:A:h}"
PROJECT_ROOT="$SCRIPT_DIR/.."
GRADLE_FILE="$PROJECT_ROOT/app/build.gradle.kts"
LOCAL_PROPERTIES="$PROJECT_ROOT/local.properties"

# Ensure we are running from project root
cd "$PROJECT_ROOT"

# Helper to print colored output
log_info() {
    echo -e "\033[1;34m[INFO]\033[0m $1"
}

log_success() {
    echo -e "\033[1;32m[SUCCESS]\033[0m $1"
}

log_error() {
    echo -e "\033[1;31m[ERROR]\033[0m $1" >&2
}

# Verify clean git status
check_clean_git() {
    if [[ -n "$(git status --porcelain)" ]]; then
        log_error "Git workspace is not clean. Please commit or stash changes first."
        exit 1
    fi
}

# Verify branch is main
check_branch() {
    current_branch=$(git branch --show-current)
    if [[ "$current_branch" != "main" ]]; then
        log_info "Warning: You are releasing from branch '$current_branch' (expected 'main')."
    fi
}

case "$1" in
    prepare)
        check_clean_git
        check_branch

        # Extract current version
        version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
        current_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')
        
        if [[ ! "$current_version" =~ "-SNAPSHOT$" ]]; then
            log_error "Current version name '$current_version' does not end with '-SNAPSHOT'."
            exit 1
        fi

        release_version="${current_version%-SNAPSHOT}"
        log_info "Releasing version $release_version (from $current_version)..."

        # Update build.gradle.kts versionName
        # Use portable Mac/Linux sed compatible with local edits
        sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$release_version\"/" "$GRADLE_FILE"

        # Commit release version change
        git add "$GRADLE_FILE"
        git commit -m "chore(release): set version name to $release_version for release"
        log_info "Committed release version bump."

        # Create tag
        git tag "$release_version"
        log_info "Created git tag $release_version."

        # Push commit and tag
        log_info "Pushing commit and tag to GitHub..."
        git push origin "$(git branch --show-current)"
        git push origin "$release_version"

        log_success "Release version $release_version successfully prepared and tagged."
        ;;

    build)
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

        log_info "Building release APK for version $release_version..."
        ./gradlew :app:assembleRelease

        # Ensure output dir exists and copy the APK
        mkdir -p app/release
        generated_apk="app/build/outputs/apk/release/Megingiard-v${release_version}.apk"
        copied_apk="app/release/Megingiard-v${release_version}.apk"

        if [[ ! -f "$generated_apk" ]]; then
            log_error "Generated APK not found at $generated_apk"
            exit 1
        fi

        cp "$generated_apk" "$copied_apk"
        log_info "Copied APK to $copied_apk"

        # Run checksum script
        log_info "Generating SHA-256 checksum..."
        scripts/generate_checksum.sh

        log_success "Release Build $release_version APK successfully created and signed."
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

        gh release create "$release_version" \
            --draft \
            --title "Megingiard-v$release_version" \
            --notes-file "$changelog_file" \
            "$apk_path" \
            "$checksum_path"

        log_success "Release draft Megingiard-v$release_version successfully uploaded with APK and checksum."
        ;;

    bump)
        # Extract current release version
        version_line=$(grep -E 'versionName[[:space:]]*=' "$GRADLE_FILE")
        release_version=$(echo "$version_line" | sed -E 's/.*versionName[[:space:]]*=[[:space:]]*"([^"]*)".*/\1/')

        # Extract current versionCode
        code_line=$(grep -E 'versionCode[[:space:]]*=' "$GRADLE_FILE")
        current_code=$(echo "$code_line" | sed -E 's/.*versionCode[[:space:]]*=[[:space:]]*([0-9]*).*/\1/')

        next_code=$((current_code + 1))

        # Parse release version to calculate next minor-SNAPSHOT version
        IFS='.' read -r major minor patch <<< "$release_version"
        next_minor=$((minor + 1))
        next_version="${major}.${next_minor}.0-SNAPSHOT"

        log_info "Upgrading version configuration for development..."
        log_info "Next Version Code: $next_code (was $current_code)"
        log_info "Next Version Name: $next_version (was $release_version)"

        # Update build.gradle.kts
        sed -i '' -E "s/versionCode[[:space:]]*=[[:space:]]*[0-9]*/versionCode = $next_code/" "$GRADLE_FILE"
        sed -i '' -E "s/versionName[[:space:]]*=[[:space:]]*\"[^\"]*\"/versionName = \"$next_version\"/" "$GRADLE_FILE"

        # Commit and push
        git add "$GRADLE_FILE"
        git commit -m "chore(release): set version code to $next_code and version name to $next_version for development"
        
        log_info "Pushing developmental version update to GitHub..."
        git push origin "$(git branch --show-current)"

        log_success "Successfully bumped development version to $next_version (code: $next_code)."
        ;;

    *)
        log_error "Unknown command. Usage: scripts/release.sh {prepare|build|publish|bump}"
        exit 1
        ;;
esac
