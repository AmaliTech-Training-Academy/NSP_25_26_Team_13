"""Tests for config/config.py — Database configuration."""
import os
import pytest

from unittest.mock import patch

class TestDBConfig:
    """Verify that database configuration is loaded correctly."""

    @patch("dotenv.load_dotenv")
    def test_db_config_defaults(self, mock_load_dotenv, monkeypatch):
        """Config should use correct defaults when no env vars are set."""
        # Clear any env vars that might be set
        for var in ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"]:
            monkeypatch.delenv(var, raising=False)

        # Re-import to pick up the cleared env
        import importlib
        import config.config as cfg
        importlib.reload(cfg)

        assert cfg.DB_CONFIG["host"] == "localhost"
        assert cfg.DB_CONFIG["port"] == "5432"
        assert cfg.DB_CONFIG["database"] == "logstream"
        assert cfg.DB_CONFIG["user"] == "postgres"
        assert cfg.DB_CONFIG["password"] == "postgres"

    def test_db_config_from_env(self, monkeypatch):
        """Config should read from environment variables when present."""
        monkeypatch.setenv("DB_HOST", "prod-db.example.com")
        monkeypatch.setenv("DB_PORT", "5432")
        monkeypatch.setenv("DB_NAME", "logstream_prod")
        monkeypatch.setenv("DB_USER", "admin")
        monkeypatch.setenv("DB_PASSWORD", "s3cret")

        import importlib
        import config.config as cfg
        importlib.reload(cfg)

        assert cfg.DB_CONFIG["host"] == "prod-db.example.com"
        assert cfg.DB_CONFIG["port"] == "5432"
        assert cfg.DB_CONFIG["database"] == "logstream_prod"
        assert cfg.DB_CONFIG["user"] == "admin"
        assert cfg.DB_CONFIG["password"] == "s3cret"

    def test_database_url_format(self, monkeypatch):
        """DATABASE_URL should be a properly constructed PostgreSQL URL."""
        monkeypatch.setenv("DB_HOST", "myhost")
        monkeypatch.setenv("DB_PORT", "5432")
        monkeypatch.setenv("DB_NAME", "mydb")
        monkeypatch.setenv("DB_USER", "myuser")
        monkeypatch.setenv("DB_PASSWORD", "mypass")

        import importlib
        import config.config as cfg
        try:
            importlib.reload(cfg)
            assert cfg.DATABASE_URL == "postgresql://myuser:mypass@myhost:5432/mydb"
        finally:
            # Restore the real environment by dropping monkeypatch and reloading config
            monkeypatch.undo()
            importlib.reload(cfg)
