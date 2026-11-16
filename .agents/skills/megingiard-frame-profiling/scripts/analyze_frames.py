#!/usr/bin/env python3
"""Parse `dumpsys gfxinfo <pkg> framestats` and ART `am profile` (.trace) files."""

import argparse
import struct
from collections import defaultdict
from pathlib import Path

SUMMARY_PREFIXES = (
    "Total frames rendered:",
    "Janky frames:",
    "50th percentile:",
    "90th percentile:",
    "95th percentile:",
    "99th percentile:",
    "Number Missed Vsync:",
    "Number High input latency:",
    "Number Slow UI thread:",
    "Number Slow bitmap uploads:",
    "Number Slow issue draw commands:",
    "Number Frame deadline missed:",
    "HISTOGRAM:",
    "50th gpu percentile:",
    "90th gpu percentile:",
    "95th gpu percentile:",
    "99th gpu percentile:",
    "GPU HISTOGRAM:",
)


REQUIRED_CSV_COLUMNS = (
    "Flags",
    "IntendedVsync",
    "Vsync",
    "HandleInputStart",
    "AnimationStart",
    "PerformTraversalsStart",
    "DrawStart",
    "SyncQueued",
    "SyncStart",
    "IssueDrawCommandsStart",
    "SwapBuffers",
    "FrameCompleted",
)

# Default Android 12–16 (SDK 31–36) column positions when header row is omitted.
DEFAULT_CSV_COLUMN_INDICES = {
    "Flags": 0,
    "IntendedVsync": 2,
    "Vsync": 3,
    "HandleInputStart": 7,
    "AnimationStart": 8,
    "PerformTraversalsStart": 9,
    "DrawStart": 10,
    "SyncQueued": 14,
    "SyncStart": 15,
    "IssueDrawCommandsStart": 16,
    "SwapBuffers": 17,
    "FrameCompleted": 18,
    "GpuCompleted": 21,
}


def analyze_gfxinfo(path: Path, budget_ms: float, top_n: int) -> None:
    text = path.read_text(encoding="utf-8", errors="replace")
    print("=== GFXINFO SUMMARY ===")
    for raw in text.splitlines():
        line = raw.strip()
        if line.startswith(SUMMARY_PREFIXES):
            print(line)

    in_csv = False
    col_idx = dict(DEFAULT_CSV_COLUMN_INDICES)
    frames = []
    for raw in text.splitlines():
        line = raw.strip()
        if line == "---PROFILEDATA---":
            in_csv = not in_csv
            if in_csv:
                col_idx = dict(DEFAULT_CSV_COLUMN_INDICES)
            continue
        if not in_csv or not line:
            continue
        if line.startswith("Flags"):
            header_cols = [c.strip() for c in line.rstrip(",").split(",")]
            parsed_map = {name: idx for idx, name in enumerate(header_cols) if name}
            if all(req in parsed_map for req in REQUIRED_CSV_COLUMNS):
                col_idx = parsed_map
            continue

        parts = [p.strip() for p in line.rstrip(",").split(",")]
        max_req_idx = max(col_idx[req] for req in REQUIRED_CSV_COLUMNS)
        if len(parts) <= max_req_idx:
            continue
        try:
            flags = int(parts[col_idx["Flags"]])
            if flags != 0:
                continue
            intended = int(parts[col_idx["IntendedVsync"]])
            vsync = int(parts[col_idx["Vsync"]])
            input_s = int(parts[col_idx["HandleInputStart"]])
            anim_s = int(parts[col_idx["AnimationStart"]])
            trav_s = int(parts[col_idx["PerformTraversalsStart"]])
            draw_s = int(parts[col_idx["DrawStart"]])
            sync_q = int(parts[col_idx["SyncQueued"]])
            sync_s = int(parts[col_idx["SyncStart"]])
            issue_s = int(parts[col_idx["IssueDrawCommandsStart"]])
            swap_s = int(parts[col_idx["SwapBuffers"]])
            done = int(parts[col_idx["FrameCompleted"]])
            gpu_idx = col_idx.get("GpuCompleted", -1)
            gpu_raw = (
                int(parts[gpu_idx])
                if 0 <= gpu_idx < len(parts) and parts[gpu_idx] != ""
                else -1
            )
        except ValueError:
            continue
        gpu_done = gpu_raw if gpu_raw > issue_s else done

        total_ms = (done - intended) / 1e6
        if total_ms <= 0 or total_ms > 5000:
            continue
        frames.append(
            {
                "total": total_ms,
                "vsync_delay": max(0.0, (vsync - intended) / 1e6),
                "input": max(0.0, (anim_s - input_s) / 1e6),
                "anim": max(0.0, (trav_s - anim_s) / 1e6),
                "trav": max(0.0, (draw_s - trav_s) / 1e6),
                "draw_record": max(0.0, (sync_q - draw_s) / 1e6),
                "sync_wait": max(0.0, (sync_s - sync_q) / 1e6),
                "rt_sync": max(0.0, (issue_s - sync_s) / 1e6),
                "issue_draw": max(0.0, (swap_s - issue_s) / 1e6),
                "gpu": max(0.0, (gpu_done - issue_s) / 1e6),
            }
        )

    if not frames:
        print("\n(No Flags=0 PROFILEDATA rows found; pass `framestats` to `dumpsys gfxinfo`.)")
        return

    over = [f for f in frames if f["total"] > budget_ms]
    print(
        f"\n=== FRAMESTATS CSV ({len(frames)} normal frames, "
        f"{len(over)} > {budget_ms:.2f} ms budget) ==="
    )
    print(
        f"{'Total':>7} {'VsyncDly':>8} {'Input':>6} {'Anim':>6} "
        f"{'Trav':>6} {'DrawRec':>8} {'SyncWait':>8} {'RTSync':>7} {'Issue':>6} {'GPU':>6}"
    )
    for f in sorted(frames, key=lambda x: -x["total"])[:top_n]:
        print(
            f"{f['total']:7.2f} {f['vsync_delay']:8.2f} {f['input']:6.2f} {f['anim']:6.2f} "
            f"{f['trav']:6.2f} {f['draw_record']:8.2f} {f['sync_wait']:8.2f} "
            f"{f['rt_sync']:7.2f} {f['issue_draw']:6.2f} {f['gpu']:6.2f}"
        )


