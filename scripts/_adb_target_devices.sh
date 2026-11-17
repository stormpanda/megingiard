#!/usr/bin/env zsh
# Shared ADB device discovery and interactive multi-device selection helper
# sourced by scripts/debug*.sh and scripts/release*.sh.
#
# Environment overrides:
#   ADB      Path to adb binary (auto-detected from PATH or ~/Library/Android/sdk/platform-tools/adb)
#   DEVICE   Target serial(s) (comma/space-separated) or "all" to bypass interactive prompt
#
# Populates globals:
#   ADB_BIN            Resolved path to the adb executable
#   SELECTED_DEVICES   1-indexed zsh array of selected ADB serials (empty if skipped/cancelled)
#   ADB_DEVICE_LABELS  Associative array mapping serial -> human-readable label

typeset -g ADB_BIN=""
typeset -ga SELECTED_DEVICES=()
typeset -gA ADB_DEVICE_LABELS=()

_format_adb_device_label() {
    local serial="$1"
    local details_line="$2"
    local model=""
    local transport="USB"

    if [[ "$serial" == *":"* || "$serial" == *"_adb"* ]]; then
        transport="Wi-Fi"
    fi

    if [[ "$details_line" =~ 'model:([^[:space:]]+)' ]]; then
        model="${match[1]//_/ }"
    fi

    if [[ -z "$model" ]]; then
        model=$("$ADB_BIN" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)
    fi

    if [[ -n "$model" ]]; then
        echo "${model} [${transport}] (${serial})"
    else
        echo "${serial} [${transport}]"
    fi
}

resolve_adb_target_devices() {
    local strict_mode="${1:-true}"
    ADB_BIN="${ADB:-$(command -v adb 2>/dev/null || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
    SELECTED_DEVICES=()
    ADB_DEVICE_LABELS=()

    if ! command -v "$ADB_BIN" >/dev/null 2>&1 && [[ ! -x "$ADB_BIN" ]]; then
        if [[ "$strict_mode" == "true" ]]; then
            log_error "ADB executable not found at '$ADB_BIN'."
            exit 1
        else
            log_info "ADB executable not found at '$ADB_BIN'. Skipping device install."
            return 0
        fi
    fi

    local raw_lines=(${(f)"$("$ADB_BIN" devices -l 2>/dev/null || true)"})
    local connected_devices=()
    local line serial state
    for line in "${raw_lines[@]}"; do
        [[ -z "$line" || "$line" == "List of devices"* ]] && continue
        serial="${line%%[[:space:]]*}"
        state=$(echo "$line" | awk '{print $2}')
        if [[ "$state" == "device" && -n "$serial" ]]; then
            connected_devices+=("$serial")
            ADB_DEVICE_LABELS[$serial]="$(_format_adb_device_label "$serial" "$line")"
        fi
    done

    local explicit_device="${DEVICE:-}"
    if [[ -n "$explicit_device" && "${explicit_device:l}" != "all" && "$explicit_device" != "*" ]]; then
        local -U explicit_list=(${=explicit_device//,/ })
        for serial in "${explicit_list[@]}"; do
            [[ -z "$serial" ]] && continue
            if [[ -z "${ADB_DEVICE_LABELS[$serial]:-}" ]]; then
                ADB_DEVICE_LABELS[$serial]="$(_format_adb_device_label "$serial" "")"
            fi
            SELECTED_DEVICES+=("$serial")
        done
        log_info "Using explicitly targeted ADB device(s): ${(j:, :)SELECTED_DEVICES}"
        return 0
    fi

    if (( ${#connected_devices} == 0 )); then
        if [[ "$strict_mode" == "true" ]]; then
            log_error "No connected Android device found via ADB."
            exit 1
        else
            log_info "No connected Android device found via ADB. Skipping device install."
            return 0
        fi
    fi

    if [[ "${explicit_device:l}" == "all" || "$explicit_device" == "*" ]]; then
        SELECTED_DEVICES=("${connected_devices[@]}")
        log_info "Targeting all ${#SELECTED_DEVICES} connected ADB device(s)."
        return 0
    fi

    if (( ${#connected_devices} == 1 )); then
        SELECTED_DEVICES=("${connected_devices[1]}")
        log_info "Android device detected via ADB: ${ADB_DEVICE_LABELS[${connected_devices[1]}]}"
        return 0
    fi

    if [[ ! -t 0 ]]; then
        SELECTED_DEVICES=("${connected_devices[1]}")
        log_warn "Multiple ADB devices detected in non-interactive session. Using first device: ${ADB_DEVICE_LABELS[${connected_devices[1]}]}"
        return 0
    fi

    echo ""
    log_info "Multiple ADB devices detected:"
    local idx
    for (( idx = 1; idx <= ${#connected_devices}; idx++ )); do
        serial="${connected_devices[$idx]}"
        echo -e "  \033[1;36m[$idx]\033[0m ${ADB_DEVICE_LABELS[$serial]}"
    done
    echo -e "  \033[1;36m[a]\033[0m All connected devices (${#connected_devices})"
    echo -e "  \033[1;36m[q]\033[0m Cancel / skip install"
    echo ""

    local selection token valid
    while true; do
        printf "Select device(s) to install on [1-%d, comma/space-separated, 'a' = all, 'q' = cancel] (default: a): " "${#connected_devices}"
        if ! read -r selection; then
            echo ""
            log_info "Input closed. Skipping device installation."
            SELECTED_DEVICES=()
            return 0
        fi

        selection="${selection#"${selection%%[![:space:]]*}"}"
        selection="${selection%"${selection##*[![:space:]]}"}"

        case "${selection:l}" in
            ""|a|all|\*)
                SELECTED_DEVICES=("${connected_devices[@]}")
                return 0
                ;;
            q|quit|cancel|n|none)
                log_info "Skipping device installation."
                SELECTED_DEVICES=()
                return 0
                ;;
        esac

        local tokens=(${=selection//,/ })
        local -U chosen=()
        valid=true
        for token in "${tokens[@]}"; do
            if [[ "${token:l}" == "a" || "${token:l}" == "all" ]]; then
                chosen=("${connected_devices[@]}")
            elif [[ "$token" =~ '^[0-9]+$' ]] && (( token >= 1 && token <= ${#connected_devices} )); then
                chosen+=("${connected_devices[$token]}")
            else
                valid=false
                break
            fi
        done

        if [[ "$valid" == "true" ]] && (( ${#chosen} > 0 )); then
            SELECTED_DEVICES=("${chosen[@]}")
            return 0
        fi

        log_warn "Invalid selection '$selection'. Enter number(s) 1-${#connected_devices} (e.g. 1 or 1,2), 'a' for all, or 'q' to cancel."
    done
}
