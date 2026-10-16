from collections.abc import AsyncIterator

from fastapi.testclient import TestClient

from app.main import app
from app.core.config import settings
from app.features.agent.schemas import AgentDecision, AgentPlanResponse
from app.features.chat.schemas import StreamEvent
from app.llm.schemas import ModelResponse, ModelUsage


def configuration() -> dict[str, str]:
    return {"api_key": "test-key", "base_url": "https://example.invalid/v1", "model": "test-model"}


def test_health() -> None:
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_connection_endpoint(monkeypatch) -> None:
    async def fake_test(_request) -> ModelResponse:
        return ModelResponse(content="连接成功", model="test-model")

    monkeypatch.setattr("app.features.model.service.test_connection", fake_test)
    response = TestClient(app).post("/v1/model/test", json=configuration())
    assert response.status_code == 200
    assert response.json()["content"] == "连接成功"


def test_stream_endpoint_emits_ndjson(monkeypatch) -> None:
    async def fake_stream(_request) -> AsyncIterator[StreamEvent]:
        yield StreamEvent(type="delta", text="你好", model="test-model")
        yield StreamEvent(type="usage", usage=ModelUsage(input_tokens=4, output_tokens=2, total_tokens=6))

    monkeypatch.setattr("app.features.chat.service.stream_assistant_reply", fake_stream)
    payload = configuration() | {"user_request": "测试", "verified_result": "成功"}
    response = TestClient(app).post("/v1/chat/stream", json=payload)
    assert response.status_code == 200
    lines = response.text.strip().splitlines()
    assert '"text": "你好"' in lines[0]
    assert '"total_tokens": 6' in lines[1]


def test_internal_token_is_required_when_configured(monkeypatch) -> None:
    monkeypatch.setattr(settings, "token", "internal-token")
    response = TestClient(app).post("/v1/model/test", json=configuration())
    assert response.status_code == 401


def test_agent_plan_endpoint(monkeypatch) -> None:
    async def fake_plan(_request) -> AgentPlanResponse:
        return AgentPlanResponse(decision=AgentDecision(intent="ANSWER", response="可以"))

    monkeypatch.setattr("app.features.agent.workflow.create_agent_plan", fake_plan)
    payload = configuration() | {"user_message": "你好", "available_tools": []}
    response = TestClient(app).post("/v1/agent/plan", json=payload)
    assert response.status_code == 200
    assert response.json()["decision"]["intent"] == "ANSWER"


def test_knowledge_query_without_candidates_does_not_call_model() -> None:
    payload = configuration() | {"query": "报价怎么审批", "candidates": []}
    response = TestClient(app).post("/v1/knowledge/query", json=payload)
    assert response.status_code == 200
    assert response.json()["sources"] == []
    assert "没有找到" in response.json()["content"]
