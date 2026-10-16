from typing import Any, TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from app.features.agent.prompts import PLANNER_SYSTEM_PROMPT
from app.features.agent.schemas import AgentDecision, AgentPlanRequest, AgentPlanResponse
from app.llm.factory import create_chat_model
from app.llm.messages import message_metadata, message_usage


class AgentState(TypedDict, total=False):
    request: AgentPlanRequest
    decision: AgentDecision
    usage: Any
    provider_request_id: str | None
    model: str | None
    requires_approval: bool
    validation_errors: list[str]


def _tool_catalog(request: AgentPlanRequest) -> str:
    if not request.available_tools:
        return "当前没有可调用工具。"
    return "\n".join(
        f"- {tool.name} | 风险={tool.risk_level} | 必填={','.join(tool.required_fields) or '无'} | {tool.description}"
        for tool in request.available_tools
    )


async def plan(state: AgentState) -> AgentState:
    request = state["request"]
    model = create_chat_model(request)
    structured_model = model.with_structured_output(AgentDecision, include_raw=True)
    result = await structured_model.ainvoke([
        SystemMessage(content=PLANNER_SYSTEM_PROMPT),
        HumanMessage(content=(
            f"用户请求：\n{request.user_message}\n\n"
            f"业务上下文：\n{request.business_context}\n\n"
            f"可用工具：\n{_tool_catalog(request)}"
        )),
    ])
    raw = result.get("raw")
    parsed = result.get("parsed")
    if parsed is None:
        parsed = AgentDecision(intent="CLARIFY", response="我还不能确定要执行的操作，请补充更明确的信息。")
    request_id, model_name = message_metadata(raw, request.model) if raw is not None else (None, request.model)
    return {
        **state,
        "decision": parsed,
        "usage": message_usage(raw) if raw is not None else None,
        "provider_request_id": request_id,
        "model": model_name,
    }


async def validate(state: AgentState) -> AgentState:
    request = state["request"]
    decision = state["decision"]
    tools = {tool.name: tool for tool in request.available_tools}
    errors: list[str] = []
    requires_approval = False

    if decision.intent == "BUSINESS_ACTION":
        tool = tools.get(decision.tool_name or "")
        if tool is None:
            errors.append("计划引用了未授权工具")
            decision = decision.model_copy(update={"intent": "CLARIFY", "tool_name": None, "arguments": {}})
        else:
            missing = [field for field in tool.required_fields if not decision.arguments.get(field)]
            if missing:
                errors.append("缺少必填参数: " + ", ".join(missing))
            requires_approval = tool.risk_level == "HIGH"
    elif decision.tool_name:
        errors.append("非业务动作不能携带工具")
        decision = decision.model_copy(update={"tool_name": None, "arguments": {}})

    return {
        **state,
        "decision": decision,
        "requires_approval": requires_approval,
        "validation_errors": errors,
    }


def build_agent_graph():
    graph = StateGraph(AgentState)
    graph.add_node("plan", plan)
    graph.add_node("validate", validate)
    graph.add_edge(START, "plan")
    graph.add_edge("plan", "validate")
    graph.add_edge("validate", END)
    return graph.compile()


agent_graph = build_agent_graph()


async def create_agent_plan(request: AgentPlanRequest) -> AgentPlanResponse:
    result = await agent_graph.ainvoke({"request": request})
    return AgentPlanResponse(
        decision=result["decision"],
        requires_approval=result.get("requires_approval", False),
        validation_errors=result.get("validation_errors", []),
        usage=result.get("usage"),
        provider_request_id=result.get("provider_request_id"),
        model=result.get("model"),
    )
