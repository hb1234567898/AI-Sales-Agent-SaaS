from typing import Any, Literal

from pydantic import BaseModel, Field

from app.llm.schemas import ModelConfiguration, ModelUsage


class ToolDefinition(BaseModel):
    name: str = Field(min_length=1)
    description: str = ""
    risk_level: Literal["LOW", "MEDIUM", "HIGH"] = "LOW"
    required_fields: list[str] = Field(default_factory=list)


class AgentPlanRequest(ModelConfiguration):
    user_message: str = Field(min_length=1, max_length=4000)
    business_context: dict[str, Any] = Field(default_factory=dict)
    available_tools: list[ToolDefinition] = Field(default_factory=list, max_length=100)


class AgentDecision(BaseModel):
    intent: Literal["ANSWER", "BUSINESS_ACTION", "KNOWLEDGE_QUERY", "CLARIFY"]
    response: str = ""
    tool_name: str | None = None
    arguments: dict[str, Any] = Field(default_factory=dict)
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)


class AgentPlanResponse(BaseModel):
    decision: AgentDecision
    requires_approval: bool = False
    validation_errors: list[str] = Field(default_factory=list)
    usage: ModelUsage | None = None
    provider_request_id: str | None = None
    model: str | None = None
