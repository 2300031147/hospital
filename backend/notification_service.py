import asyncio
from logger import get_logger

log = get_logger("aerovhyn.notifications")

async def send_sms_alert(hospital_name: str, safety_message: str, phone_number: str = "+15550000000"):
    """
    Simulates sending a high-priority SMS alert via a service like Twilio.
    In a real production environment, this would await a REST API call to Twilio or AWS SNS.
    """
    await asyncio.sleep(1) # simulate network latency
    log.info(f"SMS SENT to {phone_number}", extra={"hospital": hospital_name, "message": safety_message})
    return True

async def send_push_notification(hospital_id: int, title: str, body: str):
    """
    Simulates sending a Firebase Cloud Messaging (FCM) or Apple Push Notification (APNs) 
    to all registered devices for a specific hospital staff group.
    """
    await asyncio.sleep(0.5)
    log.info(f"PUSH SENT to Hospital ID {hospital_id}", extra={"title": title, "body": body})
    return True

async def dispatch_critical_alerts(hospital_id: int, hospital_name: str, patient_severity: str, eta_minutes: float):
    """
    Main orchestrator for outward bound notifications.
    Triggered when a CRITICAL patient is routed to a hospital.
    """
    if patient_severity.lower() == "critical":
        message = f"URGENT: CRITICAL patient arriving in {eta_minutes} mins. Please check dashboard to ACCEPT."
        
        # Fire both notifications asynchronously without blocking the main event loop
        tasks = [
            send_sms_alert(hospital_name, message, phone_number="+15559998888"), # e.g. Chief of Staff
            send_push_notification(hospital_id, "CRITICAL HANDOFF", message)
        ]
        
        await asyncio.gather(*tasks)
