package com.yourcompany.salesagent.approval.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

class ApprovalMapperXmlTests {

	@Test
	void parsesMapperXml() throws Exception {
		var configuration = new MybatisConfiguration();
		configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
		configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
		configuration.addMapper(ApprovalMapper.class);
		try (var input = getClass().getResourceAsStream("/mapper/approval/ApprovalMapper.xml")) {
			assertThatCode(() -> new XMLMapperBuilder(
					input,
					configuration,
					"mapper/approval/ApprovalMapper.xml",
					configuration.getSqlFragments()).parse())
					.doesNotThrowAnyException();
		}
	}
}
