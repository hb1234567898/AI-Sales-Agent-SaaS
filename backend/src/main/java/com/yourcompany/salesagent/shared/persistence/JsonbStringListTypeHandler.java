package com.yourcompany.salesagent.shared.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public class JsonbStringListTypeHandler extends BaseTypeHandler<List<String>> {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
	};

	@Override
	public void setNonNullParameter(
			PreparedStatement preparedStatement,
			int parameterIndex,
			List<String> parameter,
			JdbcType jdbcType) throws SQLException {
		var jsonb = new PGobject();
		jsonb.setType("jsonb");
		jsonb.setValue(JSON_MAPPER.writeValueAsString(parameter));
		preparedStatement.setObject(parameterIndex, jsonb);
	}

	@Override
	public List<String> getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
		return parse(resultSet.getString(columnName));
	}

	@Override
	public List<String> getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
		return parse(resultSet.getString(columnIndex));
	}

	@Override
	public List<String> getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
		return parse(statement.getString(columnIndex));
	}

	private List<String> parse(String json) throws SQLException {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return new ArrayList<>(JSON_MAPPER.readValue(json, LIST_TYPE));
		}
		catch (RuntimeException exception) {
			throw new SQLException("无法解析 PostgreSQL JSONB 字符串数组", exception);
		}
	}
}
