from pydantic import BaseModel, Field

from app.llm.schemas import ModelConfiguration, ModelUsage


class ChatRequest(ModelConfiguration):
    user_request: str
    verified_result: str


class AnalyzeRequest(ModelConfiguration):
    customer_context: str
    chat_content: str = Field(min_length=1)


class StreamEvent(BaseModel):
    type: str
    text: str | None = None
    usage: ModelUsage | None = None
    provider_request_id: str | None = None
    model: str | None = None
