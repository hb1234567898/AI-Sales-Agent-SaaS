from fastapi import Header, HTTPException, status

from app.core.config import settings


def verify_internal_token(authorization: str | None = Header(default=None)) -> None:
    if not settings.token:
        return
    if authorization != f"Bearer {settings.token}":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid service token")
