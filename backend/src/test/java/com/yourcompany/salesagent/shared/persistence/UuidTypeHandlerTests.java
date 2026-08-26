package com.yourcompany.salesagent.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class UuidTypeHandlerTests {

	private final UuidTypeHandler handler = new UuidTypeHandler();

	@Test
	void writesAndReadsPostgresqlUuid() throws Exception {
		var uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
		var statement = mock(PreparedStatement.class);

		handler.setNonNullParameter(statement, 1, uuid, JdbcType.OTHER);

		verify(statement).setObject(1, uuid, Types.OTHER);

		var resultSet = mock(ResultSet.class);
		when(resultSet.getObject("id")).thenReturn(uuid);
		assertThat(handler.getNullableResult(resultSet, "id")).isEqualTo(uuid);
	}
}
