from langchain_core.messages import HumanMessage, SystemMessage

from app.llm.factory import create_chat_model
from app.llm.messages import message_metadata, message_text, message_usage
from app.llm.schemas import ModelConfiguration, ModelResponse


async def test_connection(request: ModelConfiguration) -> ModelResponse:
    model = create_chat_model(request)
    response = await model.ainvoke([
        SystemMessage(content="你是销售 Agent 的模型连接检查器。不要调用工具，不要补充解释。"),
        HumanMessage(content="请只回复四个汉字：连接成功"),
    ])
    request_id, model_name = message_metadata(response, request.model)
    return ModelResponse(
        content=message_text(response.content),
        usage=message_usage(response),
        provider_request_id=request_id,
        model=model_name,
    )
