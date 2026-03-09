"""
data_generator.py · LogStream Observability Platform
=====================================================
Generates realistic sample log data whose field names exactly match
the backend team's log_entries table:

    id, timestamp, level, source, message, service_name, created_at

Key design decisions
--------------------
* Error spikes and service outages are pre-placed as concrete datetime
  windows before any log is generated. This guarantees coherent,
  visible clusters in the timeline instead of random per-entry rolls.

* No mutable global state. All runtime state (spike windows, outage
  windows) lives in local dicts passed through function arguments.

* The generator retries up to 3x the requested count to compensate
  for logs dropped by outage windows, so the output count is reliable.
"""

import argparse
import json
import random
import uuid
from collections import Counter
from datetime import datetime, timedelta,timezone
from pathlib import Path

from config.config import (
    ERROR_SPIKES,
    LEVELS,
    LEVEL_WEIGHTS,
    MESSAGE_MAP,
    SERVICE_OUTAGES,
    SERVICES,
    _HOUR_WEIGHTS,
)

# ── Output directory ──────────────────────────────────────────────────────────
# Resolved relative to this file so it works regardless of working directory.
LOG_DIR = Path(__file__).resolve().parent.parent / "data"
LOG_DIR.mkdir(parents=True, exist_ok=True)




# WINDOW PRE-BUILDERS
# These run once at the start of generate_logs() and return plain dicts.
# Nothing is mutated after this point.
def _build_spike_windows(start: datetime, end: datetime) -> dict:
    """
    Pre-place error spike windows for each configured service.

    Returns {service_name: [(spike_start, spike_end), ...]}

    Each window is a concrete datetime pair placed randomly within
    [start, end]. When a log's timestamp falls inside a window for
    its service, elevated error weights are applied.
    """
    windows = {}
    total_seconds = int((end - start).total_seconds())

    for service, cfg in ERROR_SPIKES.items():
        service_windows = []
        for _ in range(cfg["num_spikes"]):
            spike_start = start + timedelta(seconds=random.randint(0, total_seconds))
            spike_end   = spike_start + timedelta(minutes=cfg["duration_minutes"])
            service_windows.append((spike_start, spike_end))
        windows[service] = service_windows

    return windows


def _build_outage_windows(start: datetime, end: datetime) -> dict:
    """
    Pre-place service outage windows for each configured service.

    Returns {service_name: [(outage_start, outage_end), ...]}

    Any log whose timestamp falls inside an outage window for its
    service is silently dropped — the service was down.
    """
    windows = {}
    total_seconds = int((end - start).total_seconds())

    for service, cfg in SERVICE_OUTAGES.items():
        service_windows = []
        for _ in range(cfg["num_outages"]):
            outage_start = start + timedelta(seconds=random.randint(0, total_seconds))
            outage_end   = outage_start + timedelta(minutes=cfg["duration_minutes"])
            service_windows.append((outage_start, outage_end))
        windows[service] = service_windows

    return windows


def _in_window(service: str, ts: datetime, windows: dict) -> bool:
    """Return True if ts falls inside any pre-placed window for service."""
    for window_start, window_end in windows.get(service, []):
        if window_start <= ts <= window_end:
            return True
    return False



# TIMESTAMP HELPER
def _realistic_timestamp(start: datetime, end: datetime) -> datetime:
    """
    Pick a random timestamp weighted toward business hours.
    Makes 3 attempts with hour-of-day weighting; falls back to the
    last candidate if none pass, so we always return a value.
    """
    delta_seconds = int((end - start).total_seconds())
    candidate = start
    for _ in range(3):
        candidate = start + timedelta(seconds=random.randint(0, delta_seconds))
        if random.random() < _HOUR_WEIGHTS[candidate.hour] / 2.5:
            return candidate
    return candidate



# MESSAGE BUILDER
def _pick_message(level: str) -> str:
    """Pick a random message template for level and fill {n} placeholders."""
    template = random.choice(MESSAGE_MAP[level])
    parts    = template.split("{n}")
    result   = parts[0]
    for part in parts[1:]:
        result += str(random.randint(10, 999)) + part
    return result



# SPIKE WEIGHT BUILDER
def _spike_weights(service: str) -> list:
    """
    Return level weights for a service currently in an error spike.

    ERROR gets the configured spike weight. The remaining probability
    is distributed across the other levels based on their baseline
    proportions from LEVEL_WEIGHTS.
    """

    spike_error = ERROR_SPIKES[service]["error_weight"]
    remaining = 1.0 - spike_error

    # Find ERROR index dynamically
    error_index = LEVELS.index("ERROR")

    # Sum of all non-error baseline weights
    base_sum = sum(
        w for i, w in enumerate(LEVEL_WEIGHTS)
        if i != error_index
    )

    spike_weights = []

    for i, weight in enumerate(LEVEL_WEIGHTS):
        if i == error_index:
            spike_weights.append(spike_error)
        else:
            spike_weights.append(remaining * (weight / base_sum))

    return spike_weights



# SINGLE LOG BUILDER

