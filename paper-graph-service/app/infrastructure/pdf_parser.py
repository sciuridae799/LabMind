from __future__ import annotations

import re
from dataclasses import dataclass
from uuid import uuid4

import fitz

from app.domain.models import ParsedChunk

MAX_CHUNK_CHARACTERS = 6_000
CHUNK_OVERLAP_CHARACTERS = 400

_NUMBERED_HEADING = re.compile(
    r"^(?:\d+(?:\.\d+)*|[IVX]+)[.)]?\s+[A-Z][^.!?]{0,100}$",
    re.IGNORECASE,
)
_NAMED_HEADING = re.compile(
    r"^(abstract|introduction|related work|background|methodology|methods?|approach|"
    r"experiments?|experimental setup|results?|discussion|limitations?|conclusion|"
    r"conclusions|references)$",
    re.IGNORECASE,
)


class PdfParsingError(ValueError):
    pass


@dataclass(frozen=True)
class _SectionText:
    page_number: int
    section_name: str
    text: str


class ComputerPaperPdfParser:
    def parse(self, content: bytes) -> list[ParsedChunk]:
        if not content.startswith(b"%PDF-"):
            raise PdfParsingError("uploaded file is not a PDF")
        try:
            document = fitz.open(stream=content, filetype="pdf")
        except Exception as error:
            raise PdfParsingError("failed to open PDF") from error
        try:
            sections = self._extract_sections(document)
        finally:
            document.close()
        if not sections:
            raise PdfParsingError("PDF contains no extractable text")

        chunks: list[ParsedChunk] = []
        for section in sections:
            for chunk_text in self._split_text(section.text):
                chunks.append(
                    ParsedChunk(
                        id=uuid4(),
                        index=len(chunks),
                        page_number=section.page_number,
                        section_name=section.section_name,
                        text=chunk_text,
                    )
                )
        return chunks

    def _extract_sections(self, document: fitz.Document) -> list[_SectionText]:
        sections: list[_SectionText] = []
        current_section = "Document"
        for page_index, page in enumerate(document):
            blocks = sorted(page.get_text("blocks"), key=lambda block: (block[1], block[0]))
            section_parts: list[str] = []
            section_name = current_section
            for block in blocks:
                block_text = self._normalize_block_text(str(block[4]))
                if not block_text:
                    continue
                if self._is_heading(block_text):
                    self._append_section(
                        sections,
                        page_index + 1,
                        section_name,
                        section_parts,
                    )
                    section_parts = []
                    section_name = block_text
                    current_section = block_text
                    continue
                section_parts.append(block_text)
            self._append_section(
                sections,
                page_index + 1,
                section_name,
                section_parts,
            )
        return sections

    @staticmethod
    def _normalize_block_text(text: str) -> str:
        lines = [re.sub(r"\s+", " ", line).strip() for line in text.splitlines()]
        return " ".join(line for line in lines if line)

    @staticmethod
    def _is_heading(text: str) -> bool:
        if len(text) > 120:
            return False
        return bool(_NAMED_HEADING.fullmatch(text) or _NUMBERED_HEADING.fullmatch(text))

    @staticmethod
    def _append_section(
        sections: list[_SectionText],
        page_number: int,
        section_name: str,
        parts: list[str],
    ) -> None:
        text = "\n\n".join(parts).strip()
        if text:
            sections.append(
                _SectionText(
                    page_number=page_number,
                    section_name=section_name,
                    text=text,
                )
            )

    @staticmethod
    def _split_text(text: str) -> list[str]:
        if len(text) <= MAX_CHUNK_CHARACTERS:
            return [text]
        chunks: list[str] = []
        start = 0
        while start < len(text):
            maximum_end = min(start + MAX_CHUNK_CHARACTERS, len(text))
            end = maximum_end
            if maximum_end < len(text):
                boundary = max(
                    text.rfind("\n\n", start, maximum_end),
                    text.rfind(". ", start, maximum_end),
                    text.rfind("。", start, maximum_end),
                )
                if boundary > start + MAX_CHUNK_CHARACTERS // 2:
                    end = boundary + (1 if text[boundary] == "。" else 2)
            chunk = text[start:end].strip()
            if not chunk:
                raise PdfParsingError("PDF chunking produced an empty chunk")
            chunks.append(chunk)
            if end == len(text):
                break
            next_start = end - CHUNK_OVERLAP_CHARACTERS
            if next_start <= start:
                raise PdfParsingError("PDF chunking did not advance")
            start = next_start
        return chunks
