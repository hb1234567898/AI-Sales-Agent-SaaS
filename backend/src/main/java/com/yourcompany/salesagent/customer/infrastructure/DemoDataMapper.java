package com.yourcompany.salesagent.customer.infrastructure;

import java.util.UUID;

import org.apache.ibatis.annotations.Param;

public interface DemoDataMapper {

	int insertOrganization(@Param("organizationId") UUID organizationId);

	int insertUser(
			@Param("id") UUID id,
			@Param("email") String email,
			@Param("displayName") String displayName,
			@Param("authSubject") String authSubject);

	int insertMember(
			@Param("id") UUID id,
			@Param("organizationId") UUID organizationId,
			@Param("userId") UUID userId);

	int insertCredential(@Param("userId") UUID userId, @Param("passwordHash") String passwordHash);
}
