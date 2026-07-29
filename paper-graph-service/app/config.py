from __future__ import annotations

import os
from dataclasses import dataclass
from urllib.parse import urlparse


class ConfigurationError(RuntimeError):
    pass


def _required(name: str) -> str:
    value = os.environ.get(name)
    if value is None or not value.strip():
        raise ConfigurationError(f"{name} must be configured")
    return value.strip()


@dataclass(frozen=True)
class Settings:
    database_dsn: str
    minio_endpoint: str
    minio_access_key: str
    minio_secret_key: str
    minio_bucket: str
    kafka_bootstrap_servers: str
    kafka_security_protocol: str
    kafka_sasl_mechanism: str | None
    kafka_username: str | None
    kafka_password: str | None
    llm_chat_completions_url: str
    llm_api_key: str
    llm_model: str
    internal_api_token: str

    @classmethod
    def from_environment(cls) -> "Settings":
        security_protocol = _required("LAB_MIND_KAFKA_SECURITY_PROTOCOL").upper()
        sasl_mechanism: str | None = None
        username: str | None = None
        password: str | None = None
        if security_protocol.startswith("SASL_"):
            sasl_mechanism = _required("LAB_MIND_KAFKA_SASL_MECHANISM")
            username = _required("LAB_MIND_KAFKA_USERNAME")
            password = _required("LAB_MIND_KAFKA_PASSWORD")

        settings = cls(
            database_dsn=_required("LAB_MIND_PAPER_GRAPH_POSTGRES_DSN"),
            minio_endpoint=_required("LAB_MIND_MINIO_ENDPOINT"),
            minio_access_key=_required("LAB_MIND_MINIO_ACCESS_KEY"),
            minio_secret_key=_required("LAB_MIND_MINIO_SECRET_KEY"),
            minio_bucket=_required("LAB_MIND_MINIO_BUCKET"),
            kafka_bootstrap_servers=_required("LAB_MIND_KAFKA_BOOTSTRAP_SERVERS"),
            kafka_security_protocol=security_protocol,
            kafka_sasl_mechanism=sasl_mechanism,
            kafka_username=username,
            kafka_password=password,
            llm_chat_completions_url=_required(
                "LAB_MIND_PAPER_GRAPH_LLM_CHAT_COMPLETIONS_URL"
            ),
            llm_api_key=_required("LAB_MIND_PAPER_GRAPH_LLM_API_KEY"),
            llm_model=_required("LAB_MIND_PAPER_GRAPH_LLM_MODEL"),
            internal_api_token=_required("LAB_MIND_PAPER_GRAPH_INTERNAL_API_TOKEN"),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        minio_url = urlparse(self.minio_endpoint)
        if minio_url.scheme not in {"http", "https"} or not minio_url.netloc:
            raise ConfigurationError(
                "LAB_MIND_MINIO_ENDPOINT must be an absolute http(s) URL"
            )
        if minio_url.path not in {"", "/"} or minio_url.query or minio_url.fragment:
            raise ConfigurationError(
                "LAB_MIND_MINIO_ENDPOINT must not contain a path, query, or fragment"
            )
        llm_url = urlparse(self.llm_chat_completions_url)
        if llm_url.scheme not in {"http", "https"} or not llm_url.netloc:
            raise ConfigurationError(
                "LAB_MIND_PAPER_GRAPH_LLM_CHAT_COMPLETIONS_URL must be an absolute http(s) URL"
            )
