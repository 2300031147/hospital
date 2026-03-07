import requests
import json
import base64
import time

def verify():
    print("Attempting login via running server on 8990...")
    try:
        resp = requests.post("http://127.0.0.1:8990/api/auth/token", json={"username": "paramedic1", "password": "rescue123"})
        if resp.status_code != 200:
            print("Login failed:", resp.status_code, resp.text)
            return
            
        token = resp.json().get("access_token")
        payload_b64 = token.split(".")[1]
        payload_b64 += "=" * ((4 - len(payload_b64) % 4) % 4)
        payload = json.loads(base64.b64decode(payload_b64).decode("utf-8"))
        print(f"JWT Payload:")
        print(json.dumps(payload, indent=2))
        print(f"ambulance_id type: {type(payload.get('ambulance_id'))}")
    except Exception as e:
        print("Error:", e)

if __name__ == "__main__":
    verify()