def parse_art_trace(data: bytes):
    methods = {}
    threads = {}
    records = []

    if data[:4] == b"SLOW":
        if len(data) < 8:
            return methods, threads, records
        version, offset = struct.unpack_from("<HH", data, 4)
        record_size = struct.unpack_from("<H", data, 16)[0] if version >= 3 and len(data) >= 18 else 14
        if record_size < 10 or offset > len(data):
            return methods, threads, records
        pos = offset
        while pos + 2 <= len(data):
            tid = struct.unpack_from("<H", data, pos)[0]
            if tid == 0:
                if pos + 3 > len(data):
                    break
                op = data[pos + 2]
                if op == 1:
                    if pos + 5 > len(data):
                        break
                    slen = struct.unpack_from("<H", data, pos + 3)[0]
                    if pos + 5 + slen > len(data):
                        break
                    s = data[pos + 5 : pos + 5 + slen].decode("utf-8", errors="replace")
                    pos += 5 + slen
                    parts = s.strip().split("\t")
                    if len(parts) >= 3:
                        methods[int(parts[0], 16)] = f"{parts[1]}.{parts[2]}"
                elif op == 2:
                    if pos + 7 > len(data):
                        break
                    rtid, slen = struct.unpack_from("<HH", data, pos + 3)
                    if pos + 7 + slen > len(data):
                        break
                    threads[rtid] = data[pos + 7 : pos + 7 + slen].decode("utf-8", errors="replace").strip()
                    pos += 7 + slen
                elif op == 3:
                    if pos + 7 > len(data):
                        break
                    slen = struct.unpack_from("<I", data, pos + 3)[0]
                    if pos + 7 + slen > len(data):
                        break
                    pos += 7 + slen
                else:
                    break
            else:
                if pos + record_size > len(data):
                    break
                if record_size >= 14:
                    ma, tt, wt = struct.unpack_from("<III", data, pos + 2)
                else:
                    ma, tt = struct.unpack_from("<II", data, pos + 2)
                    wt = tt
                records.append((tid, ma, tt, wt))
                pos += record_size
        return methods, threads, records

    end_idx = data.find(b"\n*end\n")
    if end_idx == -1:
        raise ValueError("Unrecognized trace header (neither streaming SLOW nor *end format)")
    header = data[:end_idx].decode("utf-8", errors="replace")
    body = data[end_idx + 6 :]
    section = None
    record_size = 14
    for line in header.splitlines():
        if line.startswith("*"):
            section = line.strip()
            continue
        if section == "*version" and "=" in line:
            k, v = line.split("=", 1)
            if k.strip() == "record-size":
                record_size = int(v.strip())
        elif section == "*threads":
            parts = line.split("\t", 1)
            if len(parts) == 2:
                threads[int(parts[0])] = parts[1].strip()
        elif section == "*methods":
            parts = line.split("\t")
            if len(parts) >= 3:
                methods[int(parts[0], 16)] = f"{parts[1]}.{parts[2]}"

    pos = 0
    if body[:4] == b"SLOW":
        if len(body) < 8:
            return methods, threads, records
        version, offset = struct.unpack_from("<HH", body, 4)
        if version >= 3 and len(body) >= 18:
            record_size = struct.unpack_from("<H", body, 16)[0]
        if record_size < 10 or offset > len(body):
            return methods, threads, records
        pos = offset
    while pos + record_size <= len(body):
        tid = struct.unpack_from("<H", body, pos)[0]
        if record_size >= 14:
            ma, tt, wt = struct.unpack_from("<III", body, pos + 2)
        else:
            ma, tt = struct.unpack_from("<II", body, pos + 2)
            wt = tt
        records.append((tid, ma, tt, wt))
        pos += record_size
    return methods, threads, records


