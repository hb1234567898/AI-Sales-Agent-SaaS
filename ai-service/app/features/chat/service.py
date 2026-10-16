from collections.abc import AsyncIterator

from langchain_core.messages import HumanMessage, SystemMessage

from app.features.chat.prompts import ANALYSIS_SYSTEM_PROMPT, ASSISTANT_SYSTEM_PROMPT
from app.features.chat.schemas import AnalyzeRequest, ChatRequest, StreamEvent
from app.llm.factory import create_chat_model
from app.llm.messages import message_metadata, message_text, message_usage
from app.llm.schemas import ModelResponse


async def stream_assistant_reply(request: ChatRequest) -> AsyncIterator[StreamEvent]:
    model = create_chat_model(request, stream_usage=True)
    messages = [
        SystemMessage(content=ASSISTANT_SYSTEM_PROMPT),
        HumanMessage(content=f"用户请求：\n{request.user_request}\n已核实的业务结果：\n{request.verified_result}"),
    ]
    async for chunk in model.astream(messages):
        text = message_text(chunk.content)
        usage = message_usage(chunk)
        request_id, model_name = message_metadata(chunk, request.model)
        if text:
            yield StreamEvent(type="delta", text=text, provider_request_id=request_id, model=model_name)
        if usage:
            yield StreamEvent(
                type="usage",
                usage=usage,
                provider_request_id=request_id,
                model=model_name,
            )


async def analyze_chat(request: AnalyzeRequest) -> ModelResponse:
    model = create_chat_model(request)
    response = await model.ainvoke([
        SystemMessage(content=ANALYSIS_SYSTEM_PROMPT),
        HumanMessage(content=f"客户信息：\n{request.customer_context}\n\n待分析聊天原文：\n{request.chat_content}"),
    ])
    request_id, model_name = message_metadata(response, request.model)
    return ModelResponse(
        content=message_text(response.content),
        usage=message_usage(response),
        provider_request_id=request_id,
        model=model_name,
    )
