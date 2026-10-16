import math
import re
from typing import Any, TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from app.features.knowledge.prompts import KNOWLEDGE_SYSTEM_PROMPT
from app.features.knowledge.schemas import (
    KnowledgeCandidate,
    KnowledgeQueryRequest,
    KnowledgeQueryResponse,
    KnowledgeSource,
)
from app.llm.factory import create_chat_model
from app.llm.messages import message_metadata, message_text, message_usage


class KnowledgeState(TypedDict, total=False):
    request: KnowledgeQueryRequest
    sources: list[KnowledgeSource]
    response: Any


def _terms(value: str) -> set[str]:
    normalized = re.sub(r"\s+", "", value.lower())
    latin = set(re.findall(r"[a-z0-9_]{2,}", normalized))
    chinese = {normalized[index:index + 2] for index in range(max(0, len(normalized) - 1))}
    return latin | chinese


def rank_candidates(query: str, candidates: list[KnowledgeCandidate], top_k: int) -> list[KnowledgeSource]:
    query_terms = _terms(query)
    ranked: list[KnowledgeSource] = []
    for candidate in candidates:
        candidate_terms = _terms(candidate.title + candidate.content)
        overlap = len(query_terms & candidate_terms)
        score = overlap / math.sqrt(max(1, len(query_terms)) * max(1, len(candidate_terms)))
        if score > 0:
            ranked.append(KnowledgeSource(
                id=candidate.id,
                title=candidate.title,
                excerpt=candidate.content[:1200],
                score=round(score, 6),
                metadata=candidate.metadata,
            ))
    return sorted(ranked, key=lambda item: item.score, reverse=True)[:top_k]


async def retrieve(state: KnowledgeState) -> KnowledgeState:
    request = state["request"]
    return {**state, "sources": rank_candidates(request.query, request.candidates, request.top_k)}


async def generate(state: KnowledgeState) -> KnowledgeState:
    request = state["request"]
    sources = state["sources"]
    if not sources:
        return {**state, "response": None}
    context = "\n\n".join(
        f"[资料 {index + 1} | {source.id} | {source.title}]\n{source.excerpt}"
        for index, source in enumerate(sources)
    )
    model = create_chat_model(request)
    response = await model.ainvoke([
        SystemMessage(content=KNOWLEDGE_SYSTEM_PROMPT),
        HumanMessage(content=f"问题：\n{request.query}\n\n可用资料：\n{context}"),
    ])
    return {**state, "response": response}


def build_knowledge_graph():
    graph = StateGraph(KnowledgeState)
    graph.add_node("retrieve", retrieve)
    graph.add_node("generate", generate)
    graph.add_edge(START, "retrieve")
    graph.add_edge("retrieve", "generate")
    graph.add_edge("generate", END)
    return graph.compile()


knowledge_graph = build_knowledge_graph()


async def answer_from_knowledge(request: KnowledgeQueryRequest) -> KnowledgeQueryResponse:
    result = await knowledge_graph.ainvoke({"request": request})
    response = result.get("response")
    if response is None:
        return KnowledgeQueryResponse(
            content="当前知识库中没有找到足够相关的资料。",
            model=request.model,
            sources=[],
        )
    request_id, model_name = message_metadata(response, request.model)
    return KnowledgeQueryResponse(
        content=message_text(response.content),
        usage=message_usage(response),
        provider_request_id=request_id,
        model=model_name,
        sources=result["sources"],
    )
