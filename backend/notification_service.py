import asyncio
from logger import get_logger

log = get_logger("aerovhyn.notifications")


async def send_sms_alert(hospital_name: str, safety_message: str, phone_number: str = "+15550000000"):
    """
    Simulates sending a high-priority SMS alert via Twilio / AWS SNS.
    In production, replace the sleep with an actual REST API call.
    """
    try:
        await asyncio.sleep(1)  # simulate network latency
        log.info("SMS SENT", extra={"phone": phone_number, "hospital": hospital_name, "message": safety_message})
        return True
    except Exception as e:
        log.error("SMS FAILED", extra={"phone": phone_number, "error": str(e)})
        return False


async def send_push_notification(hospital_id: int, title: str, body: str):
    """
    Simulates sending an FCM / APNs push notification to hospital staff devices.
    In production, replace the sleep with an actual FCM/APNs REST call.
    """
    try:
        await asyncio.sleep(0.5)
        log.info("PUSH SENT", extra={"hospital_id": hospital_id, "title": title, "body": body})
        return True
    except Exception as e:
        log.error("PUSH FAILED", extra={"hospital_id": hospital_id, "error": str(e)})
        return False


async def dispatch_critical_alerts(
    hospital_id: int,
    hospital_name: str,
    patient_severity: str,
    eta_minutes: float,
):
    """
    Dispatch outbound notifications for a CRITICAL patient routing event.
    Fires both SMS and push notifications concurrently. Errors are logged
    and do NOT propagate — a notification failure must never crash the
    routing pipeline.
    """
    message = (
        f"URGENT: CRITICAL patient arriving in {round(eta_minutes, 1)} min(s). "
        f"Open the AEROVHYN dashboard to ACCEPT the handoff."
    )

    results = await asyncio.gather(
        send_sms_alert(hospital_name, message, phone_number="+15559998888"),
        send_push_notification(hospital_id, "CRITICAL HANDOFF", message),
        return_exceptions=True,  # Bug 8 fix: errors are returned as values, not raised
    )

    for idx, result in enumerate(results):
        if isinstance(result, Exception):
            channel = "SMS" if idx == 0 else "Push"
            log.error(
                f"{channel} notification failed",
                extra={"hospital_id": hospital_id, "error": str(result)},
            )
