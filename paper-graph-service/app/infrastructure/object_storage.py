from __future__ import annotations

from urllib.parse import urlparse

from minio import Minio

from app.config import Settings


class PaperObjectStorage:
    def __init__(self, settings: Settings) -> None:
        endpoint = urlparse(settings.minio_endpoint)
        self._bucket = settings.minio_bucket
        self._client = Minio(
            endpoint.netloc,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=endpoint.scheme == "https",
        )

    def verify_bucket(self) -> None:
        if not self._client.bucket_exists(self._bucket):
            raise RuntimeError(f"MinIO bucket does not exist: {self._bucket}")

    def put_pdf(self, object_key: str, content: bytes) -> None:
        from io import BytesIO

        self._client.put_object(
            self._bucket,
            object_key,
            BytesIO(content),
            length=len(content),
            content_type="application/pdf",
        )

    def get_bytes(self, object_key: str) -> bytes:
        response = self._client.get_object(self._bucket, object_key)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()

    def remove(self, object_key: str) -> None:
        self._client.remove_object(self._bucket, object_key)

    def stat(self, object_key: str) -> None:
        self._client.stat_object(self._bucket, object_key)
