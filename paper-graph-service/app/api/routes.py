from __future__ import annotations

from io import BytesIO
from urllib.parse import quote
from uuid import UUID

from fastapi import (
    APIRouter,
    Depends,
    File,
    HTTPException,
    Query,
    Response,
    UploadFile,
    status,
)
from fastapi.responses import StreamingResponse

from app.api.dependencies import paper_graph_service, request_context
from app.api.schemas import CreateGraphRequest
from app.domain.models import RequestContext
from app.services.paper_graph_service import (
    MAX_PDF_FILE_SIZE_BYTES,
    MAX_PDF_FILE_SIZE_MESSAGE,
    PaperGraphService,
)

router = APIRouter(prefix="/api", tags=["paper-graphs"])


@router.post("/paper-graphs", status_code=status.HTTP_201_CREATED)
def create_graph(
    request: CreateGraphRequest,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.create_graph(context, request.name, request.description)


@router.get("/paper-graphs")
def list_graphs(
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> list[dict]:
    return service.list_graphs(context)


@router.get("/paper-graphs/{graph_id}")
def get_graph(
    graph_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.get_graph(context, graph_id)


@router.delete("/paper-graphs/{graph_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_graph(
    graph_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> Response:
    service.delete_graph(context, graph_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post(
    "/paper-graphs/{graph_id}/documents",
    status_code=status.HTTP_201_CREATED,
)
async def upload_document(
    graph_id: UUID,
    file: UploadFile = File(),
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    filename = file.filename
    if filename is None:
        raise ValueError("uploaded PDF filename is required")
    content = await file.read(MAX_PDF_FILE_SIZE_BYTES + 1)
    if len(content) > MAX_PDF_FILE_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail=MAX_PDF_FILE_SIZE_MESSAGE,
        )
    return service.upload_document(context, graph_id, filename, content)


@router.get("/paper-graphs/{graph_id}/documents")
def list_documents(
    graph_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> list[dict]:
    return service.list_documents(context, graph_id)


@router.get("/paper-documents/{document_id}/status")
def document_status(
    document_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.document_status(context, document_id)


@router.post("/paper-documents/{document_id}/rebuild")
def rebuild_document(
    document_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.rebuild_document(context, document_id)


@router.get("/paper-documents/{document_id}/download")
def download_document(
    document_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> StreamingResponse:
    filename, content = service.download_document(context, document_id)
    encoded_filename = quote(filename)
    return StreamingResponse(
        BytesIO(content),
        media_type="application/pdf",
        headers={
            "Content-Disposition": f"inline; filename*=UTF-8''{encoded_filename}",
            "Content-Length": str(len(content)),
        },
    )


@router.delete(
    "/paper-documents/{document_id}", status_code=status.HTTP_204_NO_CONTENT
)
def delete_document(
    document_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> Response:
    service.delete_document(context, document_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/paper-graphs/{graph_id}/visualization")
def visualization(
    graph_id: UUID,
    document_id: UUID | None = Query(default=None, alias="documentId"),
    entity_types: list[str] | None = Query(default=None, alias="entityType"),
    query: str | None = Query(default=None),
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.visualization(
        context,
        graph_id,
        document_id,
        entity_types or [],
        query,
    )


@router.get("/paper-graphs/{graph_id}/nodes/{node_id}")
def node_detail(
    graph_id: UUID,
    node_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.node_detail(context, graph_id, node_id)


@router.get("/paper-graphs/{graph_id}/nodes/{node_id}/neighbors")
def neighbors(
    graph_id: UUID,
    node_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.neighbors(context, graph_id, node_id)


@router.get("/paper-graphs/{graph_id}/edges/{edge_id}/evidence")
def edge_evidence(
    graph_id: UUID,
    edge_id: UUID,
    context: RequestContext = Depends(request_context),
    service: PaperGraphService = Depends(paper_graph_service),
) -> dict:
    return service.edge_evidence(context, graph_id, edge_id)
