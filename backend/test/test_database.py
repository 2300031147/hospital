import pytest
import os
import sys

# Ensure passlib is available by installing it locally inside this sandbox if needed
# We just installed the packages!
from database import hash_password, verify_password

def test_hash_password():
    password = "mysecretpassword"
    hashed = hash_password(password)
    assert hashed != password
    assert isinstance(hashed, str)
    assert len(hashed) > 0

def test_verify_password():
    password = "mysecretpassword"
    hashed = hash_password(password)
    assert verify_password(password, hashed) is True
    assert verify_password("wrongpassword", hashed) is False
