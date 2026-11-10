from fastapi import APIRouter, Depends

from app.core.security import verify_internal_token
from app.features.model import service
from app.llm.schemas import ModelConfiguration, ModelResponse

router = APIRouter(
    prefix="/v1/model",
    tags=["model"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/test", response_model=ModelResponse)
async def model_test(request: ModelConfiguration) -> ModelResponse:
    return await service.test_connection(request)