def generate_log(
    service_name:   str,
    ts:             datetime,
    spike_windows:  dict,
    outage_windows: dict,
):
    """
    Build one log record for service_name at timestamp ts.

    Returns None if ts falls inside an outage window (service was down).
    Otherwise returns a dict matching the backend log_entries schema.
    """
    # Outage check — service was silent during this window
    if _in_window(service_name, ts, outage_windows):
        return None

    # Level selection — spike overrides baseline weights
    if _in_window(service_name, ts, spike_windows):
        weights = _spike_weights(service_name)
    else:
        weights = LEVEL_WEIGHTS

    level = random.choices(LEVELS, weights=weights)[0]

    return {
        "id":           str(uuid.uuid4()),
        "timestamp":    ts.isoformat(),
        "level":        level,
        "source":       service_name,
        "message":      _pick_message(level),
        "service_name": service_name,
        "created_at":   ts.isoformat(),
    }



# BULK GENERATOR
def generate_logs(services: list, num_logs: int = 5000, days: int = 30) -> list:
    """
    Generate num_logs log records spread over the past `days` days.

    Spike and outage windows are pre-placed once before the main loop,
    so every record is checked against coherent datetime ranges rather
    than per-entry probability rolls.

    The loop runs up to 3x num_logs attempts to absorb logs dropped
    by outage windows, ensuring the output count is close to num_logs.
    """
    end_time   = datetime.now(timezone.utc)
    start_time = end_time - timedelta(days=days)

    # Pre-place all windows before touching any individual log
    spike_windows  = _build_spike_windows(start_time, end_time)
    outage_windows = _build_outage_windows(start_time, end_time)

    _print_windows(spike_windows, outage_windows)

    logs     = []
    attempts = 0
    max_attempts = num_logs * 3   # headroom to absorb outage drops

    while len(logs) < num_logs and attempts < max_attempts:
        service = random.choice(services)
        ts      = _realistic_timestamp(start_time, end_time)
        log     = generate_log(service, ts, spike_windows, outage_windows)
        if log:
            logs.append(log)
        attempts += 1

    if len(logs) < num_logs:
        print(
            f"[generator] Warning: only {len(logs):,} logs generated "
            f"(target {num_logs:,}) — outage windows may be very dense."
        )

    logs.sort(key=lambda x: x["timestamp"])
    return logs



# reporting helpers 
def _print_windows(spike_windows: dict, outage_windows: dict) -> None:
    """Print a summary of pre-placed windows so you can verify they are real."""
    print("\n-- Pre-placed spike windows ----------------------------------")
    for svc, windows in spike_windows.items():
        for i, (s, e) in enumerate(windows, 1):
            duration = int((e - s).total_seconds() // 60)
            print(f"  {svc}  spike {i}: "
                  f"{s.strftime('%Y-%m-%d %H:%M')} -> "
                  f"{e.strftime('%H:%M')}  ({duration} min)")
    print("-- Pre-placed outage windows ---------------------------------")
    for svc, windows in outage_windows.items():
        for i, (s, e) in enumerate(windows, 1):
            duration = int((e - s).total_seconds() // 60)
            print(f"  {svc}  outage {i}: "
                  f"{s.strftime('%Y-%m-%d %H:%M')} -> "
                  f"{e.strftime('%H:%M')}  ({duration} min)")
    print("--------------------------------------------------------------\n")


def print_summary(logs: list) -> None:
    """Print a distribution table to stdout after generation."""
    total = len(logs)
    print(f"\n{'='*56}")
    print(f"  Total logs generated : {total:,}")

    print(f"\n  Level distribution:")
    for level, count in Counter(l["level"] for l in logs).most_common():
        pct = count / total * 100
        bar = "X" * int(pct / 2)
        print(f"    {level:<7} {count:>6,}  ({pct:5.1f}%)  {bar}")

    print(f"\n  Service distribution:")
    for svc, count in Counter(l["service_name"] for l in logs).most_common():
        pct = count / total * 100
        print(f"    {svc:<26} {count:>6,}  ({pct:5.1f}%)")

    print(f"{'='*56}\n")



# CLI ENTRY POINT
def parse_args():
    parser = argparse.ArgumentParser(
        description="LogStream sample log data generator",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--services", nargs="+", default=list(SERVICES.keys()),
        help="Service names to include (space-separated)",
    )
    parser.add_argument(
        "--count", type=int, default=10_000,
        help="Number of log entries to generate",
    )
    parser.add_argument(
        "--days", type=int, default=30,
        help="Spread logs over the last N days",
    )
    parser.add_argument(
        "--out", type=str, default=None,
        help="Output file path (default: data/logs_YYYYMMDD_HHMMSS.json)",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()

    # Validate requested services against config
    unknown = set(args.services) - set(SERVICES.keys())
    if unknown:
        print(f"[generator] Unknown services ignored: {unknown}")
        args.services = [s for s in args.services if s in SERVICES]

    if not args.services:
        raise SystemExit("[generator] No valid services to generate logs for.")

    print(f"[generator] Generating {args.count:,} logs across "
          f"{len(args.services)} services over {args.days} days...")

    logs = generate_logs(args.services, num_logs=args.count, days=args.days)

    # Determine output path
    out_path = (
        Path(args.out)
        if args.out
        else LOG_DIR / f"logs_{datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')}.json"
    )
    out_path.parent.mkdir(parents=True, exist_ok=True)

    with open(out_path, "w") as f:
        json.dump(logs, f, indent=2)

    print_summary(logs)
    print(f"[generator] Saved {len(logs):,} records -> {out_path}")