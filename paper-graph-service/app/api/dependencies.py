from __future__ import annotations

import hmac
from dataclasses import dataclass

from fastapi import Header, HTTPException, Request, status

from app.config import Settings
from app.domain.models import RequestContext
from app.services.paper_graph_service import PaperGraphService


@dataclass(frozen=True)
class ApiContainer:
    settings: Settings
    service: PaperGraphService


def get_container(request: Request) -> ApiContainer:
    container = getattr(request.app.state, "container", None)
    if not isinstance(container, ApiContainer):
        raise RuntimeError("paper graph API container is not initialized")
    return container


def request_context(
    request: Request,
    internal_token: str = Header(alias="X-Lab-Mind-Internal-Token"),
    user_id: str = Header(alias="X-Lab-Mind-User-Id"),
    workspace_id: str = Header(alias="X-Lab-Mind-Workspace-Id"),
) -> RequestContext:
    container = get_container(request)
    if not hmac.compare_digest(internal_token, container.settings.internal_api_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid internal API token",
        )
    if not user_id.strip() or not workspace_id.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="user and workspace headers must not be blank",
        )
    return RequestContext(user_id=user_id.strip(), workspace_id=workspace_id.strip())


def paper_graph_service(request: Request) -> PaperGraphService:
    return get_container(request).service
