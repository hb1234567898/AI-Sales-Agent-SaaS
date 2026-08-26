package com.yourcompany.salesagent.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.postgresql.util.PGobject;

class JsonbStringListTypeHandlerTests {

	private final JsonbStringListTypeHandler handler = new JsonbStringListTypeHandler();

	@Test
	void writesAndReadsPostgresqlJsonbArray() throws Exception {
		var statement = mock(PreparedStatement.class);
		handler.setNonNullParameter(statement, 1, List.of("林婉清", "陈默"), JdbcType.OTHER);

		var valueCaptor = ArgumentCaptor.forClass(Object.class);
		verify(statement).setObject(org.mockito.ArgumentMatchers.eq(1), valueCaptor.capture());
		var jsonb = (PGobject) valueCaptor.getValue();
		assertThat(jsonb.getType()).isEqualTo("jsonb");

		var resultSet = mock(ResultSet.class);
		when(resultSet.getString("participants")).thenReturn(jsonb.getValue());
		assertThat(handler.getNullableResult(resultSet, "participants")).containsExactly("林婉清", "陈默");
	}
}
