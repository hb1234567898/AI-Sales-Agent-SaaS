package com.yourcompany.salesagent.knowledge.application;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.ai.application.*;
import com.yourcompany.salesagent.ai.infrastructure.PythonAiClient;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.file.domain.UploadedFile;
import com.yourcompany.salesagent.knowledge.api.*;
import com.yourcompany.salesagent.knowledge.infrastructure.*;

@Service
public class KnowledgeService {
	private final KnowledgeMapper mapper;
	private final FileStorageService files;
	private final PythonAiClient python;
	private final AiModelService models;
	private final ModelCallRecorder recorder;
	private final Clock clock;
	private final TransactionTemplate transactions;
	public KnowledgeService(KnowledgeMapper mapper, FileStorageService files, PythonAiClient python,
			AiModelService models, ModelCallRecorder recorder, Clock clock, PlatformTransactionManager tx) {
		this.mapper = mapper; this.files = files; this.python = python; this.models = models;
		this.recorder = recorder; this.clock = clock; this.transactions = new TransactionTemplate(tx);
	}
	public IPage<KnowledgeDocumentResponse> find(AuthPrincipal actor, String query, String category, int page, int size) {
		return mapper.selectDocuments(new Page<>(page + 1L, size), actor.organizationId(), actor.memberId(), query.strip(), category)
				.convert(row -> response(actor, row));
	}
	public KnowledgeDocumentResponse get(AuthPrincipal actor, UUID id) { return response(actor, require(actor, id, false)); }
	public IPage<KnowledgeChunkResponse> chunks(AuthPrincipal actor, UUID id, int page, int size) {
		require(actor, id, false);
		return mapper.selectChunks(new Page<>(page + 1L, size), actor.organizationId(), actor.memberId(), id);
	}
	public UploadedFile download(AuthPrincipal actor, UUID id) {
		var row = require(actor, id, false);
		return files.requireActive(actor.organizationId(), row.getFileId());
	}
	public KnowledgeDocumentResponse upload(AuthPrincipal actor, KnowledgeDocumentRequest input, MultipartFile file) {
		requireWriter(actor);
		validateFilename(file.getOriginalFilename());
		var row = transactions.execute(tx -> {
			var uploaded = files.upload(actor, null, file);
			var id = UUID.randomUUID();
			mapper.insertDocument(actor.organizationId(), actor.memberId(), id, uploaded.id(), input, clock.instant());
			return require(actor, id, false);
		});
		return process(actor, Objects.requireNonNull(row));
	}
	public KnowledgeDocumentResponse createText(AuthPrincipal actor, KnowledgeTextRequest request) {
		requireWriter(actor);
		var row = transactions.execute(tx -> {
			var filename = request.metadata().title().substring(0, Math.min(request.metadata().title().length(), 240)) + ".txt";
			var file = files.uploadContent(actor, null, filename, "text/plain", request.content().getBytes(StandardCharsets.UTF_8));
			var id = UUID.randomUUID();
			mapper.insertDocument(actor.organizationId(), actor.memberId(), id, file.id(), request.metadata(), clock.instant());
			return require(actor, id, false);
		});
		return process(actor, Objects.requireNonNull(row));
	}
	public KnowledgeDocumentResponse edit(AuthPrincipal actor, UUID id, KnowledgeDocumentRequest input) {
		var row = transactions.execute(tx -> {
			var current = requireManage(actor, id);
			requireIdle(current);
			if (input.version() == null) throw new KnowledgeException("请携带资料版本后重试");
			if ("PRIVATE".equals(input.visibility()) && !actor.memberId().equals(current.getUploadedByMemberId()))
				throw new KnowledgeException("只有资料上传者可以将资料设为个人私有");
			if (mapper.updateMetadata(actor.organizationId(), id, input, clock.instant()) != 1) throw conflict();
			var updated = require(actor, id, false);
			if (!current.getTitle().equals(input.title()) || !current.getCategory().equals(input.category())) {
				start(actor, updated, updated.getFileId(), updated.getVersion());
				return require(actor, id, false);
			}
			return updated;
		});
		return "PROCESSING".equals(Objects.requireNonNull(row).getStatus()) ? process(actor, row) : response(actor, row);
	}
	public KnowledgeDocumentResponse reprocess(AuthPrincipal actor, UUID id, long version, MultipartFile replacement) {
		if (replacement != null) validateFilename(replacement.getOriginalFilename());
		var row = transactions.execute(tx -> {
			var current = requireManage(actor, id);
			requireIdle(current);
			UUID fileId = replacement == null ? current.getFileId() : files.upload(actor, null, replacement).id();
			start(actor, current, fileId, version);
			return require(actor, id, false);
		});
		return process(actor, Objects.requireNonNull(row));
	}
	public KnowledgeDocumentResponse state(AuthPrincipal actor, UUID id, KnowledgeStateRequest input) {
		return transactions.execute(tx -> {
			requireManage(actor, id);
			if (mapper.updateState(actor.organizationId(), id, input.enabled(), input.version(), clock.instant()) != 1) throw conflict();
			return get(actor, id);
		});
	}
	public void delete(AuthPrincipal actor, UUID id, long version) {
		transactions.executeWithoutResult(tx -> {
			requireManage(actor, id);
			if (mapper.deleteDocument(actor.organizationId(), id, version, clock.instant()) != 1) throw conflict();
			mapper.deleteChunks(actor.organizationId(), id);
		});
	}
	public KnowledgeAnswerResponse ask(AuthPrincipal actor, KnowledgeQueryRequest request) {
		models.requireAvailableTokenQuota(actor.organizationId(), actor.memberId());
		if (request.documentId() != null) require(actor, request.documentId(), false);
		List<KnowledgeChunkResponse> candidates;
		try {
			var searchQuery = python.knowledgeSearchQuery(request.question());
			if (searchQuery == null || searchQuery.isBlank()) return noSources();
			candidates = mapper.selectCandidates(actor.organizationId(), actor.memberId(), searchQuery, request.documentId());
		}
		catch (PythonAiClient.PythonAiException e) { throw new AiModelConnectionException("知识检索服务暂不可用，请稍后重试", e); }
		if (candidates.isEmpty()) return noSources();
		var configuration = models.requireRuntimeConfiguration(actor.organizationId(), actor.memberId());
		var payload = candidates.stream().map(chunk -> Map.<String, Object>of("id", chunk.id().toString(), "title", chunk.title(), "content", chunk.content())).toList();
		var started = clock.instant();
		PythonAiClient.PythonKnowledgeAnswer answer;
		try {
			answer = python.answerKnowledge(configuration, request.question(), payload);
		}
		catch (RuntimeException e) {
			record(actor, configuration, request.question(), null, started, "FAILED");
			throw new AiModelConnectionException("知识库问答失败，请检查模型配置和 AI 服务状态", e);
		}
		record(actor, configuration, request.question(), answer, started, "SUCCEEDED");
		var byId = candidates.stream().collect(Collectors.toMap(chunk -> chunk.id().toString(), chunk -> chunk));
		var sources = new ArrayList<KnowledgeAnswerResponse.Source>();
		if (answer.sources() != null) for (var source : answer.sources()) {
			var chunk = byId.get(source.id());
			if (chunk == null || mapper.sourceAvailable(actor.organizationId(), actor.memberId(), chunk.id(), chunk.revision()) != 1)
				return new KnowledgeAnswerResponse("相关资料已更新或不可访问，请重新提问。", List.of(), usage(answer));
			sources.add(new KnowledgeAnswerResponse.Source(chunk.id(), chunk.documentId(), chunk.title(), chunk.chunkIndex(),
					chunk.pageNumber(), chunk.content(), source.score(), chunk.revision()));
		}
		if (sources.isEmpty()) return noSources();
		if (answer.content() == null || answer.content().isBlank()) throw new KnowledgeException("模型未返回回答，请重新提问");
		return new KnowledgeAnswerResponse(answer.content(), sources, usage(answer));
	}
	private void start(AuthPrincipal actor, KnowledgeDocumentRow row, UUID fileId, long version) {
		if (mapper.beginProcessing(actor.organizationId(), row.getId(), fileId, version, clock.instant()) != 1) throw conflict();
		mapper.deleteChunks(actor.organizationId(), row.getId());
	}
	private KnowledgeDocumentResponse process(AuthPrincipal actor, KnowledgeDocumentRow snapshot) {
		PythonAiClient.PythonDocument parsed;
		try {
			var file = files.requireActive(actor.organizationId(), snapshot.getFileId());
			parsed = python.ingestDocument(file.getOriginalFilename(), snapshot.getTitle(), snapshot.getCategory(), file.getContent());
			if (parsed.chunks() == null || parsed.chunks().isEmpty() || parsed.chunks().size() > 1000)
				throw new KnowledgeException("文档解析未返回有效片段");
		}
		catch (RuntimeException e) {
			var error = e.getMessage() != null && e.getMessage().startsWith("Document: ")
					? e.getMessage().substring(10) : "文档解析服务暂不可用或文件无法解析，请重新解析";
			transactions.executeWithoutResult(tx -> mapper.completeProcessing(actor.organizationId(), snapshot.getId(), snapshot.getRevision(), 0, 0, error, clock.instant()));
			return get(actor, snapshot.getId());
		}
		transactions.executeWithoutResult(tx -> {
			var current = mapper.lockDocument(actor.organizationId(), actor.memberId(), snapshot.getId());
			if (current == null || current.getRevision() != snapshot.getRevision() || !"PROCESSING".equals(current.getStatus())) return;
			for (var chunk : parsed.chunks()) mapper.insertChunk(actor.organizationId(), UUID.randomUUID(), snapshot.getId(),
					snapshot.getRevision(), chunk.chunkIndex(), chunk.pageNumber(), chunk.content(), chunk.searchTerms());
			mapper.completeProcessing(actor.organizationId(), snapshot.getId(), snapshot.getRevision(), parsed.chunks().size(), parsed.characterCount(), null, clock.instant());
		});
		return get(actor, snapshot.getId());
	}
	private void record(AuthPrincipal actor, AiModelRuntimeConfiguration configuration, String question,
			PythonAiClient.PythonKnowledgeAnswer answer, Instant started, String status) {
		recorder.record(new ModelCallRecordRequest(actor.organizationId(), actor.memberId(), null, null, null,
				"KNOWLEDGE_QA", configuration.provider(), answer == null || answer.model() == null ? configuration.model() : answer.model(),
				answer == null ? null : answer.providerRequestId(), "knowledge-v1", "knowledge-answer-v1", status, 1,
				usage(answer), Math.max(0, clock.millis() - started.toEpochMilli()), null, null,
				Map.of("questionCharacters", question.length()), Map.of("sourceCount", answer == null || answer.sources() == null ? 0 : answer.sources().size()),
				"FAILED".equals(status) ? "KNOWLEDGE_MODEL_FAILED" : null, null, started, clock.instant()));
	}
	private static ModelUsage usage(PythonAiClient.PythonKnowledgeAnswer answer) { return answer == null || answer.usage() == null ? null : answer.usage().toModelUsage(); }
	private static KnowledgeAnswerResponse noSources() { return new KnowledgeAnswerResponse("当前可访问的知识库中没有找到足够相关的资料，请补充资料或换一种问法。", List.of(), null); }
	private KnowledgeDocumentRow require(AuthPrincipal actor, UUID id, boolean lock) {
		var row = lock ? mapper.lockDocument(actor.organizationId(), actor.memberId(), id) : mapper.selectDocument(actor.organizationId(), actor.memberId(), id);
		if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料不存在或无权访问");
		return row;
	}
	private KnowledgeDocumentRow requireManage(AuthPrincipal actor, UUID id) {
		requireWriter(actor);
		var row = require(actor, id, true);
		if (!canManage(actor, row)) throw new AccessDeniedException("只有上传者或管理员可以管理该资料");
		return row;
	}
	private static void requireWriter(AuthPrincipal actor) { if ("VIEWER".equals(actor.role())) throw new AccessDeniedException("只读成员不能修改知识库"); }
	private static boolean canManage(AuthPrincipal actor, KnowledgeDocumentRow row) {
		return !"VIEWER".equals(actor.role()) && (actor.memberId().equals(row.getUploadedByMemberId()) || "OWNER".equals(actor.role()) || "ADMIN".equals(actor.role()));
	}
	private static KnowledgeDocumentResponse response(AuthPrincipal actor, KnowledgeDocumentRow row) { return KnowledgeDocumentResponse.from(row, canManage(actor, row)); }
	private void requireIdle(KnowledgeDocumentRow row) {
		if ("PROCESSING".equals(row.getStatus()) && row.getUpdatedAt().isAfter(clock.instant().minus(Duration.ofMinutes(10))))
			throw new KnowledgeException("资料正在解析，请稍后重试");
	}
	private static void validateFilename(String filename) {
		if (filename == null || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|docx|txt|md|markdown)$"))
			throw new KnowledgeException("支持 PDF、DOCX、TXT 和 Markdown 文件");
	}
	private static OptimisticLockingFailureException conflict() { return new OptimisticLockingFailureException("资料已更新，请刷新后重试"); }
}
