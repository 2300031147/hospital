"""
AEROVHYN — Structured JSON Logger
Emits JSON log lines to stdout, readable by Datadog/CloudWatch/Loki without extra plugins.
Falls back to plain text if LOG_FORMAT=text is set (useful in local dev).
"""

import logging
import json
import os
import sys
from datetime import datetime, timezone
from typing import Any

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_FORMAT = os.getenv("LOG_FORMAT", "json")  # "json" | "text"


class JSONFormatter(logging.Formatter):
    """Format log records as single-line JSON objects."""

    RESERVED = {"msg", "args", "exc_info", "exc_text", "stack_info"}

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "ts": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "msg": record.getMessage(),
        }

        # Attach request_id if injected by middleware
        if hasattr(record, "request_id"):
            payload["request_id"] = record.request_id

        # Extra fields passed via logger.info("...", extra={...})
        for key, val in record.__dict__.items():
            if key.startswith("_") or key in logging.LogRecord.__dict__ or key in self.RESERVED:
                continue
            if key not in ("name", "msg", "args", "levelname", "levelno", "pathname",
                           "filename", "module", "exc_info", "exc_text", "stack_info",
                           "lineno", "funcName", "created", "msecs", "relativeCreated",
                           "thread", "threadName", "processName", "process", "taskName",
                           "request_id", "message"):
                payload[key] = val

        if record.exc_info:
            payload["exc"] = self.formatException(record.exc_info)

        return json.dumps(payload, default=str)


class TextFormatter(logging.Formatter):
    LEVEL_COLORS = {
        "DEBUG": "\033[36m",
        "INFO": "\033[32m",
        "WARNING": "\033[33m",
        "ERROR": "\033[31m",
        "CRITICAL": "\033[35m",
    }
    RESET = "\033[0m"

    def format(self, record: logging.LogRecord) -> str:
        ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
        color = self.LEVEL_COLORS.get(record.levelname, "")
        rid = getattr(record, "request_id", None)
        rid_str = f" [{rid[:8]}]" if rid else ""
        return f"[{ts}]{rid_str} {color}[{record.levelname[:4]}]{self.RESET} {record.getMessage()}"


def _build_handler() -> logging.Handler:
    handler = logging.StreamHandler(sys.stdout)
    if LOG_FORMAT == "json":
        handler.setFormatter(JSONFormatter())
    else:
        handler.setFormatter(TextFormatter())
    return handler


def get_logger(name: str = "aerovhyn") -> logging.Logger:
    """Return a configured logger. Call once per module at the top level."""
    logger = logging.getLogger(name)
    if not logger.handlers:
        logger.addHandler(_build_handler())
        logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
        logger.propagate = False
    return logger


# Module-level singleton for convenience
log = get_logger("aerovhyn")
