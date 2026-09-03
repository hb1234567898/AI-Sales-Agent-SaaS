package com.yourcompany.salesagent.assistant.infrastructure;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface AssistantConversationMapper {

	AssistantConversationRow selectConversation(
			@Param("organizationId") UUID organizationId,
			@Param("conversationId") UUID conversationId);

	IPage<AssistantConversationRow> selectConversations(
			Page<AssistantConversationRow> page,
			@Param("organizationId") UUID organizationId,
			@Param("memberId") UUID memberId);

	int insertConversation(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("userId") UUID userId,
			@Param("memberId") UUID memberId,
			@Param("title") String title,
			@Param("channel") String channel,
			@Param("now") Instant now);

	int touchConversation(
			@Param("organizationId") UUID organizationId,
			@Param("conversationId") UUID conversationId,
			@Param("now") Instant now);

	int insertMessage(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("conversationId") UUID conversationId,
			@Param("role") String role,
			@Param("content") String content,
			@Param("reasoningSummary") String reasoningSummary,
			@Param("toolTracesJson") String toolTracesJson,
			@Param("data") Map<String, Object> data,
			@Param("createdAt") Instant createdAt);

	IPage<AssistantMessageRow> selectMessages(
			Page<AssistantMessageRow> page,
			@Param("organizationId") UUID organizationId,
			@Param("conversationId") UUID conversationId);
}
