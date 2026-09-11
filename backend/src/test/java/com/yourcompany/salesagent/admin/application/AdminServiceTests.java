package com.yourcompany.salesagent.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.yourcompany.salesagent.admin.api.AdminMemberCreateRequest;
import com.yourcompany.salesagent.admin.api.AdminMemberUpdateRequest;
import com.yourcompany.salesagent.admin.api.AdminTeamUpdateRequest;
import com.yourcompany.salesagent.admin.domain.MemberRole;
import com.yourcompany.salesagent.admin.domain.MemberStatus;
import com.yourcompany.salesagent.admin.infrastructure.AdminMapper;
import com.yourcompany.salesagent.admin.infrastructure.AdminMemberRow;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;

class AdminServiceTests {

	private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OWNER_MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
	private static final UUID SALES_MEMBER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID SALES_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private static final Instant NOW = Instant.parse("2026-08-28T02:00:00Z");

	@Test
	void ownerCanCreateAnAdministratorWithHashedPassword() {
		var mapper = mock(AdminMapper.class);
		when(mapper.selectMember(eq(ORGANIZATION_ID), any())).thenAnswer(invocation -> member(
				invocation.getArgument(1), UUID.randomUUID(), MemberRole.ADMIN, MemberStatus.ACTIVE));
		var service = service(mapper);

		var result = service.createMember(
				principal("OWNER", OWNER_MEMBER_ID),
				new AdminMemberCreateRequest("新管理员", "ADMIN@example.com", MemberRole.ADMIN, "SecurePass123"));

		assertThat(result.email()).isEqualTo("admin@example.com");
		verify(mapper).insertCredential(any(), any(), eq(NOW));
		verify(mapper).insertMember(any(), eq(ORGANIZATION_ID), any(), eq(MemberRole.ADMIN), eq(NOW));
	}

	@Test
	void administratorCannotAssignAdministratorRole() {
		var service = service(mock(AdminMapper.class));

		assertThatThrownBy(() -> service.createMember(
				principal("ADMIN", OWNER_MEMBER_ID),
				new AdminMemberCreateRequest("另一管理员", "other@example.com", MemberRole.ADMIN, "SecurePass123")))
				.isInstanceOf(AdminValidationException.class)
				.hasMessage("只有团队所有者可以任命管理员");
	}

	@Test
	void suspendingMemberRevokesActiveSessions() {
		var mapper = mock(AdminMapper.class);
		var current = member(SALES_MEMBER_ID, SALES_USER_ID, MemberRole.SALES, MemberStatus.ACTIVE);
		var suspended = member(SALES_MEMBER_ID, SALES_USER_ID, MemberRole.SALES, MemberStatus.SUSPENDED);
		when(mapper.selectMember(ORGANIZATION_ID, SALES_MEMBER_ID)).thenReturn(current, suspended);
		when(mapper.updateMember(
				ORGANIZATION_ID, SALES_MEMBER_ID, "销售成员", MemberRole.SALES, MemberStatus.SUSPENDED, NOW)).thenReturn(1);
		var service = service(mapper);

		var result = service.updateMember(
				principal("OWNER", OWNER_MEMBER_ID),
				SALES_MEMBER_ID,
				new AdminMemberUpdateRequest("销售成员", MemberRole.SALES, MemberStatus.SUSPENDED));

		assertThat(result.status()).isEqualTo(MemberStatus.SUSPENDED);
		verify(mapper).revokeMemberSessions(ORGANIZATION_ID, SALES_MEMBER_ID, NOW);
	}

	@Test
	void rejectsUnknownBusinessTimezone() {
		var service = service(mock(AdminMapper.class));

		assertThatThrownBy(() -> service.updateTeam(
				principal("OWNER", OWNER_MEMBER_ID),
				new AdminTeamUpdateRequest("销售团队", "Asia/Not-A-Zone", "zh-CN")))
				.isInstanceOf(AdminValidationException.class)
				.hasMessageContaining("IANA 时区");
	}

	private static AdminService service(AdminMapper mapper) {
		return new AdminService(mapper, new BCryptPasswordEncoder(4), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static AuthPrincipal principal(String role, UUID memberId) {
		return new AuthPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), ORGANIZATION_ID, memberId,
				"owner@example.com", "团队所有者", "销售团队", role, NOW.plusSeconds(3600));
	}

	private static AdminMemberRow member(UUID memberId, UUID userId, MemberRole role, MemberStatus status) {
		return new AdminMemberRow(
				memberId, userId, role.name().toLowerCase() + "@example.com", "销售成员", role, status,
				NOW.minusSeconds(3600), null, NOW.minusSeconds(3600));
	}
}
