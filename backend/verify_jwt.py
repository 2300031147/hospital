import sys
import os
import json
import base64

sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))
from fastapi.testclient import TestClient
from main import app
from database import get_db, Base, engine
import models
from passlib.context import CryptContext

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def verify_jwt():
    Base.metadata.create_all(bind=engine)
    db = next(get_db())
    # Create test ambulance
    amb = db.query(models.Ambulance).filter(models.Ambulance.name == "TEST-AMB-JWT").first()
    if not amb:
        amb = models.Ambulance(name="TEST-AMB-JWT", lat=0, lon=0)
        db.add(amb)
        db.commit()
        db.refresh(amb)
        
    # Create test user
    user = db.query(models.User).filter(models.User.username == "paramedic_jwt").first()
    if not user:
        user = models.User(
            username="paramedic_jwt",
            hashed_password=pwd_context.hash("SecurePass123!"),
            full_name="Test Paramedic",
            role="paramedic",
            ambulance_id="TEST-AMB-JWT"
        )
        db.add(user)
        db.commit()

    client = TestClient(app)
    response = client.post("/api/auth/token", data={"username": "paramedic_jwt", "password": "SecurePass123!"})
    token = response.json().get("access_token")
    
    if token:
        payload_b64 = token.split(".")[1]
        # Pad base64 if needed
        payload_b64 += "=" * ((4 - len(payload_b64) % 4) % 4)
        payload = json.loads(base64.b64decode(payload_b64).decode("utf-8"))
        print(f"JWT Payload:")
        print(json.dumps(payload, indent=2))
        print(f"ambulance_id type: {type(payload.get('ambulance_id'))}")
    else:
        print("Failed to login:", response.json())

if __name__ == "__main__":
    verify_jwt()
