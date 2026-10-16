from fastapi import APIRouter, Depends

from app.core.security import verify_internal_token
from app.features.agent import workflow
from app.features.agent.schemas import AgentPlanRequest, AgentPlanResponse

router = APIRouter(
    prefix="/v1/agent",
    tags=["agent"],
    dependencies=[Depends(verify_internal_token)],
)


@router.post("/plan", response_model=AgentPlanResponse)
async def agent_plan(request: AgentPlanRequest) -> AgentPlanResponse:
    return await workflow.create_agent_plan(request)
