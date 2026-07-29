from __future__ import annotations

from fastapi.testclient import TestClient

from app.api.dependencies import ApiContainer
from app.config import Settings
from app.main import create_app
from app.services.paper_graph_service import (
    MAX_PDF_FILE_SIZE_BYTES,
    MAX_PDF_FILE_SIZE_MESSAGE,
)


class FakePaperGraphService:
    def list_graphs(self, context):
        return [
            {
                "id": "graph-1",
                "name": "Computer Papers",
                "userId": context.user_id,
                "workspaceId": context.workspace_id,
            }
        ]

    def upload_document(self, context, graph_id, filename, content):
        return {"size": len(content)}


def settings() -> Settings:
    return Settings(
        database_dsn="postgresql://test",
        minio_endpoint="http://127.0.0.1:9000",
        minio_access_key="access",
        minio_secret_key="secret",
        minio_bucket="bucket",
        kafka_bootstrap_servers="127.0.0.1:9092",
        kafka_security_protocol="PLAINTEXT",
        kafka_sasl_mechanism=None,
        kafka_username=None,
        kafka_password=None,
        llm_chat_completions_url="http://127.0.0.1:8001/v1/chat/completions",
        llm_api_key="key",
        llm_model="model",
        internal_api_token="internal-secret",
    )


def test_internal_api_rejects_untrusted_token() -> None:
    app = create_app(ApiContainer(settings(), FakePaperGraphService()))

    with TestClient(app) as client:
        response = client.get(
            "/api/paper-graphs",
            headers={
                "X-Lab-Mind-Internal-Token": "wrong",
                "X-Lab-Mind-User-Id": "42",
                "X-Lab-Mind-Workspace-Id": "workspace-1",
            },
        )

    assert response.status_code == 401
    assert response.json() == {"detail": "invalid internal API token"}


def test_internal_api_uses_java_identity_headers() -> None:
    app = create_app(ApiContainer(settings(), FakePaperGraphService()))

    with TestClient(app) as client:
        response = client.get(
            "/api/paper-graphs",
            headers={
                "X-Lab-Mind-Internal-Token": "internal-secret",
                "X-Lab-Mind-User-Id": "42",
                "X-Lab-Mind-Workspace-Id": "workspace-1",
            },
        )

    assert response.status_code == 200
    assert response.json() == [
        {
            "id": "graph-1",
            "name": "Computer Papers",
            "userId": "42",
            "workspaceId": "workspace-1",
        }
    ]


def test_internal_api_accepts_pdf_at_10_mb_limit() -> None:
    app = create_app(ApiContainer(settings(), FakePaperGraphService()))
    content = b"%PDF-" + b"x" * (MAX_PDF_FILE_SIZE_BYTES - len(b"%PDF-"))

    with TestClient(app) as client:
        response = client.post(
            "/api/paper-graphs/22222222-2222-2222-2222-222222222222/documents",
            headers={
                "X-Lab-Mind-Internal-Token": "internal-secret",
                "X-Lab-Mind-User-Id": "42",
                "X-Lab-Mind-Workspace-Id": "workspace-1",
            },
            files={"file": ("paper.pdf", content, "application/pdf")},
        )

    assert response.status_code == 201
    assert response.json() == {"size": MAX_PDF_FILE_SIZE_BYTES}


def test_internal_api_rejects_pdf_over_10_mb_limit() -> None:
    app = create_app(ApiContainer(settings(), FakePaperGraphService()))
    content = b"%PDF-" + b"x" * (MAX_PDF_FILE_SIZE_BYTES - len(b"%PDF-") + 1)

    with TestClient(app) as client:
        response = client.post(
            "/api/paper-graphs/22222222-2222-2222-2222-222222222222/documents",
            headers={
                "X-Lab-Mind-Internal-Token": "internal-secret",
                "X-Lab-Mind-User-Id": "42",
                "X-Lab-Mind-Workspace-Id": "workspace-1",
            },
            files={"file": ("paper.pdf", content, "application/pdf")},
        )

    assert response.status_code == 413
    assert response.json() == {"detail": MAX_PDF_FILE_SIZE_MESSAGE}
