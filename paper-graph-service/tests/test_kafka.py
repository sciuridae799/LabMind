from __future__ import annotations

from types import SimpleNamespace
from uuid import UUID

from kafka.errors import TopicAlreadyExistsError

from app.config import Settings
from app.domain.models import BuildMessage
from app.infrastructure import kafka as kafka_module


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


def test_existing_topic_is_accepted_and_build_event_is_exact(monkeypatch) -> None:
    sent: dict = {}

    class FakeAdmin:
        def __init__(self, **options) -> None:
            assert options["bootstrap_servers"] == "127.0.0.1:9092"

        def create_topics(self, topics):
            assert topics[0].name == kafka_module.PAPER_GRAPH_BUILD_TOPIC
            return SimpleNamespace(
                topic_error_codes=[
                    (
                        kafka_module.PAPER_GRAPH_BUILD_TOPIC,
                        TopicAlreadyExistsError.errno,
                        "topic already exists",
                    )
                ]
            )

        def close(self) -> None:
            sent["admin_closed"] = True

    class FakeFuture:
        def get(self, timeout: int) -> None:
            sent["timeout"] = timeout

    class FakeProducer:
        def __init__(self, **options) -> None:
            self._key_serializer = options["key_serializer"]
            self._value_serializer = options["value_serializer"]

        def send(self, topic, key, value):
            sent["topic"] = topic
            sent["key"] = self._key_serializer(key)
            sent["value"] = self._value_serializer(value)
            return FakeFuture()

    monkeypatch.setattr(kafka_module, "KafkaAdminClient", FakeAdmin)
    monkeypatch.setattr(kafka_module, "KafkaProducer", FakeProducer)

    producer = kafka_module.BuildEventProducer(settings())
    producer.publish(
        BuildMessage(
            graph_id=UUID("11111111-1111-1111-1111-111111111111"),
            document_id=UUID("22222222-2222-2222-2222-222222222222"),
            version=3,
            object_key=(
                "paper-graph/42/22222222-2222-2222-2222-222222222222/3/source.pdf"
            ),
            extractor_version="computer-paper-v1",
        )
    )

    assert sent == {
        "admin_closed": True,
        "topic": "paper.graph.build",
        "key": b"22222222-2222-2222-2222-222222222222",
        "value": (
            b'{"graph_id":"11111111-1111-1111-1111-111111111111",'
            b'"document_id":"22222222-2222-2222-2222-222222222222",'
            b'"version":3,"object_key":"paper-graph/42/'
            b'22222222-2222-2222-2222-222222222222/3/source.pdf",'
            b'"extractor_version":"computer-paper-v1"}'
        ),
        "timeout": kafka_module.KAFKA_SEND_TIMEOUT_SECONDS,
    }
