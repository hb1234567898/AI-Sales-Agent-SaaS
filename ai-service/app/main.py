import logging

from fastapi import FastAPI, Request
from fastapi.exception_handlers import request_validation_exception_handler
from fastapi.exceptions import RequestValidationError

from app.api.router import api_router

LOGGER = logging.getLogger(__name__)

app = FastAPI(title="AI Sales Agent AI Service", version="0.1.0")
app.include_router(api_router)


@app.exception_handler(RequestValidationError)
async def log_request_validation_error(request: Request, exception: RequestValidationError):
    errors = [
        {
            "location": ".".join(str(part) for part in error.get("loc", [])),
            "message": error.get("msg", "validation failed"),
            "type": error.get("type", "validation_error"),
        }
        for error in exception.errors()
    ]
    LOGGER.warning(
        "Request validation failed for %s %s: %s",
        request.method,
        request.url.path,
        errors,
    )
    return await request_validation_exception_handler(request, exception)
