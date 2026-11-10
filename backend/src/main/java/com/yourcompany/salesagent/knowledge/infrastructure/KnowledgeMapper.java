package com.yourcompany.salesagent.knowledge.infrastructure;

import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.knowledge.api.*;

public interface KnowledgeMapper {
	IPage<KnowledgeDocumentRow> selectDocuments(Page<KnowledgeDocumentRow> page, @Param("org") UUID org,
			@Param("member") UUID member, @Param("query") String query, @Param("category") String category);
	KnowledgeDocumentRow selectDocument(@Param("org") UUID org, @Param("member") UUID member, @Param("id") UUID id);
	KnowledgeDocumentRow lockDocument(@Param("org") UUID org, @Param("member") UUID member, @Param("id") UUID id);
	int insertDocument(@Param("org") UUID org, @Param("member") UUID member, @Param("id") UUID id,
			@Param("file") UUID file, @Param("input") KnowledgeDocumentRequest input, @Param("now") Instant now);
	int updateMetadata(@Param("org") UUID org, @Param("id") UUID id, @Param("input") KnowledgeDocumentRequest input, @Param("now") Instant now);
	int updateState(@Param("org") UUID org, @Param("id") UUID id, @Param("enabled") boolean enabled, @Param("version") long version, @Param("now") Instant now);
	int beginProcessing(@Param("org") UUID org, @Param("id") UUID id, @Param("file") UUID file, @Param("version") long version, @Param("now") Instant now);
	int completeProcessing(@Param("org") UUID org, @Param("id") UUID id, @Param("revision") long revision,
			@Param("count") int count, @Param("characters") int characters, @Param("error") String error, @Param("now") Instant now);
	int deleteDocument(@Param("org") UUID org, @Param("id") UUID id, @Param("version") long version, @Param("now") Instant now);
	int deleteChunks(@Param("org") UUID org, @Param("id") UUID id);
	int insertChunk(@Param("org") UUID org, @Param("id") UUID id, @Param("document") UUID document,
			@Param("revision") long revision, @Param("index") int index, @Param("pageNumber") Integer pageNumber,
			@Param("content") String content, @Param("terms") String terms);
	IPage<KnowledgeChunkResponse> selectChunks(Page<KnowledgeChunkResponse> page, @Param("org") UUID org, @Param("member") UUID member, @Param("id") UUID id);
	List<KnowledgeChunkResponse> selectCandidates(@Param("org") UUID org, @Param("member") UUID member,
			@Param("searchQuery") String searchQuery, @Param("document") UUID document);
	int sourceAvailable(@Param("org") UUID org, @Param("member") UUID member, @Param("id") UUID id, @Param("revision") long revision);
}
