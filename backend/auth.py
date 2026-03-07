import os
from datetime import datetime, timedelta
from typing import Optional
from fastapi import Depends, HTTPException, status, Request
from jose import JWTError, jwt
from pydantic import BaseModel

SECRET_KEY = os.getenv("AEROVHYN_JWT_SECRET", "AEROVHYN_EDGE_SUPER_SECRET_KEY_999")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 720  # Increased to 720 minutes (12 hours) for shift workers

class Token(BaseModel):
    access_token: str
    token_type: str

class TokenData(BaseModel):
    username: Optional[str] = None
    role: Optional[str] = None # 'paramedic', 'hospital_admin', 'command_center'
    hospital_id: Optional[int] = None
    ambulance_id: Optional[int] = None

def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=15)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt

async def verify_token(request: Request) -> TokenData:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    
    # 1. Check the cookie first and strip "Bearer " if present
    token = request.cookies.get("access_token")
    if token and token.startswith("Bearer "):
        token = token.split(" ")[1]
            
    # 2. If no cookie, check the Authorization header
    if not token:
        auth_header = request.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            token = auth_header.split(" ")[1]
            
    if not token:
        raise credentials_exception

    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        role: str = payload.get("role")
        hospital_id: int = payload.get("hospital_id")
        
        if username is None:
            raise credentials_exception
        ambulance_id: Optional[int] = payload.get("ambulance_id")
        token_data = TokenData(username=username, role=role, hospital_id=hospital_id, ambulance_id=ambulance_id)
        return token_data
    except JWTError:
        raise credentials_exception

async def require_paramedic(token_data: TokenData = Depends(verify_token)):
    if token_data.role != "paramedic":
        # Check if Command Center is testing the endpoint
        if token_data.role == "command_center":
            return token_data
        raise HTTPException(status_code=403, detail="Paramedic privileges required")
    return token_data

async def require_hospital_admin(token_data: TokenData = Depends(verify_token)):
    if token_data.role not in ["hospital_admin", "command_center"]:
        raise HTTPException(status_code=403, detail="Hospital Admin privileges required")
    return token_data

async def require_command_center(token_data: TokenData = Depends(verify_token)):
    if token_data.role != "command_center":
        raise HTTPException(status_code=403, detail="Command Center privileges required")
    return token_data
