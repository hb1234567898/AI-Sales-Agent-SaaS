import json
import logging
from typing import Any, TypedDict

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from app.features.agent.prompts import PLANNER_SYSTEM_PROMPT
from app.features.agent.schemas import AgentDecision, AgentPlanRequest, AgentPlanResponse
from app.llm.factory import create_chat_model
from app.llm.messages import message_metadata, message_text, message_usage


LOGGER = logging.getLogger(__name__)

JSON_FALLBACK_PROMPT = """
当前模型接口不支持结构化输出协议。请只返回一个 JSON 对象，不要使用 Markdown 代码块或补充说明。
JSON 字段必须为：
- intent: ANSWER、BUSINESS_ACTION、KNOWLEDGE_QUERY 或 CLARIFY
- response: 给用户的文字回复
- tool_name: 候选工具名称；不调用工具时为 null
- arguments: 工具参数对象
- confidence: 0 到 1 之间的数字
""".strip()


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


def _planner_messages(request: AgentPlanRequest, *, json_fallback: bool = False) -> list[Any]:
    system_prompt = PLANNER_SYSTEM_PROMPT
    if json_fallback:
        system_prompt = f"{system_prompt}\n\n{JSON_FALLBACK_PROMPT}"
    return [
        SystemMessage(content=system_prompt),
        HumanMessage(content=(
            f"用户请求：\n{request.user_message}\n\n"
            f"业务上下文：\n{request.business_context}\n\n"
            f"可用工具：\n{_tool_catalog(request)}"
        )),
    ]


def _parse_json_decision(content: Any) -> AgentDecision:
    text = message_text(content).strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < start:
        raise ValueError("model response does not contain a JSON object")
    return AgentDecision.model_validate(json.loads(text[start:end + 1]))


async def _plain_json_plan(model: Any, request: AgentPlanRequest) -> tuple[Any, AgentDecision]:
    raw = await model.ainvoke(_planner_messages(request, json_fallback=True))
    try:
        return raw, _parse_json_decision(raw.content)
    except (json.JSONDecodeError, TypeError, ValueError) as exc:
        LOGGER.warning("AI planner returned invalid JSON: %s", exc)
        return raw, AgentDecision(
            intent="CLARIFY",
            response="我还不能确定要执行的操作，请补充更明确的信息。",
        )


async def plan(state: AgentState) -> AgentState:
    request = state["request"]
    model = create_chat_model(request)
    raw = None
    parsed = None
    try:
        structured_model = model.with_structured_output(AgentDecision, include_raw=True)
        result = await structured_model.ainvoke(_planner_messages(request))
        raw = result.get("raw")
        parsed = result.get("parsed")
        if parsed is None and raw is not None:
            try:
                parsed = _parse_json_decision(raw.content)
            except (json.JSONDecodeError, TypeError, ValueError):
                pass
    except Exception as exc:
        LOGGER.warning(
            "Structured AI planning failed; falling back to plain JSON: %s",
            exc,
        )

    if parsed is None:
        raw, parsed = await _plain_json_plan(model, request)
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
