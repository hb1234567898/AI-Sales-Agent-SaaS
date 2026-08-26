package com.yourcompany.salesagent.shared.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	@Override
	public void setNonNullParameter(
			PreparedStatement preparedStatement,
			int parameterIndex,
			Map<String, Object> parameter,
			JdbcType jdbcType) throws SQLException {
		var jsonb = new PGobject();
		jsonb.setType("jsonb");
		jsonb.setValue(JSON_MAPPER.writeValueAsString(parameter));
		preparedStatement.setObject(parameterIndex, jsonb);
	}

	@Override
	public Map<String, Object> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
		return parse(resultSet.getString(columnName));
	}

	@Override
	public Map<String, Object> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
		return parse(resultSet.getString(columnIndex));
	}

	@Override
	public Map<String, Object> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
		return parse(statement.getString(columnIndex));
	}

	private Map<String, Object> parse(String json) throws SQLException {
		if (json == null || json.isBlank()) {
			return new HashMap<>();
		}
		try {
			return new HashMap<>(JSON_MAPPER.readValue(json, MAP_TYPE));
		}
		catch (RuntimeException exception) {
			throw new SQLException("无法解析 PostgreSQL JSONB 字段", exception);
		}
	}
}
