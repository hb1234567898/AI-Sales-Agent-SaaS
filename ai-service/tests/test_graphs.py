import asyncio

from langchain_core.messages import AIMessage

from app.features.agent.schemas import (
    AgentDecision,
    AgentPlanRequest,
    ToolDefinition,
)
from app.features.agent.workflow import create_agent_plan, validate
from app.features.knowledge.schemas import KnowledgeCandidate
from app.features.knowledge.workflow import rank_candidates


def model_configuration() -> dict[str, str]:
    return {"api_key": "test", "base_url": "https://example.invalid/v1", "model": "test-model"}


def test_high_risk_plan_requires_java_approval() -> None:
    request = AgentPlanRequest(
        **model_configuration(),
        user_message="给客户发送邮件",
        available_tools=[ToolDefinition(
            name="email.send",
            description="发送邮件",
            risk_level="HIGH",
            required_fields=["customerId"],
        )],
    )
    result = asyncio.run(validate({
        "request": request,
        "decision": AgentDecision(
            intent="BUSINESS_ACTION",
            tool_name="email.send",
            arguments={"customerId": "customer-1"},
            confidence=0.9,
        ),
    }))
    assert result["requires_approval"] is True
    assert result["validation_errors"] == []


def test_unavailable_tool_is_rejected() -> None:
    request = AgentPlanRequest(**model_configuration(), user_message="删除全部客户")
    result = asyncio.run(validate({
        "request": request,
        "decision": AgentDecision(
            intent="BUSINESS_ACTION",
            tool_name="customer.delete_all",
            confidence=0.8,
        ),
    }))
    assert result["decision"].intent == "CLARIFY"
    assert result["decision"].tool_name is None
    assert result["validation_errors"] == ["计划引用了未授权工具"]


def test_langgraph_planner_returns_validated_plan(monkeypatch) -> None:
    class FakeStructuredModel:
        async def ainvoke(self, _messages):
            return {
                "raw": AIMessage(content="", usage_metadata={
                    "input_tokens": 10,
                    "output_tokens": 5,
                    "total_tokens": 15,
                }),
                "parsed": AgentDecision(
                    intent="BUSINESS_ACTION",
                    tool_name="customer.create",
                    arguments={"name": "云岚科技"},
                    confidence=0.95,
                ),
                "parsing_error": None,
            }

    class FakeModel:
        def with_structured_output(self, *_args, **_kwargs):
            return FakeStructuredModel()

    monkeypatch.setattr("app.features.agent.workflow.create_chat_model", lambda _request: FakeModel())
    request = AgentPlanRequest(
        **model_configuration(),
        user_message="新增客户云岚科技",
        available_tools=[ToolDefinition(
            name="customer.create",
            description="新增客户",
            required_fields=["name"],
        )],
    )
    response = asyncio.run(create_agent_plan(request))
    assert response.decision.tool_name == "customer.create"
    assert response.validation_errors == []
    assert response.usage.total_tokens == 15


def test_knowledge_candidates_are_ranked_and_limited() -> None:
    candidates = [
        KnowledgeCandidate(id="pricing", title="报价流程", content="报价单需要销售经理审批后才能发送给客户。"),
        KnowledgeCandidate(id="leave", title="请假制度", content="员工请假需要在人事系统提交申请。"),
        KnowledgeCandidate(id="email", title="报价邮件", content="发送报价邮件前需要核对客户邮箱和报价附件。"),
    ]
    sources = rank_candidates("报价发送前需要什么审批", candidates, top_k=2)
    assert {source.id for source in sources} == {"pricing", "email"}
    assert len(sources) == 2
    assert sources[0].score >= sources[1].score
