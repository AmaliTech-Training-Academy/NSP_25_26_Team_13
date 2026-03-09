import pytest
from datetime import datetime, timedelta,timezone
from collections import Counter

from scripts.data_generator import (
    generate_log,
    generate_logs,
    _build_spike_windows,
    _build_outage_windows,
    _in_window,
    LEVELS,
    SERVICES,
    ERROR_SPIKES,
    SERVICE_OUTAGES,
)

# ── Test helpers ─────────────────────────────────────────────
def all_keys_present(log: dict) -> bool:
    """Check that a log dict has all required keys."""
    required_keys = {"id", "timestamp", "level", "source", "message", "service_name", "created_at"}
    return required_keys <= log.keys()

# ── Tests ────────────────────────────────────────────────────
def test_generate_single_log_no_spike_or_outage():
    """Log generation without spike or outage should always produce a dict."""
    now = datetime.now(timezone.utc)
    spike_windows = {}
    outage_windows = {}
    log = generate_log("auth-service", now, spike_windows, outage_windows)
    assert log is not None
    assert all_keys_present(log)
    assert log["service_name"] == "auth-service"
    assert log["level"] in LEVELS

def test_outage_window_blocks_log():
    """Logs during outage windows should return None."""
    now = datetime.now(timezone.utc)
    outage_windows = {"auth-service": [(now - timedelta(minutes=1), now + timedelta(minutes=1))]}
    spike_windows = {}
    log = generate_log("auth-service", now, spike_windows, outage_windows)
    assert log is None

def test_spike_window_increases_error_probability():
    """Check that logs inside spike windows use the spike weight."""
    now = datetime.now(timezone.utc)
    spike_windows = {"payment-service": [(now - timedelta(minutes=1), now + timedelta(minutes=1))]}
    outage_windows = {}
    
    error_count = 0
    iterations = 500
    for _ in range(iterations):
        log = generate_log("payment-service", now, spike_windows, outage_windows)
        if log["level"] == "ERROR":
            error_count += 1
    spike_error_weight = ERROR_SPIKES["payment-service"]["error_weight"]
    # Allow some variation due to randomness
    assert 0.5 <= error_count / iterations <= 0.8

def test_generate_bulk_logs_count_and_schema():
    """Check that bulk log generation produces the correct count and schema."""
    services = ["auth-service", "payment-service"]
    logs = generate_logs(services, num_logs=50, days=1)
    assert len(logs) == 50
    for log in logs:
        assert all_keys_present(log)
        assert log["service_name"] in services
        assert log["level"] in LEVELS

def test_build_spike_and_outage_windows():
    """Ensure that spike and outage windows are correctly created."""
    start = datetime.now(timezone.utc)
    end = start + timedelta(hours=1)
    
    spike_windows = _build_spike_windows(start, end)
    outage_windows = _build_outage_windows(start, end)
    
    # Should create windows only for configured services
    for svc in ERROR_SPIKES.keys():
        assert svc in spike_windows
        assert len(spike_windows[svc]) == ERROR_SPIKES[svc]["num_spikes"]
    for svc in SERVICE_OUTAGES.keys():
        assert svc in outage_windows
        assert len(outage_windows[svc]) == SERVICE_OUTAGES[svc]["num_outages"]

def test_in_window_detection():
    """_in_window correctly detects timestamps inside windows."""
    now = datetime.now(timezone.utc)
    windows = {"auth-service": [(now - timedelta(minutes=1), now + timedelta(minutes=1))]}
    assert _in_window("auth-service", now, windows)
    assert not _in_window("auth-service", now - timedelta(minutes=5), windows)
    assert not _in_window("payment-service", now, windows)