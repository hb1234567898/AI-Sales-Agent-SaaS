import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.core.security import verify_internal_token
from app.features.chat import service
from app.features.chat.schemas import AnalyzeRequest, ChatRequest
from app.llm.schemas import ModelResponse

router = APIRouter(
    prefix="/v1/chat",
    tags=["chat"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/analyze", response_model=ModelResponse)
async def chat_analyze(request: AnalyzeRequest) -> ModelResponse:
    return await service.analyze_chat(request)


@router.post("/stream")
async def chat_stream(request: ChatRequest) -> StreamingResponse:
    async def ndjson() -> AsyncIterator[str]:
        async for event in service.stream_assistant_reply(request):
            yield json.dumps(event.model_dump(exclude_none=True), ensure_ascii=False) + "\n"

    return StreamingResponse(ndjson(), media_type="application/x-ndjson")
