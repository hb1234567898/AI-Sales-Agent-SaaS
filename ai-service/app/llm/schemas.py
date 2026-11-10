from pydantic import AliasChoices, BaseModel, ConfigDict, Field


class ModelConfiguration(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    api_key: str = Field(min_length=1, validation_alias=AliasChoices("api_key", "apiKey"))
    base_url: str = Field(min_length=1, validation_alias=AliasChoices("base_url", "baseUrl"))
    model: str = Field(min_length=1)


class ModelUsage(BaseModel):
    input_tokens: int = 0
    output_tokens: int = 0
    total_tokens: int = 0
    cached_input_tokens: int = 0


class ModelResponse(BaseModel):
    content: str
    usage: ModelUsage | None = None
    provider_request_id: str | None = None
    model: str | None = None
