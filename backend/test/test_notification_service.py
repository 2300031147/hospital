import pytest
import asyncio
from unittest.mock import patch
import sys
import os

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from notification_service import send_sms_alert

@pytest.mark.asyncio
async def test_send_sms_alert_success():
    with patch("notification_service.log.info"):
        with patch("asyncio.sleep"):
            result = await send_sms_alert("Test Hospital", "Test Message", "+15550000000")
            assert result is True

@pytest.mark.asyncio
async def test_send_sms_alert_failure():
    # Mock asyncio.sleep to raise an exception
    with patch("asyncio.sleep", side_effect=Exception("Mocked network error")):
        result = await send_sms_alert("Test Hospital", "Test Message", "+15550000000")
        assert result is False
