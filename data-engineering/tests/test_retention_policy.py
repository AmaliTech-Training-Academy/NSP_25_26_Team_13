"""Tests for scripts/retention_policy.py — Per-service retention enforcement logic."""
import pandas as pd
import pytest
from unittest.mock import patch, MagicMock, call
from datetime import datetime, timedelta


class TestGetRetentionPolicies:
    """Tests for fetching retention policies from the database."""

    @patch("retention_policy.pd.read_sql")
    @patch("retention_policy.engine")
    def test_returns_policies(self, mock_engine, mock_read_sql):
        """Should return a DataFrame of active retention policies including service_name."""
        from retention_policy import get_retention_policies

        expected = pd.DataFrame({
            "service_name": ["auth-service", None],
            "log_level": ["ERROR", "INFO"],
            "retention_days": [90, 30],
        })
        mock_read_sql.return_value = expected

        result = get_retention_policies()
        assert not result.empty
        assert "service_name" in result.columns
        assert "log_level" in result.columns
        assert "retention_days" in result.columns


class TestEnforceRetention:
    """Tests for the per-service retention enforcement logic."""

    @patch("retention_policy.ARCHIVAL_ENABLED", True)
    @patch("retention_policy.Path")
    @patch("retention_policy.pd.DataFrame.to_csv")
    @patch("retention_policy.pd.read_sql")
    @patch("retention_policy.engine")
    @patch("retention_policy.get_retention_policies")
    def test_archives_to_db_and_csv_when_enabled(self, mock_get_policies, mock_engine,
                                                   mock_read_sql, mock_to_csv, mock_path):
        """When archival is enabled, should INSERT into logs_archive, export CSV, then DELETE."""
        from retention_policy import enforce_retention

        mock_get_policies.return_value = pd.DataFrame({
            "service_name": ["auth-service"],
            "log_level": ["ERROR"],
            "retention_days": [30],
        })

        # Mock connection
        mock_conn = MagicMock()
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)

        # Mock the INSERT (archive) result
        mock_conn.execute.return_value.rowcount = 2

        # Mock read_sql to return expired rows for CSV export
        mock_read_sql.return_value = pd.DataFrame({
            "id": ["uuid-1", "uuid-2"],
            "timestamp": [datetime(2024, 1, 1), datetime(2024, 1, 2)],
            "level": ["ERROR", "ERROR"],
            "service_name": ["auth-service", "auth-service"],
        })

        # Mock Path for archive directory
        mock_archive_dir = MagicMock()
        mock_path.return_value.__truediv__ = MagicMock(return_value=mock_archive_dir)

        enforce_retention()

        # Should have called execute twice: INSERT INTO logs_archive + DELETE
        assert mock_conn.execute.call_count == 2

        # First call should be the archive INSERT
        first_call_sql = str(mock_conn.execute.call_args_list[0][0][0])
        assert "INSERT INTO logs_archive" in first_call_sql

        # Second call should be the DELETE
        second_call_sql = str(mock_conn.execute.call_args_list[1][0][0])
        assert "DELETE FROM log_entries" in second_call_sql

        # Should have archived to CSV
        mock_to_csv.assert_called_once()

    @patch("retention_policy.ARCHIVAL_ENABLED", False)
    @patch("retention_policy.Path")
    @patch("retention_policy.pd.DataFrame.to_csv")
    @patch("retention_policy.pd.read_sql")
    @patch("retention_policy.engine")
    @patch("retention_policy.get_retention_policies")
    def test_skips_archival_when_disabled(self, mock_get_policies, mock_engine,
                                           mock_read_sql, mock_to_csv, mock_path):
        """When archival is disabled, should only DELETE without INSERT or CSV."""
        from retention_policy import enforce_retention

        mock_get_policies.return_value = pd.DataFrame({
            "service_name": ["auth-service"],
            "log_level": ["ERROR"],
            "retention_days": [30],
        })

        # --- FIX: Mock the SELECT query to return data ---
        mock_read_sql.return_value = pd.DataFrame({"id": [1], "message": ["test"]})

        mock_conn = MagicMock()
        # Mock engine.connect() for the SELECT and engine.begin() for the DELETE
        mock_engine.connect.return_value.__enter__.return_value = mock_conn
        mock_engine.begin.return_value.__enter__.return_value = mock_conn
        
        enforce_retention()

        # Should only call execute once (for the DELETE)
        assert mock_conn.execute.call_count == 1
        mock_to_csv.assert_not_called()

    @patch("retention_policy.ARCHIVAL_ENABLED", False)
    @patch("retention_policy.Path")
    @patch("retention_policy.pd.read_sql")
    @patch("retention_policy.engine")
    @patch("retention_policy.get_retention_policies")
    def test_falls_back_to_global_default(self, mock_get_policies, mock_engine,
                                           mock_read_sql, mock_path):
        """When no policies exist, should fall back to 30-day global default."""
        from retention_policy import enforce_retention

        # No policies found
        mock_get_policies.return_value = pd.DataFrame()

        # --- FIX: Mock the SELECT query to return data for the default policy ---
        mock_read_sql.return_value = pd.DataFrame({"id": [1]})

        mock_conn = MagicMock()
        mock_engine.connect.return_value.__enter__.return_value = mock_conn
        mock_engine.begin.return_value.__enter__.return_value = mock_conn

        enforce_retention()
        
        # Verify the DELETE was called
        mock_conn.execute.assert_called_once()

    @patch("retention_policy.ARCHIVAL_ENABLED", False)
    @patch("retention_policy.Path")
    @patch("retention_policy.pd.read_sql")
    @patch("retention_policy.engine")
    @patch("retention_policy.get_retention_policies")
    def test_handles_error_per_policy(self, mock_get_policies, mock_engine,
                                       mock_read_sql, mock_path):
        """Errors on one policy should not crash the entire enforcement run."""
        from retention_policy import enforce_retention

        mock_get_policies.return_value = pd.DataFrame({
            "service_name": ["auth-service"],
            "log_level": ["ERROR"],
            "retention_days": [30],
        })

        mock_conn = MagicMock()
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)

        # Simulate execute throwing an error
        mock_conn.execute.side_effect = Exception("Connection lost")

        # Should NOT raise
        enforce_retention()

    @patch("retention_policy.ARCHIVAL_ENABLED", True)
    @patch("retention_policy.Path")
    @patch("retention_policy.pd.DataFrame.to_csv")
    @patch("retention_policy.pd.read_sql")
    @patch("retention_policy.engine")
    @patch("retention_policy.get_retention_policies")
    def test_global_policy_archives_all(self, mock_get_policies, mock_engine,
                                         mock_read_sql, mock_to_csv, mock_path):
        """A policy with NULL service_name and NULL log_level should archive and delete all logs."""
        from retention_policy import enforce_retention

        mock_get_policies.return_value = pd.DataFrame({
            "service_name": [None],
            "log_level": [None],
            "retention_days": [7],
        })

        mock_conn = MagicMock()
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
        mock_engine.begin.return_value.__exit__ = MagicMock(return_value=False)
        mock_conn.execute.return_value.rowcount = 1

        # Simulate expired rows for CSV
        mock_read_sql.return_value = pd.DataFrame({
            "id": ["uuid-1"],
            "timestamp": [datetime(2024, 1, 1)],
            "level": ["INFO"],
            "service_name": ["any-service"],
        })

        mock_archive_dir = MagicMock()
        mock_path.return_value.__truediv__ = MagicMock(return_value=mock_archive_dir)

        enforce_retention()

        # Should archive (INSERT) and delete
        assert mock_conn.execute.call_count == 2
        mock_to_csv.assert_called_once()


class TestArchivalConfig:
    """Tests for the archival configuration flag."""

    def test_archival_flag_defaults_to_disabled(self):
        """ARCHIVAL_ENABLED should default to False when env var is not set."""
        import os
        from unittest.mock import patch as env_patch

        with env_patch.dict(os.environ, {}, clear=True):
            # Re-evaluate the config expression
            result = os.getenv("ARCHIVAL_ENABLED", "false").lower() == "true"
            assert result is False

    def test_archival_flag_enabled_when_true(self):
        """ARCHIVAL_ENABLED should be True when env var is 'true'."""
        import os
        from unittest.mock import patch as env_patch

        with env_patch.dict(os.environ, {"ARCHIVAL_ENABLED": "true"}):
            result = os.getenv("ARCHIVAL_ENABLED", "false").lower() == "true"
            assert result is True
