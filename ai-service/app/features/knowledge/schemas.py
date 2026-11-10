from typing import Any

from pydantic import BaseModel, Field

from app.llm.schemas import ModelConfiguration, ModelResponse


class KnowledgeCandidate(BaseModel):
    id: str = Field(min_length=1)
    title: str = Field(default="", max_length=500)
    content: str = Field(min_length=1, max_length=20000)
    metadata: dict[str, Any] = Field(default_factory=dict)


class KnowledgeQueryRequest(ModelConfiguration):
    query: str = Field(min_length=1, max_length=2000)
    candidates: list[KnowledgeCandidate] = Field(default_factory=list, max_length=100)
    top_k: int = Field(default=4, ge=1, le=10)


class KnowledgeSource(BaseModel):
    id: str
    title: str
    excerpt: str
    score: float
    metadata: dict[str, Any] = Field(default_factory=dict)


class KnowledgeQueryResponse(ModelResponse):
    sources: list[KnowledgeSource] = Field(default_factory=list)


class DocumentIngestRequest(BaseModel):
    filename: str = Field(min_length=1, max_length=255)
    title: str = Field(min_length=1, max_length=255)
    category: str = Field(default="OTHER", max_length=40)
    content_base64: str = Field(min_length=1, max_length=13981016)


class DocumentChunk(BaseModel):
    chunk_index: int
    page_number: int | None = None
    content: str
    search_terms: str


class DocumentIngestResponse(BaseModel):
    chunks: list[DocumentChunk]
    character_count: int


class SearchTermsRequest(BaseModel):
    query: str = Field(min_length=1, max_length=2000)


class SearchTermsResponse(BaseModel):
    search_query: str
