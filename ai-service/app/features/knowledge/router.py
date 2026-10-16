from fastapi import APIRouter, Depends

from app.core.security import verify_internal_token
from app.features.knowledge import workflow
from app.features.knowledge.schemas import KnowledgeQueryRequest, KnowledgeQueryResponse

router = APIRouter(
    prefix="/v1/knowledge",
    tags=["knowledge"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/query", response_model=KnowledgeQueryResponse)
async def knowledge_query(request: KnowledgeQueryRequest) -> KnowledgeQueryResponse:
    return await workflow.answer_from_knowledge(request)
