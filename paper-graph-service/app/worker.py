from __future__ import annotations

import logging

from app.config import Settings
from app.domain.schema import ComputerPaperGraphSchema
from app.infrastructure.database import PaperGraphDatabase
from app.infrastructure.kafka import BuildEventConsumer
from app.infrastructure.llm_client import GraphExtractionModelClient
from app.infrastructure.object_storage import PaperObjectStorage
from app.infrastructure.pdf_parser import ComputerPaperPdfParser
from app.services.build_service import PaperGraphBuildService

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s - %(message)s",
)
logger = logging.getLogger(__name__)


def main() -> None:
    settings = Settings.from_environment()
    database = PaperGraphDatabase(settings.database_dsn)
    database.verify_schema()
    object_storage = PaperObjectStorage(settings)
    object_storage.verify_bucket()
    consumer = BuildEventConsumer(settings)
    builder = PaperGraphBuildService(
        database=database,
        object_storage=object_storage,
        parser=ComputerPaperPdfParser(),
        model_client=GraphExtractionModelClient(settings),
        schema=ComputerPaperGraphSchema(),
    )
    try:
        for _, message in consumer:
            outcome = builder.build(message)
            if outcome.status == "FAILED":
                logger.error(
                    "Paper graph build recorded FAILED for %s: %s",
                    message.document_id,
                    outcome.error_message,
                )
            consumer.commit()
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
