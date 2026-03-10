"""
test_pandas_analytics.py
=========================
Test suite for pandas_analytics.py

Coverage:
  - validate()               : all 10 validation checks
  - error_rate_24h()         : core analytics
  - common_errors_top_n()    : core analytics
  - volume_trends_hourly()   : core analytics
  - volume_trends_daily()    : core analytics
  - warn_rate_24h()          : extended analytics
  - level_distribution()     : extended analytics
  - top_noisy_services()     : extended analytics
  - recent_critical_events() : extended analytics
  - error_spike_detection()  : extended analytics
  - silent_services()        : extended analytics
  - mean_time_between_errors(): extended analytics

Run with:
    pytest tests/test_data_analytics.py -v
    pytest tests/test_data_analytics.py -v --cov=scripts/analytics
"""

import uuid
import pytest
import pandas as pd
from datetime import datetime, timedelta, timezone


# Import the module under test
# Adjust the import path to match your project structure
from scripts.data_analytics import (
    validate,
    ValidationReport,
    error_rate_24h,
    common_errors_top_n,
    volume_trends_hourly,
    volume_trends_daily,
    warn_rate_24h,
    level_distribution,
    top_noisy_services,
    recent_critical_events,
    error_spike_detection,
    silent_services,
    mean_time_between_errors,
)



# Shared fixtures
def _make_row(
    service="auth-service",
    level="INFO",
    message="User authenticated",
    minutes_ago=30,
    id_=None,
):
    """Helper to build a single valid log row."""
    ts = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(minutes=minutes_ago)
    return {
        "id":           id_ or str(uuid.uuid4()),
        "timestamp":    ts,
        "level":        level,
        "source":       service,
        "message":      message,
        "service_name": service,
        "created_at":   ts,
    }


def _make_df(rows: list[dict]) -> pd.DataFrame:
    df = pd.DataFrame(rows)
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    return df


@pytest.fixture
def valid_df():
    """A clean, fully valid DataFrame with varied levels and services."""
    rows = []
    services = ["auth-service", "payment-service", "order-service"]
    levels   = ["INFO", "INFO", "INFO", "DEBUG", "DEBUG", "WARN", "ERROR", "TRACE"]

    for i, svc in enumerate(services):
        for j, lvl in enumerate(levels):
            rows.append(_make_row(
                service=svc,
                level=lvl,
                message=f"Sample {lvl} message from {svc} number {j}",
                minutes_ago=(j * 10) + (i * 5),  # spread timestamps
            ))
    return _make_df(rows)


@pytest.fixture
def recent_error_df():
    """DataFrame with errors in the last 24h for analytics tests."""
    rows = [
        _make_row("auth-service",    "ERROR", "JWT token expired",          minutes_ago=10),
        _make_row("auth-service",    "ERROR", "JWT token expired",          minutes_ago=20),
        _make_row("auth-service",    "INFO",  "User authenticated",         minutes_ago=30),
        _make_row("payment-service", "ERROR", "Payment gateway unavailable",minutes_ago=15),
        _make_row("payment-service", "INFO",  "Payment processed",          minutes_ago=25),
        _make_row("order-service",   "INFO",  "Order created",              minutes_ago=5),
    ]
    return _make_df(rows)



# SECTION 1 — validate()

