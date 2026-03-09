"""Tests for scripts/utils/logger.py — Custom logging configuration."""
import logging
import pytest
from pathlib import Path


class TestGetLogger:
    """Verify that get_logger returns a properly configured logger."""

    def _fresh_logger(self, name):
        """
        Remove the logger from the manager so get_logger() treats it as brand new.
        This is needed because Python's logging module caches loggers by name,
        and the hasHandlers() guard in get_logger skips setup for existing loggers.
        """
        manager = logging.Logger.manager
        if name in manager.loggerDict:
            del manager.loggerDict[name]

    def test_get_logger_returns_logger(self):
        """get_logger should return a logging.Logger instance."""
        from scripts.utils.logger import get_logger
        self._fresh_logger("test_returns_instance")
        logger = get_logger("test_returns_instance")
        assert isinstance(logger, logging.Logger)
        assert logger.name == "test_returns_instance"

    def test_logger_has_console_handler(self):
        """Logger should have a StreamHandler for console output."""
        from scripts.utils.logger import get_logger
        self._fresh_logger("test_has_console")
        logger = get_logger("test_has_console")
        has_stream = any(
            isinstance(h, logging.StreamHandler) and not isinstance(h, logging.FileHandler)
            for h in logger.handlers
        )
        assert has_stream, f"Expected a StreamHandler, got: {[type(h).__name__ for h in logger.handlers]}"

    def test_logger_has_file_handler(self):
        """Logger should have a FileHandler for persistent logging."""
        from scripts.utils.logger import get_logger
        self._fresh_logger("test_has_file")
        logger = get_logger("test_has_file")
        has_file = any(isinstance(h, logging.FileHandler) for h in logger.handlers)
        assert has_file, f"Expected a FileHandler, got: {[type(h).__name__ for h in logger.handlers]}"

    def test_logger_no_duplicate_handlers(self):
        """Calling get_logger twice with the same name should not add duplicate handlers."""
        from scripts.utils.logger import get_logger
        self._fresh_logger("test_no_dup")
        logger1 = get_logger("test_no_dup")
        handler_count = len(logger1.handlers)
        logger2 = get_logger("test_no_dup")
        assert len(logger2.handlers) == handler_count
        assert logger1 is logger2

    def test_log_format(self, capfd):
        """Log output should match expected format: timestamp | LEVEL | name | message."""
        from scripts.utils.logger import get_logger
        self._fresh_logger("test_fmt_check")
        logger = get_logger("test_fmt_check")
        logger.info("Test message")

        captured = capfd.readouterr()
        assert "| INFO |" in captured.out
        assert "test_fmt_check" in captured.out
        assert "Test message" in captured.out

    def test_logger_level_is_info(self):
        """Logger level should be set to INFO."""
        from scripts.utils.logger import get_logger
        self._fresh_logger("test_lvl_info")
        logger = get_logger("test_lvl_info")
        assert logger.level == logging.INFO

    def test_log_file_created(self):
        """The log file should be created in the logs directory."""
        from scripts.utils.logger import get_logger, LOG_DIR
        self._fresh_logger("test_file_exists")
        logger = get_logger("test_file_exists")
        logger.info("File creation test")
        log_file = LOG_DIR / "test_file_exists.log"
        assert log_file.exists()
