"""
validation.py · LogStream Observability Platform
=================================================
Data validation for raw log DataFrames produced by data_generator.py.

Exports
-------
    ValidationReport  — dataclass describing the outcome of a validation run
    validate()        — cleans a raw DataFrame and returns (clean_df, report)

Import this module wherever raw log data needs to be checked before use:

    from scripts.validation import validate, ValidationReport

Field contract (must match backend log_entries table):
    id, timestamp, level, source, message, service_name, created_at
"""




from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

import pandas as pd

from config.config import REQUIRED_COLS, SERVICES, VALID_LEVELS
from scripts.utils.logger import get_logger


logger = get_logger("data_analytics")



# VALIDATION REPORT

@dataclass
class ValidationReport:
    total_rows:   int  = 0
    valid_rows:   int  = 0
    invalid_rows: int  = 0
    issues:       dict = field(default_factory=dict)

    def passed(self) -> bool:
        return self.invalid_rows == 0

    def summary(self) -> str:
        lines = [
            f"\n{'='*55}",
            "  Validation Report",
            f"  Total rows   : {self.total_rows:,}",
            f"  Valid rows   : {self.valid_rows:,}",
            f"  Invalid rows : {self.invalid_rows:,}",
        ]
        if self.issues:
            lines.append("  Issues found :")
            for check, count in self.issues.items():
                lines.append(f"    \u2717 {check:<40} {count:>6,} rows affected")
        else:
            lines.append("  \u2713 All checks passed")
        lines.append(f"{'='*55}\n")
        return "\n".join(lines)


# Validate
def validate(
    df: pd.DataFrame,
    raise_on_error: bool = False,
) -> tuple[pd.DataFrame, ValidationReport]:
    """
    Validate a raw log DataFrame against the log_entries field contract.

    Returns a *cleaned* copy of the DataFrame and a ValidationReport.
    Rows that fail any check are excluded from the cleaned copy.

    Checks
    ------
    1.  Required columns present (raises immediately if missing)
    2.  Completely empty rows
    3.  service_name not null or blank
    4.  level in {TRACE, DEBUG, INFO, WARN, ERROR}  (normalised to upper)
    5.  message not null or blank
    6.  timestamp parseable as datetime
    7.  No duplicate ids (first occurrence kept)
    8.  timestamp not in the future (60-second clock-skew tolerance)
    9.  service_name does not exceed 100 characters
    10. message is not suspiciously short (< 3 characters)

    Parameters
    ----------
    df             : raw DataFrame (straight from pd.read_json / pd.read_csv)
    raise_on_error : if True, raise ValueError when invalid rows exist

    Returns
    -------
    (clean_df, report)
    """
    report = ValidationReport(total_rows=len(df))
    issues: dict[str, int] = {}

    # Work on a copy so the caller's DataFrame is never mutated
    df = df.copy()
    mask_invalid = pd.Series(False, index=df.index)

    # ── 1. Required columns
    missing_cols = REQUIRED_COLS - set(df.columns)
    if missing_cols:
        raise ValueError(f"[validate] Missing required columns: {missing_cols}")

    # ── 2. Completely empty rows 
    empty_mask = df.isna().all(axis=1)
    if empty_mask.any():
        issues["Completely empty rows"] = int(empty_mask.sum())
        mask_invalid |= empty_mask

    # ── 3. service_name null or blank 
    bad_service = df["service_name"].isna() | (
        df["service_name"].astype(str).str.strip() == ""
    )
    if bad_service.any():
        issues["service_name null or blank"] = int(bad_service.sum())
        mask_invalid |= bad_service

    # ── 4. level normalisation and validation 
    normalised_level = df["level"].astype(str).str.upper().str.strip()
    bad_level = ~normalised_level.isin(VALID_LEVELS)
    if bad_level.any():
        issues["level not in TRACE/DEBUG/INFO/WARN/ERROR"] = int(bad_level.sum())
        mask_invalid |= bad_level
    else:
        df["level"] = normalised_level   # normalise in-place on the copy

    # ── 5. message null or blank 
    bad_msg = df["message"].isna() | (df["message"].astype(str).str.strip() == "")
    if bad_msg.any():
        issues["message null or blank"] = int(bad_msg.sum())
        mask_invalid |= bad_msg

    #
    # The generator emits ISO-8601 strings with +00:00 (timezone-aware).
    # utc=True interprets them correctly; .dt.tz_localize(None) then strips
    # the tz info so every downstream comparison is naive-vs-naive.
    # This is done ONCE here. A second pd.to_datetime call would re-introduce
    # tz-aware datetimes and break comparisons in _window() and validate().
    parsed_ts = pd.to_datetime(df["timestamp"], utc=True, errors="coerce").dt.tz_localize(None)
    bad_ts = parsed_ts.isna()
    if bad_ts.any():
        issues["timestamp unparseable"] = int(bad_ts.sum())
        mask_invalid |= bad_ts
    df["timestamp"] = parsed_ts   # assigned once; never re-parsed below

    # ──  Duplicate ids 
    dup_ids = df.duplicated(subset=["id"], keep="first")
    if dup_ids.any():
        issues["Duplicate id (keeping first)"] = int(dup_ids.sum())
        mask_invalid |= dup_ids

    # ── 8. Future timestamps (60-second clock-skew tolerance) 
    now_naive = datetime.now(timezone.utc).replace(tzinfo=None)
    future_mask = df["timestamp"] > now_naive + timedelta(seconds=60)
    if future_mask.any():
        issues["timestamp in the future"] = int(future_mask.sum())
        mask_invalid |= future_mask

    # ── 9. service_name length 
    too_long_svc = df["service_name"].astype(str).str.len() > 100
    if too_long_svc.any():
        issues["service_name exceeds 100 chars"] = int(too_long_svc.sum())
        mask_invalid |= too_long_svc

    # ── 10. Suspiciously short message 
    short_msg = df["message"].astype(str).str.strip().str.len() < 3
    if short_msg.any():
        issues["message shorter than 3 chars"] = int(short_msg.sum())
        mask_invalid |= short_msg

    # ── Build clean DataFrame 
    clean_df = df[~mask_invalid].reset_index(drop=True)

    report.invalid_rows = int(mask_invalid.sum())
    report.valid_rows   = len(clean_df)
    report.issues       = issues

    logger.info(report.summary())

    if raise_on_error and not report.passed():
        raise ValueError(
            f"Validation failed: {report.invalid_rows:,} invalid rows found."
        )

    return clean_df, report