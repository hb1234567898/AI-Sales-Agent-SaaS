package com.yourcompany.salesagent.shared.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * 在 Java UUID 与 PostgreSQL uuid 类型之间进行转换。
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = { JdbcType.OTHER, JdbcType.VARCHAR }, includeNullJdbcType = true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

	@Override
	public void setNonNullParameter(
			PreparedStatement preparedStatement,
			int parameterIndex,
			UUID parameter,
			JdbcType jdbcType) throws SQLException {
		preparedStatement.setObject(parameterIndex, parameter, Types.OTHER);
	}

	@Override
	public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
		return toUuid(resultSet.getObject(columnName));
	}

	@Override
	public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
		return toUuid(resultSet.getObject(columnIndex));
	}

	@Override
	public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
		return toUuid(statement.getObject(columnIndex));
	}

	private UUID toUuid(Object value) throws SQLException {
		if (value == null) {
			return null;
		}
		if (value instanceof UUID uuid) {
			return uuid;
		}
		try {
			return UUID.fromString(value.toString());
		}
		catch (IllegalArgumentException exception) {
			throw new SQLException("无法将数据库字段转换为 UUID: " + value, exception);
		}
	}
}
