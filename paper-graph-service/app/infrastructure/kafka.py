from __future__ import annotations

import json
from typing import Any, Iterator

from kafka import KafkaConsumer, KafkaProducer
from kafka.admin import KafkaAdminClient, NewTopic
from kafka.errors import TopicAlreadyExistsError, for_code

from app.config import Settings
from app.domain.models import BuildMessage

PAPER_GRAPH_BUILD_TOPIC = "paper.graph.build"
PAPER_GRAPH_CONSUMER_GROUP = "labmind-paper-graph"
KAFKA_SEND_TIMEOUT_SECONDS = 10


def _connection_options(settings: Settings) -> dict[str, Any]:
    options: dict[str, Any] = {
        "bootstrap_servers": settings.kafka_bootstrap_servers,
        "security_protocol": settings.kafka_security_protocol,
    }
    if settings.kafka_security_protocol.startswith("SASL_"):
        options.update(
            {
                "sasl_mechanism": settings.kafka_sasl_mechanism,
                "sasl_plain_username": settings.kafka_username,
                "sasl_plain_password": settings.kafka_password,
            }
        )
    return options


class BuildEventProducer:
    def __init__(self, settings: Settings) -> None:
        admin = KafkaAdminClient(**_connection_options(settings))
        try:
            response = admin.create_topics(
                [NewTopic(PAPER_GRAPH_BUILD_TOPIC, num_partitions=1, replication_factor=1)]
            )
            for topic, error_code, error_message in response.topic_error_codes:
                if error_code in {0, TopicAlreadyExistsError.errno}:
                    continue
                error_type = for_code(error_code)
                raise error_type(
                    f"failed to create Kafka topic {topic}: {error_message}"
                )
        finally:
            admin.close()
        self._producer = KafkaProducer(
            **_connection_options(settings),
            key_serializer=lambda value: value.encode("utf-8"),
            value_serializer=lambda value: json.dumps(
                value, ensure_ascii=False, separators=(",", ":")
            ).encode("utf-8"),
            acks="all",
            retries=0,
        )

    def publish(self, message: BuildMessage) -> None:
        payload = {
            "graph_id": str(message.graph_id),
            "document_id": str(message.document_id),
            "version": message.version,
            "object_key": message.object_key,
            "extractor_version": message.extractor_version,
        }
        self._producer.send(
            PAPER_GRAPH_BUILD_TOPIC,
            key=str(message.document_id),
            value=payload,
        ).get(timeout=KAFKA_SEND_TIMEOUT_SECONDS)

    def close(self) -> None:
        self._producer.flush(timeout=KAFKA_SEND_TIMEOUT_SECONDS)
        self._producer.close(timeout=KAFKA_SEND_TIMEOUT_SECONDS)


class BuildEventConsumer:
    def __init__(self, settings: Settings) -> None:
        self._consumer = KafkaConsumer(
            PAPER_GRAPH_BUILD_TOPIC,
            **_connection_options(settings),
            group_id=PAPER_GRAPH_CONSUMER_GROUP,
            enable_auto_commit=False,
            auto_offset_reset="earliest",
            key_deserializer=lambda value: value.decode("utf-8"),
            value_deserializer=lambda value: json.loads(value.decode("utf-8")),
        )

    def __iter__(self) -> Iterator[tuple[Any, BuildMessage]]:
        for record in self._consumer:
            value = record.value
            if not isinstance(value, dict) or set(value) != {
                "graph_id",
                "document_id",
                "version",
                "object_key",
                "extractor_version",
            }:
                raise ValueError("paper.graph.build message has an invalid shape")
            from uuid import UUID

            yield record, BuildMessage(
                graph_id=UUID(value["graph_id"]),
                document_id=UUID(value["document_id"]),
                version=value["version"],
                object_key=value["object_key"],
                extractor_version=value["extractor_version"],
            )

    def commit(self) -> None:
        self._consumer.commit()

    def close(self) -> None:
        self._consumer.close()
