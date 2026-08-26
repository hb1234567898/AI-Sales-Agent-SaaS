package com.yourcompany.salesagent.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;

class JsonbMapTypeHandlerTests {

	private final JsonbMapTypeHandler handler = new JsonbMapTypeHandler();

	@Test
	void writesAndReadsPostgresqlJsonb() throws Exception {
		var statement = mock(PreparedStatement.class);
		handler.setNonNullParameter(statement, 1, Map.of("score", 92, "nextAction", "发送方案"), JdbcType.OTHER);

		var valueCaptor = ArgumentCaptor.forClass(Object.class);
		verify(statement).setObject(org.mockito.ArgumentMatchers.eq(1), valueCaptor.capture());
		var jsonb = (PGobject) valueCaptor.getValue();
		assertThat(jsonb.getType()).isEqualTo("jsonb");
		assertThat(jsonb.getValue()).contains("\"score\":92");

		var resultSet = mock(ResultSet.class);
		when(resultSet.getString("attributes")).thenReturn(jsonb.getValue());
		var attributes = handler.getNullableResult(resultSet, "attributes");
		assertThat(attributes.get("score")).isEqualTo(92);
		assertThat(attributes.get("nextAction")).isEqualTo("发送方案");
	}
}
