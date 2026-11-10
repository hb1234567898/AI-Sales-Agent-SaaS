from fastapi import APIRouter, Depends

from app.core.security import verify_internal_token
from app.features.knowledge import workflow
from app.features.knowledge.schemas import KnowledgeQueryRequest, KnowledgeQueryResponse
from app.features.knowledge.schemas import DocumentIngestRequest, DocumentIngestResponse, SearchTermsRequest, SearchTermsResponse
from app.features.knowledge.documents import ingest_document, search_query

router = APIRouter(
    prefix="/v1/knowledge",
    tags=["knowledge"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/query", response_model=KnowledgeQueryResponse)
async def knowledge_query(request: KnowledgeQueryRequest) -> KnowledgeQueryResponse:
    return await workflow.answer_from_knowledge(request)


@router.post("/ingest", response_model=DocumentIngestResponse)
def knowledge_ingest(request: DocumentIngestRequest) -> DocumentIngestResponse:
    from fastapi import HTTPException
    try:
        return ingest_document(request)
    except ValueError as exception:
        raise HTTPException(status_code=400, detail=str(exception)) from exception
    except Exception as exception:
        raise HTTPException(status_code=400, detail="文件无法解析，请确认格式和内容有效") from exception


@router.post("/terms", response_model=SearchTermsResponse)
def knowledge_terms(request: SearchTermsRequest) -> SearchTermsResponse:
    return SearchTermsResponse(search_query=search_query(request.query))
