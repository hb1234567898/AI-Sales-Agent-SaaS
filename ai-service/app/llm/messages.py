from typing import Any

from langchain_core.messages import AIMessage, AIMessageChunk

from app.llm.schemas import ModelUsage


def message_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            item.get("text", "") if isinstance(item, dict) else str(item)
            for item in content
        )
    return str(content or "")


def message_usage(message: AIMessage | AIMessageChunk) -> ModelUsage | None:
    raw = message.usage_metadata
    if not raw:
        return None
    details = raw.get("input_token_details") or {}
    return ModelUsage(
        input_tokens=raw.get("input_tokens", 0),
        output_tokens=raw.get("output_tokens", 0),
        total_tokens=raw.get("total_tokens", 0),
        cached_input_tokens=details.get("cache_read", 0),
    )


def message_metadata(message: AIMessage | AIMessageChunk, fallback_model: str) -> tuple[str | None, str]:
    metadata = message.response_metadata or {}
    return metadata.get("id") or message.id, metadata.get("model_name") or fallback_model
