from typing import Any, Literal

from pydantic import AliasChoices, BaseModel, ConfigDict, Field

from app.llm.schemas import ModelConfiguration, ModelUsage


class ToolDefinition(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    name: str = Field(min_length=1)
    description: str = ""
    risk_level: Literal["LOW", "MEDIUM", "HIGH"] = Field(
        default="LOW",
        validation_alias=AliasChoices("risk_level", "riskLevel"),
    )
    required_fields: list[str] = Field(
        default_factory=list,
        validation_alias=AliasChoices("required_fields", "requiredFields"),
    )


class AgentPlanRequest(ModelConfiguration):
    user_message: str = Field(
        min_length=1,
        max_length=4000,
        validation_alias=AliasChoices("user_message", "userMessage"),
    )
    business_context: dict[str, Any] = Field(
        default_factory=dict,
        validation_alias=AliasChoices("business_context", "businessContext"),
    )
    available_tools: list[ToolDefinition] = Field(
        default_factory=list,
        max_length=100,
        validation_alias=AliasChoices("available_tools", "availableTools"),
    )


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
