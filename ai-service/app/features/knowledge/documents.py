import base64
import binascii
import re
from io import BytesIO
from pathlib import PurePosixPath
from zipfile import ZipFile

from docx import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter
from pypdf import PdfReader

from app.features.knowledge.schemas import DocumentChunk, DocumentIngestRequest, DocumentIngestResponse

MAX_BYTES = 10 * 1024 * 1024
MAX_CHARACTERS = 500_000
MAX_CHUNKS = 1000
SPLITTER = RecursiveCharacterTextSplitter(
    chunk_size=900, chunk_overlap=120,
    separators=["\n\n", "\n", "。", "！", "？", "；", " ", ""],
)


def search_terms(text: str) -> list[str]:
    terms: set[str] = set()
    for word in re.findall(r"[a-z0-9_]+|[\u3400-\u9fff]+", text.lower()):
        if word[0].isascii():
            terms.add(word[:80])
        elif len(word) == 1:
            terms.add(word)
        else:
            terms.update(word[index:index + 2] for index in range(len(word) - 1))
    return sorted(terms)


def search_query(text: str) -> str:
    return " | ".join(search_terms(text)[:256])


def _extract(content: bytes, extension: str) -> list[tuple[int | None, str]]:
    if extension in {".txt", ".md", ".markdown"}:
        for encoding in ("utf-8-sig", "gb18030"):
            try:
                text = content.decode(encoding)
                if "\x00" in text:
                    raise ValueError("文件包含二进制内容，请上传文本文件")
                return [(None, text)]
            except UnicodeDecodeError:
                continue
        raise ValueError("文本编码无法识别，请另存为 UTF-8 后上传")
    if extension == ".pdf":
        reader = PdfReader(BytesIO(content))
        if reader.is_encrypted:
            raise ValueError("请先移除 PDF 密码后再上传")
        if len(reader.pages) > 200:
            raise ValueError("PDF 不能超过 200 页，请拆分后上传")
        return [(index + 1, page.extract_text() or "") for index, page in enumerate(reader.pages)]
    if extension == ".docx":
        with ZipFile(BytesIO(content)) as archive:
            if sum(info.file_size for info in archive.infolist()) > 32 * 1024 * 1024:
                raise ValueError("Word 文档解压后过大，请拆分后上传")
        document = Document(BytesIO(content))
        paragraphs = [paragraph.text for paragraph in document.paragraphs]
        for table in document.tables:
            paragraphs.extend(" | ".join(cell.text for cell in row.cells) for row in table.rows)
        return [(None, "\n".join(paragraphs))]
    raise ValueError("支持 PDF、DOCX、TXT 和 Markdown 文件")


def ingest_document(request: DocumentIngestRequest) -> DocumentIngestResponse:
    try:
        content = base64.b64decode(request.content_base64, validate=True)
    except (binascii.Error, ValueError) as exception:
        raise ValueError("文件内容编码无效") from exception
    if not content or len(content) > MAX_BYTES:
        raise ValueError("文件不能为空且不能超过 10MB")
    extension = PurePosixPath(request.filename.replace("\\", "/")).suffix.lower()
    pages = _extract(content, extension)
    count = sum(len(text) for _, text in pages)
    if count > MAX_CHARACTERS:
        raise ValueError("文档文字超过 50 万字，请拆分后上传")
    chunks: list[DocumentChunk] = []
    for page_number, text in pages:
        for part in SPLITTER.split_text(text.replace("\x00", " ")):
            if part.strip():
                chunks.append(DocumentChunk(
                    chunk_index=len(chunks), page_number=page_number, content=part,
                    search_terms=" ".join(search_terms(request.title + " " + request.category + " " + part)),
                ))
            if len(chunks) > MAX_CHUNKS:
                raise ValueError("文档片段过多，请拆分后上传")
    if not chunks:
        raise ValueError("未识别到可检索文字，扫描件请先进行 OCR 识别")
    return DocumentIngestResponse(chunks=chunks, character_count=count)
