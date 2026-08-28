package com.yourcompany.salesagent.admin.infrastructure;

import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.admin.domain.MemberRole;
import com.yourcompany.salesagent.admin.domain.MemberStatus;

public interface AdminMapper {

	IPage<AdminMemberRow> selectMembers(
			Page<AdminMemberRow> page,
			@Param("organizationId") UUID organizationId,
			@Param("keyword") String keyword,
			@Param("role") MemberRole role,
			@Param("status") MemberStatus status);

	AdminMemberRow selectMember(@Param("organizationId") UUID organizationId, @Param("memberId") UUID memberId);

	AdminTeamRow selectTeam(@Param("organizationId") UUID organizationId);

	long countEmail(@Param("email") String email);

	int insertUser(
			@Param("userId") UUID userId,
			@Param("email") String email,
			@Param("displayName") String displayName,
			@Param("authSubject") String authSubject,
			@Param("now") Instant now);

	int insertCredential(
			@Param("userId") UUID userId,
			@Param("passwordHash") String passwordHash,
			@Param("now") Instant now);

	int insertMember(
			@Param("memberId") UUID memberId,
			@Param("organizationId") UUID organizationId,
			@Param("userId") UUID userId,
			@Param("role") MemberRole role,
			@Param("now") Instant now);

	int updateMember(
			@Param("organizationId") UUID organizationId,
			@Param("memberId") UUID memberId,
			@Param("displayName") String displayName,
			@Param("role") MemberRole role,
			@Param("status") MemberStatus status,
			@Param("now") Instant now);

	int revokeMemberSessions(
			@Param("organizationId") UUID organizationId,
			@Param("memberId") UUID memberId,
			@Param("now") Instant now);

	int updateTeam(
			@Param("organizationId") UUID organizationId,
			@Param("name") String name,
			@Param("timezone") String timezone,
			@Param("locale") String locale,
			@Param("now") Instant now);
}