class TestValidate:

    def test_valid_dataframe_passes_all_checks(self, valid_df):
        clean, report = validate(valid_df)
        assert report.passed()
        assert report.invalid_rows == 0
        assert len(clean) == len(valid_df)

    def test_missing_required_column_raises(self):
        df = _make_df([_make_row()])
        df = df.drop(columns=["service_name"])
        with pytest.raises(ValueError, match="Missing required columns"):
            validate(df)

    def test_null_service_name_is_dropped(self):
        rows = [_make_row(), _make_row(service=None)]
        rows[1]["service_name"] = None
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1
        assert "service_name null or blank" in report.issues

    def test_blank_service_name_is_dropped(self):
        rows = [_make_row(), _make_row(service="   ")]
        rows[1]["service_name"] = "   "
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1

    def test_invalid_level_is_dropped(self):
        rows = [_make_row(), _make_row(level="VERBOSE")]
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1
        assert "level not in TRACE/DEBUG/INFO/WARN/ERROR" in report.issues

    def test_level_is_normalized_to_uppercase(self):
        rows = [_make_row(level="info"), _make_row(level="warn")]
        df = _make_df(rows)
        clean, report = validate(df)
        assert set(clean["level"].unique()).issubset({"INFO", "WARN", "ERROR", "DEBUG", "TRACE"})

    def test_null_message_is_dropped(self):
        rows = [_make_row(), _make_row()]
        rows[1]["message"] = None
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1
        assert "message null or blank" in report.issues

    def test_blank_message_is_dropped(self):
        rows = [_make_row(), _make_row()]
        rows[1]["message"] = "   "
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1

    def test_unparseable_timestamp_is_dropped(self):
        # Build the bad row with a string timestamp BEFORE _make_df casts the column.
        # Pandas 2.x won't let you assign a raw string into a typed datetime64 column
        # after the fact, so we inject the bad value at dict level then build the
        # DataFrame with object dtype first, then let validate() handle the parsing.
        good_row = _make_row()
        bad_row  = _make_row()
        bad_row["timestamp"] = "not-a-date"
        df = pd.DataFrame([good_row, bad_row])   # no pd.to_datetime cast here
        clean, report = validate(df)
        assert len(clean) == 1
        assert "timestamp unparseable" in report.issues

    def test_duplicate_id_is_dropped(self):
        shared_id = str(uuid.uuid4())
        rows = [_make_row(id_=shared_id), _make_row(id_=shared_id)]
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1
        assert "Duplicate id (keeping first)" in report.issues

    def test_future_timestamp_is_dropped(self):
        rows = [_make_row(), _make_row()]
        df = _make_df(rows)
        df.loc[1, "timestamp"] = datetime.now(timezone.utc).replace(tzinfo=None) + timedelta(hours=2)
        clean, report = validate(df)
        assert len(clean) == 1
        assert "timestamp in the future" in report.issues

    def test_service_name_too_long_is_dropped(self):
        rows = [_make_row(), _make_row(service="x" * 101)]
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1
        assert "service_name exceeds 100 chars" in report.issues

    def test_short_message_is_dropped(self):
        rows = [_make_row(), _make_row(message="ab")]
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1
        assert "message shorter than 3 chars" in report.issues

    def test_raise_on_error_flag(self):
        rows = [_make_row(), _make_row(level="BAD")]
        df = _make_df(rows)
        with pytest.raises(ValueError, match="Validation failed"):
            validate(df, raise_on_error=True)

    def test_empty_dataframe_returns_empty(self):
        df = _make_df([_make_row()])
        df = df.iloc[0:0]  # empty but with correct columns
        clean, report = validate(df)
        assert len(clean) == 0
        assert report.total_rows == 0

    def test_multiple_issues_all_reported(self):
        rows = [
            _make_row(),                         # valid
            _make_row(level="BAD"),              # bad level
            _make_row(message="x"),              # short message
        ]
        df = _make_df(rows)
        clean, report = validate(df)
        assert len(clean) == 1
        assert report.invalid_rows == 2
        assert len(report.issues) >= 2



#— error_rate_24h()


class TestErrorRate24h:

    def test_returns_correct_columns(self, recent_error_df):
        result = error_rate_24h(recent_error_df)
        assert set(result.columns) == {"service_name", "total_logs", "error_count", "error_rate_percent"}

    def test_error_rate_is_correct(self, recent_error_df):
        result = error_rate_24h(recent_error_df)
        auth = result[result["service_name"] == "auth-service"].iloc[0]
        # auth has 2 errors out of 3 total = 66.67%
        assert auth["error_count"] == 2
        assert auth["total_logs"] == 3
        assert auth["error_rate_percent"] == round(2 / 3 * 100, 2)

    def test_sorted_by_error_rate_descending(self, recent_error_df):
        result = error_rate_24h(recent_error_df)
        rates = result["error_rate_percent"].tolist()
        assert rates == sorted(rates, reverse=True)

    def test_service_name_filter(self, recent_error_df):
        result = error_rate_24h(recent_error_df, service_name="auth-service")
        assert len(result) == 1
        assert result.iloc[0]["service_name"] == "auth-service"

    def test_returns_empty_df_when_no_data_in_window(self):
        rows = [_make_row(minutes_ago=1500)]  # 25 hours ago — outside 24h window
        df = _make_df(rows)
        result = error_rate_24h(df)
        assert result.empty

    def test_no_errors_gives_zero_rate(self):
        rows = [_make_row(level="INFO"), _make_row(level="DEBUG")]
        df = _make_df(rows)
        result = error_rate_24h(df)
        assert result.iloc[0]["error_rate_percent"] == 0.0



