import os
from datetime import datetime, timedelta
from typing import Optional
from fastapi import Depends, HTTPException, status, Request
from jose import JWTError, jwt
from pydantic import BaseModel

SECRET_KEY = os.getenv("AEROVHYN_JWT_SECRET")
if not SECRET_KEY:
    raise RuntimeError(
        "AEROVHYN_JWT_SECRET environment variable is not set. "
        "Generate one with: openssl rand -hex 32"
    )

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
    user_id: Optional[int] = None

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
    cookie_token = request.cookies.get("access_token")
    if cookie_token:
        if cookie_token.startswith("Bearer "):
            token = cookie_token[7:].strip()
        else:
            token = cookie_token.strip()
            
    # 2. If no token from cookie, check the Authorization header
    if not token:
        auth_header = request.headers.get("Authorization")
        if auth_header and auth_header.startswith("Bearer "):
            # Bug #40: Use slice instead of split to handle extra spaces robustly
            token = auth_header[7:].strip()
            
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
        user_id: Optional[int] = payload.get("user_id")
        
        # Bug #56: Check user existence in DB to instantly revoke deleted users
        from database import get_db
        db = await get_db()
        try:
            cursor = await db.execute("SELECT id FROM users WHERE username = ?", (username,))
            if not await cursor.fetchone():
                raise credentials_exception
        finally:
            await db.close()

        token_data = TokenData(username=username, role=role, hospital_id=hospital_id, ambulance_id=ambulance_id, user_id=user_id)
        return token_data
    except JWTError:
        raise credentials_exception

async def require_paramedic(token_data: TokenData = Depends(verify_token)):
    """
    Ensures user is a paramedic. 
    NOTE (Bug #38): command_center is allowed as a 'super-role' for ops override.
    """
    if token_data.role != "paramedic":
        if token_data.role == "command_center":
            return token_data
        raise HTTPException(status_code=403, detail="Paramedic or Dispatcher permissions required")
    return token_data

async def require_hospital_admin(token_data: TokenData = Depends(verify_token)):
    if token_data.role not in ["hospital_admin", "command_center"]:
        raise HTTPException(status_code=403, detail="Hospital Admin privileges required")
    return token_data

async def require_command_center(token_data: TokenData = Depends(verify_token)):
    if token_data.role != "command_center":
        raise HTTPException(status_code=403, detail="Command Center privileges required")
    return token_data
