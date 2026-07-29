from __future__ import annotations

from app.config import Settings
from app.infrastructure.llm_client import GraphExtractionModelClient


def settings() -> Settings:
    return Settings(
        database_dsn="postgresql://user:password@localhost/database",
        minio_endpoint="http://localhost:9000",
        minio_access_key="access-key",
        minio_secret_key="secret-key",
        minio_bucket="bucket",
        kafka_bootstrap_servers="localhost:9092",
        kafka_security_protocol="PLAINTEXT",
        kafka_sasl_mechanism=None,
        kafka_username=None,
        kafka_password=None,
        llm_chat_completions_url="https://example.com/chat/completions",
        llm_api_key="api-key",
        llm_model="test-model",
        internal_api_token="internal-token",
    )


def test_requests_native_json_output(monkeypatch) -> None:
    request: dict = {}

    class Response:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict:
            return {
                "choices": [
                    {"message": {"content": '{"PROPOSES":[]}'}}
                ]
            }

    def post(url, **kwargs):
        request["url"] = url
        request.update(kwargs)
        return Response()

    monkeypatch.setattr("app.infrastructure.llm_client.httpx.post", post)

    content = GraphExtractionModelClient(settings()).extract("Return strict JSON.")

    assert content == '{"PROPOSES":[]}'
    assert request["json"]["response_format"] == {"type": "json_object"}
