from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from app.api.dependencies import ApiContainer
from app.api.routes import router
from app.config import Settings
from app.infrastructure.database import (
    PaperGraphDatabase,
    RecordNotFoundError,
    StateConflictError,
)
from app.infrastructure.kafka import BuildEventProducer
from app.infrastructure.object_storage import PaperObjectStorage
from app.services.paper_graph_service import PaperGraphService


def create_app(container: ApiContainer | None = None) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        active_container = container
        producer: BuildEventProducer | None = None
        if active_container is None:
            settings = Settings.from_environment()
            database = PaperGraphDatabase(settings.database_dsn)
            database.verify_schema()
            object_storage = PaperObjectStorage(settings)
            object_storage.verify_bucket()
            producer = BuildEventProducer(settings)
            active_container = ApiContainer(
                settings=settings,
                service=PaperGraphService(database, object_storage, producer),
            )
        app.state.container = active_container
        try:
            yield
        finally:
            if producer is not None:
                producer.close()

    app = FastAPI(
        title="LabMind Paper Graph API",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.include_router(router)

    @app.exception_handler(RecordNotFoundError)
    async def handle_not_found(
        request: Request, error: RecordNotFoundError
    ) -> JSONResponse:
        return JSONResponse(status_code=status.HTTP_404_NOT_FOUND, content={"detail": str(error)})

    @app.exception_handler(StateConflictError)
    async def handle_conflict(
        request: Request, error: StateConflictError
    ) -> JSONResponse:
        return JSONResponse(status_code=status.HTTP_409_CONFLICT, content={"detail": str(error)})

    @app.exception_handler(ValueError)
    async def handle_invalid_input(request: Request, error: ValueError) -> JSONResponse:
        return JSONResponse(status_code=status.HTTP_400_BAD_REQUEST, content={"detail": str(error)})

    return app


app = create_app()
