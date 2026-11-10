from fastapi import APIRouter

from app.api.health import router as health_router
from app.features.agent.router import router as agent_router
from app.features.chat.router import router as chat_router
from app.features.knowledge.router import router as knowledge_router
from app.features.model.router import router as model_router

api_router = APIRouter()
api_router.include_router(health_router)
api_router.include_router(model_router)
api_router.include_router(chat_router)
api_router.include_router(agent_router)
api_router.include_router(knowledge_router)