# — common_errors_top_n()


class TestCommonErrorsTopN:

    def test_returns_correct_columns(self, recent_error_df):
        result = common_errors_top_n(recent_error_df)
        assert set(result.columns) == {"service_name", "message", "occurrences"}

    def test_only_error_level_included(self, valid_df):
        result = common_errors_top_n(valid_df)
        # All rows in result must have come from ERROR-level logs
        # We verify by checking service/message combos exist in original ERROR rows
        errors = valid_df[valid_df["level"] == "ERROR"]
        for _, row in result.iterrows():
            match = errors[
                (errors["service_name"] == row["service_name"]) &
                (errors["message"]      == row["message"])
            ]
            assert len(match) > 0

    def test_top_n_limit_respected(self, valid_df):
        result = common_errors_top_n(valid_df, top_n=2)
        assert len(result) <= 2

    def test_sorted_by_occurrences_descending(self, recent_error_df):
        result = common_errors_top_n(recent_error_df)
        counts = result["occurrences"].tolist()
        assert counts == sorted(counts, reverse=True)

    def test_service_name_filter(self, recent_error_df):
        result = common_errors_top_n(recent_error_df, service_name="auth-service")
        assert all(result["service_name"] == "auth-service")

    def test_empty_when_no_errors(self):
        rows = [_make_row(level="INFO"), _make_row(level="DEBUG")]
        df = _make_df(rows)
        result = common_errors_top_n(df)
        assert result.empty

    def test_days_back_parameter_filters_old_data(self):
        rows = [
            _make_row(level="ERROR", minutes_ago=10),     # recent
            _make_row(level="ERROR", minutes_ago=50000),  # very old
        ]
        df = _make_df(rows)
        result = common_errors_top_n(df, days_back=1)
        assert len(result) == 1



#  — volume_trends_hourly()
class TestVolumeTrendsHourly:

    def test_returns_correct_columns(self, valid_df):
        result = volume_trends_hourly(valid_df)
        assert set(result.columns) == {"hour", "service_name", "level", "log_count"}

    def test_hour_column_is_floored_to_hour(self, valid_df):
        result = volume_trends_hourly(valid_df)
        for ts in result["hour"]:
            assert ts.minute == 0
            assert ts.second == 0

    def test_service_filter(self, valid_df):
        result = volume_trends_hourly(valid_df, service_name="auth-service")
        assert all(result["service_name"] == "auth-service")

    def test_days_back_excludes_old_records(self):
        rows = [
            _make_row(minutes_ago=60),     # 1 hour ago — within 7 days
            _make_row(minutes_ago=15000),  # ~10 days ago — outside 7-day window
        ]
        df = _make_df(rows)
        result = volume_trends_hourly(df, days_back=7)
        assert len(result) == 1

    def test_sorted_by_hour_ascending(self, valid_df):
        result = volume_trends_hourly(valid_df)
        hours = result["hour"].tolist()
        assert hours == sorted(hours)

    def test_empty_when_no_data_in_window(self):
        rows = [_make_row(minutes_ago=15000)]
        df = _make_df(rows)
        result = volume_trends_hourly(df, days_back=7)
        assert result.empty


# — volume_trends_daily()