def analyze_trace(path: Path, top_frames: int, min_us: int, max_depth: int) -> None:
    methods, threads, records = parse_art_trace(path.read_bytes())
    doframe_mids = {m for m, name in methods.items() if "Choreographer.doFrame" in name}
    tid_counts = defaultdict(int)
    for tid, ma, _, _ in records:
        if (ma & ~0x3) in doframe_mids:
            tid_counts[tid] += 1

    if tid_counts:
        main_tid = max(tid_counts, key=tid_counts.get)
    else:
        main_tid = next((t for t, name in threads.items() if name == "main"), 2)

    print(
        f"=== ART METHOD TRACE ({len(records)} records, "
        f"UI tid={main_tid} '{threads.get(main_tid, '?')}') ==="
    )

    stack = []
    frames = []
    self_tt = defaultdict(int)
    incl_tt = defaultdict(int)

    for tid, ma, tt, wt in records:
        if tid != main_tid:
            continue
        action = ma & 0x3
        mid = ma & ~0x3
        if action == 0:
            node = [mid, tt, wt, [], 0]
            if stack:
                stack[-1][3].append(node)
            stack.append(node)
        elif action in (1, 2) and stack:
            node = stack.pop()
            dur_tt = max(0, tt - node[1])
            dur_wt = max(0, wt - node[2])
            own_tt = max(0, dur_tt - node[4])
            node[1] = dur_tt
            node[2] = dur_wt
            self_tt[node[0]] += own_tt
            if all(anc[0] != node[0] for anc in stack):
                incl_tt[node[0]] += dur_tt
            if stack:
                stack[-1][4] += dur_tt
            if node[0] in doframe_mids:
                frames.append(node)

    print("\n=== TOP 20 COMPOSE / MEGINGIARD INCLUSIVE CPU TIME (UI THREAD) ===")
    keywords = ("stormpanda", "compose", "Pager", "Lazy", "AndroidComposeView", "Recomposer", "AndroidParagraph")
    filtered = [(m, t) for m, t in incl_tt.items() if any(k in methods.get(m, "") for k in keywords)]
    for mid, t in sorted(filtered, key=lambda x: -x[1])[:20]:
        print(f"  {t / 1000:7.2f} ms incl  ({self_tt[mid] / 1000:6.2f} ms self)  {methods.get(mid, hex(mid))}")

    frames.sort(key=lambda n: -n[1])
    print(f"\n=== SLOWEST {min(top_frames, len(frames))} / {len(frames)} Choreographer.doFrame CALL TREES ===")

    def dump_node(node, depth: int = 0) -> None:
        if depth > max_depth or node[1] < min_us:
            return
        indent = "  " * min(depth, 30)
        name = methods.get(node[0], hex(node[0]))
        print(f"{indent}[{depth:02d}] {node[1] / 1000:6.2f}ms cpu ({node[2] / 1000:6.2f}ms wall) {name}")
        for child in sorted(node[3], key=lambda c: -c[1]):
            dump_node(child, depth + 1)

    for idx, frame in enumerate(frames[:top_frames], start=1):
        print(f"\n--- Frame #{idx}: cpu={frame[1] / 1000:.2f}ms, wall={frame[2] / 1000:.2f}ms ---")
        dump_node(frame)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gfxinfo", type=Path, help="Path to `dumpsys gfxinfo <pkg> framestats` output")
    parser.add_argument("--budget-ms", type=float, default=8.33, help="Frame budget in ms (8.33 for 120Hz, 16.67 for 60Hz)")
    parser.add_argument("--top-frames", type=int, default=8, help="Number of slowest frames to print")
    parser.add_argument("--trace", type=Path, help="Path to pulled ART `am profile` .trace file")
    parser.add_argument("--min-us", type=int, default=250, help="Minimum CPU microseconds for call-tree nodes")
    parser.add_argument("--max-depth", type=int, default=45, help="Maximum call-tree depth to print")
    args = parser.parse_args()

    if not args.gfxinfo and not args.trace:
        parser.error("Provide at least one of --gfxinfo or --trace")
    if args.gfxinfo:
        analyze_gfxinfo(args.gfxinfo, args.budget_ms, args.top_frames)
    if args.trace:
        analyze_trace(args.trace, args.top_frames, args.min_us, args.max_depth)


if __name__ == "__main__":
    main()
