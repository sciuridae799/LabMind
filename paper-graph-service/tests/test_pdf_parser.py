from __future__ import annotations

import fitz
import pytest

from app.infrastructure.pdf_parser import (
    CHUNK_OVERLAP_CHARACTERS,
    MAX_CHUNK_CHARACTERS,
    ComputerPaperPdfParser,
    PdfParsingError,
)


def create_computer_paper_pdf() -> bytes:
    document = fitz.open()
    first_page = document.new_page()
    first_page.insert_text((72, 72), "Abstract", fontsize=15)
    first_page.insert_text(
        (72, 120),
        "We propose GraphNet for code representation learning.",
        fontsize=11,
    )
    second_page = document.new_page()
    second_page.insert_text((72, 72), "2 Experiments", fontsize=15)
    second_page.insert_text(
        (72, 120),
        "GraphNet achieves 91.2 accuracy on CodeSearchNet.",
        fontsize=11,
    )
    content = document.tobytes()
    document.close()
    return content


def test_preserves_page_and_section_metadata() -> None:
    chunks = ComputerPaperPdfParser().parse(create_computer_paper_pdf())

    assert [(chunk.page_number, chunk.section_name) for chunk in chunks] == [
        (1, "Abstract"),
        (2, "2 Experiments"),
    ]
    assert "GraphNet" in chunks[0].text
    assert "CodeSearchNet" in chunks[1].text
    assert [chunk.index for chunk in chunks] == [0, 1]


def test_rejects_non_pdf_bytes() -> None:
    with pytest.raises(PdfParsingError, match="not a PDF"):
        ComputerPaperPdfParser().parse(b"plain text")


def test_pdf_layout_line_breaks_become_spaces() -> None:
    text = ComputerPaperPdfParser._normalize_block_text(
        "source-code vulnerability\ndetection"
    )

    assert text == "source-code vulnerability detection"


def test_pdf_layout_blocks_become_spaces() -> None:
    sections = []

    ComputerPaperPdfParser._append_section(
        sections,
        page_number=1,
        section_name="Abstract",
        parts=["GraphNet solves the", "source-code vulnerability detection task."],
    )

    assert sections[0].text == (
        "GraphNet solves the source-code vulnerability detection task."
    )


def test_long_section_splits_with_explicit_overlap() -> None:
    text = "A" * (MAX_CHUNK_CHARACTERS + 800)

    chunks = ComputerPaperPdfParser._split_text(text)

    assert len(chunks) == 2
    assert len(chunks[0]) == MAX_CHUNK_CHARACTERS
    assert chunks[0][-CHUNK_OVERLAP_CHARACTERS:] == chunks[1][:CHUNK_OVERLAP_CHARACTERS]
