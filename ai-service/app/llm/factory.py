from langchain_openai import ChatOpenAI

from app.core.config import settings
from app.llm.schemas import ModelConfiguration


def create_chat_model(configuration: ModelConfiguration, *, stream_usage: bool = False) -> ChatOpenAI:
    return ChatOpenAI(
        api_key=configuration.api_key,
        base_url=configuration.base_url,
        model=configuration.model,
        temperature=0.2,
        stream_usage=stream_usage,
        timeout=settings.request_timeout_seconds,
        max_retries=1,
    )