class TestVolumeTrendsDaily:

    def test_returns_correct_columns(self, valid_df):
        result = volume_trends_daily(valid_df)
        assert set(result.columns) == {"day", "service_name", "level", "log_count"}

    def test_day_column_is_floored_to_day(self, valid_df):
        result = volume_trends_daily(valid_df)
        for ts in result["day"]:
            assert ts.hour == 0
            assert ts.minute == 0

    def test_service_filter(self, valid_df):
        result = volume_trends_daily(valid_df, service_name="order-service")
        assert all(result["service_name"] == "order-service")

    def test_days_back_parameter(self):
        rows = [
            _make_row(minutes_ago=60),      # recent
            _make_row(minutes_ago=50000),   # very old
        ]
        df = _make_df(rows)
        result = volume_trends_daily(df, days_back=30)
        assert len(result) == 1

    def test_log_counts_sum_to_total(self, valid_df):
        result = volume_trends_daily(valid_df)
        # Total log_count across all groups should equal rows within 30d window
        from datetime import timedelta
        cutoff = datetime.now(timezone.utc).replace(tzinfo=None) - timedelta(days=30)
        expected = len(valid_df[valid_df["timestamp"] >= cutoff])
        assert result["log_count"].sum() == expected



#— warn_rate_24h()


class TestWarnRate24h:

    def test_returns_correct_columns(self, valid_df):
        result = warn_rate_24h(valid_df)
        assert set(result.columns) == {"service_name", "total_logs", "warn_count", "warn_rate_percent"}

    def test_warn_rate_calculated_correctly(self):
        rows = [
            _make_row("auth-service", "WARN",  "Slow query",  minutes_ago=10),
            _make_row("auth-service", "WARN",  "Slow query",  minutes_ago=20),
            _make_row("auth-service", "INFO",  "Request OK",  minutes_ago=30),
            _make_row("auth-service", "INFO",  "Request OK",  minutes_ago=40),
        ]
        df = _make_df(rows)
        result = warn_rate_24h(df)
        row = result.iloc[0]
        assert row["warn_count"] == 2
        assert row["warn_rate_percent"] == 50.0

    def test_sorted_descending(self, valid_df):
        result = warn_rate_24h(valid_df)
        rates = result["warn_rate_percent"].tolist()
        assert rates == sorted(rates, reverse=True)

    def test_empty_when_no_data(self):
        rows = [_make_row(minutes_ago=1500)]
        df = _make_df(rows)
        result = warn_rate_24h(df)
        assert result.empty


# ===========================================================================
# SECTION 7 — level_distribution()
# ===========================================================================

class TestLevelDistribution:

    def test_returns_correct_columns(self, valid_df):
        result = level_distribution(valid_df)
        assert set(result.columns) == {"service_name", "level", "log_count", "pct_of_service_total"}

    def test_percentages_sum_to_100_per_service(self, valid_df):
        result = level_distribution(valid_df)
        for svc, grp in result.groupby("service_name"):
            total_pct = grp["pct_of_service_total"].sum()
            assert abs(total_pct - 100.0) < 0.1, f"{svc} pct sums to {total_pct}"

    def test_all_valid_levels_present_if_generated(self, valid_df):
        result = level_distribution(valid_df)
        all_levels = set(result["level"].unique())
        assert all_levels.issubset({"TRACE", "DEBUG", "INFO", "WARN", "ERROR"})


# ===========================================================================
# SECTION 8 — top_noisy_services()
# ===========================================================================

class TestTopNoisyServices:

    def test_returns_correct_columns(self, valid_df):
        result = top_noisy_services(valid_df)
        assert set(result.columns) == {"service_name", "total_logs", "pct_of_total"}

    def test_sorted_by_total_logs_descending(self, valid_df):
        result = top_noisy_services(valid_df)
        counts = result["total_logs"].tolist()
        assert counts == sorted(counts, reverse=True)

    def test_percentages_sum_to_100(self, valid_df):
        result = top_noisy_services(valid_df)
        assert abs(result["pct_of_total"].sum() - 100.0) < 0.1

    def test_empty_when_no_recent_data(self):
        rows = [_make_row(minutes_ago=1500)]
        df = _make_df(rows)
        result = top_noisy_services(df)
        assert result.empty


# ===========================================================================
# SECTION 9 — recent_critical_events()
# ===========================================================================

