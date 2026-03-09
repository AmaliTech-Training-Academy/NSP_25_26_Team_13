"""Tests for scripts/etl_pipeline.py — ETL transform and pipeline logic."""
import pandas as pd
import pytest
from unittest.mock import patch, MagicMock
from datetime import datetime


class TestTransformHealthMetrics:
    """Unit tests for the transform_health_metrics function."""

    def test_normal_mixed_logs(self, sample_logs_df):
        """Should calculate correct error_rate and status for mixed log levels."""
        from scripts.etl_pipeline import transform_health_metrics
        result = transform_health_metrics(sample_logs_df)

        assert not result.empty
        assert "error_rate" in result.columns
        assert "status" in result.columns

        # auth-service: 1 ERROR out of 3 logs = 33.3% → CRITICAL
        auth = result[result["service_name"] == "auth-service"].iloc[0]
        assert auth["error_logs"] == 1
        assert auth["total_logs"] == 3
        assert auth["status"] == "CRITICAL"

    def test_empty_dataframe(self, empty_logs_df):
        """Should return empty DataFrame when input is empty."""
        from scripts.etl_pipeline import transform_health_metrics
        result = transform_health_metrics(empty_logs_df)
        assert result.empty


class TestAggregateMetrics:
    """Tests for the new aggregate_metrics function."""

    def test_hourly_aggregation(self, sample_logs_df):
        from scripts.etl_pipeline import aggregate_metrics
        start_ts = datetime(2025, 3, 1, 10, 0, 0)
        result = aggregate_metrics(sample_logs_df, start_ts, period_type="hour")

        assert not result.empty
        assert "hour_timestamp" in result.columns
        assert "total_count" in result.columns
        assert "error_count" in result.columns
        
        auth = result[result["service_name"] == "auth-service"].iloc[0]
        assert auth["total_count"] == 3
        assert auth["error_count"] == 1

    def test_empty_aggregation(self, empty_logs_df):
        from scripts.etl_pipeline import aggregate_metrics
        result = aggregate_metrics(empty_logs_df, datetime.utcnow())
        assert result.empty


class TestLoadData:
    """Tests for the load_data function."""

    def test_skips_empty_dataframe(self):
        """load_data should do nothing when given an empty DataFrame."""
        from scripts.etl_pipeline import load_data
        empty_df = pd.DataFrame()
        # Should not raise — just returns
        with patch("scripts.etl_pipeline.engine") as mock_engine:
            load_data(empty_df, "test_table")
            mock_engine.assert_not_called()


class TestRunPipeline:
    """Integration-level tests for the full pipeline run."""

    @patch("scripts.etl_pipeline.manage_partitions")
    @patch("scripts.etl_pipeline.extract_incremental_logs")
    @patch("scripts.etl_pipeline.load_data")
    def test_standard_pipeline_skips_on_empty(self, mock_load, mock_extract, mock_partitions, empty_logs_df):
        """Pipeline should skip transformation when no logs are found."""
        from scripts.etl_pipeline import run_standard_pipeline
        mock_extract.return_value = empty_logs_df

        run_standard_pipeline()
        
        mock_extract.assert_called_once()
        mock_load.assert_not_called()

    @patch("scripts.etl_pipeline.extract_logs")
    @patch("scripts.etl_pipeline.load_data")
    @patch("scripts.etl_pipeline.engine")
    def test_aggregation_job_success(self, mock_engine, mock_load, mock_extract, sample_logs_df):
        """Aggregation job should correctly process logs for a period."""
        from scripts.etl_pipeline import run_aggregation
        mock_extract.return_value = sample_logs_df
        
        # Mock connection for deletion
        mock_conn = MagicMock()
        mock_engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)

        run_aggregation(mode="hourly")
        
        assert mock_extract.called
        assert mock_load.called
        # Verify deletion query was sent
        mock_conn.execute.assert_called()
