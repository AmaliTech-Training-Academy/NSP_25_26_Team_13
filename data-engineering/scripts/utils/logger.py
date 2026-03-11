"""
DESCRIPTION:
    Provides a centralized factory function for generating standardized logger 
    instances. This ensures consistent log formatting and dual-destination 
    output (Console + File) across all project modules.

KEY FEATURES:
    1. AUTOMATED DIRECTORY MANAGEMENT: Dynamically creates a '/logs' directory 
       at the project root if it is missing.
    2. DUAL-STREAM LOGGING: 
        - Console (STDOUT): Real-time visibility during development.
        - File (Persistent): Module-specific .log files for long-term debugging.
    3. SINGLETON-LIKE BEHAVIOR: Implements a check for existing handlers to 
       prevent duplicate log entries when a logger is requested multiple times.
    4. STANDARDIZED FORMATTING: Enforces a uniform structure across the app:
       [Timestamp] | [Level] | [Module Name] | [Message]
"""

import logging
import sys
from pathlib import Path

# Create a logs directory in the project root if it doesn't exist
LOG_DIR = Path(__file__).parent.parent / "../logs"
LOG_DIR.mkdir(exist_ok=True)

def get_logger(name: str):
    """
    Returns a configured logger instance with both file and console handlers.
    """
    
    logger = logging.getLogger(name)
    logger.setLevel(logging.INFO)

    # Prevent duplicate handlers if the logger is called multiple times
    if logger.handlers:
        return logger

    # Formatting: Includes timestamp, log level, and the specific module name
    formatter = logging.Formatter(
        "%(asctime)s | %(levelname)s | %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )

    # Console Handler (Standard Output)
    console_handler = logging.StreamHandler(sys.stdout)
    
    console_handler.setStream(open(sys.stdout.fileno(),mode='w',encoding='utf-8',buffering=1))
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

    # File Handler (Persistent logs for debugging)
    # Use the logger's name and convert to snake_case for the filename
    log_filename = f"{name.lower().replace(' ', '_')}.log"
    file_handler = logging.FileHandler(LOG_DIR / log_filename)
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    return logger