class TestRecentCriticalEvents:

    def test_returns_correct_columns(self, recent_error_df):
        result = recent_critical_events(recent_error_df)
        assert set(result.columns) == {"timestamp", "service_name", "level", "message"}

    def test_only_error_level_returned(self, valid_df):
        result = recent_critical_events(valid_df)
        assert all(result["level"] == "ERROR")

    def test_sorted_by_timestamp_descending(self, recent_error_df):
        result = recent_critical_events(recent_error_df)
        timestamps = result["timestamp"].tolist()
        assert timestamps == sorted(timestamps, reverse=True)

    def test_limit_respected(self, valid_df):
        result = recent_critical_events(valid_df, limit=1)
        assert len(result) <= 1

    def test_empty_when_no_errors(self):
        rows = [_make_row(level="INFO"), _make_row(level="DEBUG")]
        df = _make_df(rows)
        result = recent_critical_events(df)
        assert result.empty


# ===========================================================================
# SECTION 10 — error_spike_detection()  [stretch goal — lighter coverage]
# ===========================================================================

class TestErrorSpikeDetection:

    def test_returns_correct_columns(self, valid_df):
        result = error_spike_detection(valid_df)
        assert set(result.columns) == {
            "service_name", "avg_daily_errors_7d",
            "errors_last_1h", "spike_ratio", "spike_status"
        }

    def test_spike_status_values_are_valid(self, valid_df):
        result = error_spike_detection(valid_df)
        assert set(result["spike_status"].unique()).issubset({"NORMAL", "ELEVATED", "CRITICAL"})

    def test_normal_status_when_no_recent_errors(self):
        rows = [_make_row(level="INFO", minutes_ago=i * 10) for i in range(10)]
        df = _make_df(rows)
        result = error_spike_detection(df)
        # No errors at all — should return empty or all NORMAL
        if not result.empty:
            assert all(result["spike_status"] == "NORMAL")






# SECTION 11 — silent_services()  [stretch goal — lighter coverage]


class TestSilentServices:

    def test_returns_correct_columns(self):
        rows = [_make_row(minutes_ago=60)]
        df = _make_df(rows)
        result = silent_services(df, silent_minutes=10)
        assert set(result.columns) == {"service_name", "last_log_at", "minutes_silent"}

    def test_recent_service_not_flagged(self):
        rows = [_make_row(minutes_ago=2)]  # 2 minutes ago — not silent
        df = _make_df(rows)
        result = silent_services(df, silent_minutes=10)
        assert result.empty

    def test_old_service_is_flagged(self):
        rows = [_make_row(minutes_ago=60)]  # 60 minutes ago — silent
        df = _make_df(rows)
        result = silent_services(df, silent_minutes=10)
        assert len(result) == 1
        assert result.iloc[0]["minutes_silent"] >= 10


# ===========================================================================
# SECTION 12 — mean_time_between_errors()  [stretch goal — lighter coverage]
# ===========================================================================

class TestMeanTimeBetweenErrors:

    def test_returns_correct_columns(self):
        rows = [
            _make_row(level="ERROR", minutes_ago=10),
            _make_row(level="ERROR", minutes_ago=30),
        ]
        df = _make_df(rows)
        result = mean_time_between_errors(df)
        assert set(result.columns) == {
            "service_name", "total_errors", "avg_minutes_between_errors"
        }

    def test_gap_is_approximately_correct(self):
        rows = [
            _make_row("auth-service", "ERROR", "err", minutes_ago=60),
            _make_row("auth-service", "ERROR", "err", minutes_ago=30),
            _make_row("auth-service", "ERROR", "err", minutes_ago=0),
        ]
        df = _make_df(rows)
        result = mean_time_between_errors(df)
        row = result[result["service_name"] == "auth-service"].iloc[0]
        # gaps are ~30 min and ~30 min → avg ~30
        assert 25 <= row["avg_minutes_between_errors"] <= 35

    def test_empty_when_no_errors(self):
        rows = [_make_row(level="INFO"), _make_row(level="DEBUG")]
        df = _make_df(rows)
        result = mean_time_between_errors(df)
        assert result.empty

    def test_single_error_per_service_returns_empty(self):
        # Need at least 2 errors per service to compute a gap
        rows = [_make_row(level="ERROR")]
        df = _make_df(rows)
        result = mean_time_between_errors(df)
        assert result.empty