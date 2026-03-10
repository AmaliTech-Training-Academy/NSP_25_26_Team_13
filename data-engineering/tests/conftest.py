"""
Shared pytest fixtures for data-engineering tests.
All fixtures here are automatically available to every test file.
"""
import sys
import pytest
import pandas as pd
from pathlib import Path
from unittest.mock import MagicMock

# Add the scripts directory to the Python path so tests can import modules
SCRIPTS_DIR = Path(__file__).parent.parent / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))


@pytest.fixture
def sample_logs_df():
    """A realistic sample DataFrame mimicking the log_entries table."""
    return pd.DataFrame({
        "id": ["aaa", "bbb", "ccc", "ddd", "eee", "fff"],
        "timestamp": pd.to_datetime([
            "2025-03-01 10:00:00",
            "2025-03-01 10:05:00",
            "2025-03-01 11:00:00",
            "2025-03-01 11:30:00",
            "2025-03-01 12:00:00",
            "2025-03-01 12:15:00",
        ]),
        "level": ["INFO", "ERROR", "INFO", "ERROR", "INFO", "WARN"],
        "message": [
            "Service started",
            "Connection timeout",
            "Request processed",
            "Connection timeout",
            "Health check OK",
            "High memory usage",
        ],
        "service_name": [
            "auth-service", "auth-service",
            "payment-api", "payment-api",
            "auth-service", "payment-api",
        ],
        "source": ["app", "app", "app", "app", "app", "app"],
    })


@pytest.fixture
def empty_logs_df():
    """An empty DataFrame with the correct columns."""
    return pd.DataFrame(columns=[
        "id", "timestamp", "level", "message", "service_name", "source"
    ])


@pytest.fixture
def mock_engine(mocker):
    """A mocked SQLAlchemy engine to avoid real DB connections."""
    engine = MagicMock()
    mock_conn = MagicMock()
    engine.connect.return_value.__enter__ = MagicMock(return_value=mock_conn)
    engine.connect.return_value.__exit__ = MagicMock(return_value=False)
    engine.begin.return_value.__enter__ = MagicMock(return_value=mock_conn)
    engine.begin.return_value.__exit__ = MagicMock(return_value=False)
    return engine, mock_conn


@pytest.fixture
def sample_retention_policies_df():
    """Sample retention policies for testing the retention enforcer."""
    return pd.DataFrame({
        "log_level": ["ERROR", "INFO"],
        "retention_days": [90, 30],
    })
