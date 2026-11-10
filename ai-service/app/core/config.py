from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AI_SERVICE_", extra="ignore")

    token: str = ""
    request_timeout_seconds: float = 120.0


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
