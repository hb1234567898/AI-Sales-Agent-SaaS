import asyncio
import base64
from io import BytesIO

import pytest
from docx import Document
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage
from pypdf import PdfWriter

from app.main import app
from app.core.config import settings
from app.features.knowledge.documents import ingest_document, search_query, search_terms
from app.features.knowledge.schemas import DocumentIngestRequest, KnowledgeCandidate, KnowledgeQueryRequest
from app.features.knowledge.workflow import answer_from_knowledge


def request(filename: str, content: bytes) -> DocumentIngestRequest:
    return DocumentIngestRequest(filename=filename, title="销售手册", category="PLAYBOOK", content_base64=base64.b64encode(content).decode())


def test_ingest_chinese_text_has_bounded_chunks_and_searchable_terms():
    content = ("报价审批需要销售经理确认。客户报价附件必须核对。\n" * 100).encode()
    result = ingest_document(request("报价流程.md", content))
    assert len(result.chunks) > 1
    assert all(len(chunk.content) <= 900 for chunk in result.chunks)
    assert [chunk.chunk_index for chunk in result.chunks] == list(range(len(result.chunks)))
    assert "报价" in result.chunks[0].search_terms.split()
    assert result.character_count == len(content.decode())


def test_supports_gb18030_text():
    result = ingest_document(request("销售.txt", "报价审批".encode("gb18030")))
    assert result.chunks[0].content == "报价审批"


def test_extracts_docx_paragraphs_and_tables():
    doc = Document()
    doc.add_paragraph("标准版报价")
    table = doc.add_table(rows=1, cols=2)
    table.cell(0, 0).text = "企业版"
    table.cell(0, 1).text = "联系销售"
    output = BytesIO(); doc.save(output)
    result = ingest_document(request("报价.docx", output.getvalue()))
    assert "标准版报价" in result.chunks[0].content
    assert "企业版 | 联系销售" in result.chunks[0].content


def test_blank_pdf_requires_ocr_instead_of_inventing_text():
    writer = PdfWriter(); writer.add_blank_page(width=300, height=300)
    output = BytesIO(); writer.write(output)
    with pytest.raises(ValueError, match="OCR"):
        ingest_document(request("scan.pdf", output.getvalue()))


@pytest.mark.parametrize("filename,content", [("run.exe", b"hello"), ("empty.txt", b""), ("binary.txt", b"\x00\x01")])
def test_rejects_unsupported_empty_and_binary_files(filename, content):
    with pytest.raises(ValueError):
        ingest_document(request(filename, content))


def test_terms_are_safe_tsquery_tokens():
    assert "报价" in search_terms("报价流程")
    assert "crm" in search_terms("CRM pricing")
    assert all(term.strip().isalnum() or "_" in term for term in search_terms("x' | : & ! () 报价"))
    assert search_query("? | & !") == ""


def test_ingestion_and_terms_require_internal_auth(monkeypatch):
    monkeypatch.setattr(settings, "token", "internal-test")
    client = TestClient(app)
    assert client.post("/v1/knowledge/ingest", json=request("a.txt", b"hello").model_dump()).status_code == 401
    response = client.post("/v1/knowledge/terms", headers={"Authorization": "Bearer internal-test"}, json={"query": "报价"})
    assert response.status_code == 200
    assert response.json()["search_query"] == "报价"


def test_parse_error_is_a_readable_400():
    response = TestClient(app).post("/v1/knowledge/ingest", json=request("a.exe", b"hello").model_dump())
    assert response.status_code == 400
    assert "支持" in response.json()["detail"]


def test_grounded_answer_preserves_sources_and_usage(monkeypatch):
    class FakeModel:
        async def ainvoke(self, messages):
            assert "资料 1" in messages[1].content
            assert "销售经理审批" in messages[1].content
            return AIMessage(content="需要销售经理审批。[资料 1]", usage_metadata={"input_tokens": 20, "output_tokens": 10, "total_tokens": 30})
    monkeypatch.setattr("app.features.knowledge.workflow.create_chat_model", lambda _: FakeModel())
    result = asyncio.run(answer_from_knowledge(KnowledgeQueryRequest(api_key="test", base_url="https://example.invalid/v1", model="test",
        query="报价审批", candidates=[KnowledgeCandidate(id="source-1", title="报价流程", content="报价须经销售经理审批")])))
    assert result.sources[0].id == "source-1"
    assert result.usage.total_tokens == 30
    assert "[资料 1]" in result.content


def test_no_match_does_not_call_model(monkeypatch):
    def unexpected(_):
        raise AssertionError("no model call expected")
    monkeypatch.setattr("app.features.knowledge.workflow.create_chat_model", unexpected)
    result = asyncio.run(answer_from_knowledge(KnowledgeQueryRequest(api_key="test", base_url="https://example.invalid/v1", model="test", query="报价", candidates=[])))
    assert result.sources == []
    assert result.usage is None
