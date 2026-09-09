package com.yourcompany.salesagent.followup.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

class FollowUpMapperXmlTests {

	@Test
	void parsesMapperXml() throws Exception {
		var configuration = new MybatisConfiguration();
		configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
		configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
		configuration.addMapper(FollowUpMapper.class);
		try (var input = getClass().getResourceAsStream("/mapper/followup/FollowUpMapper.xml")) {
			assertThatCode(() -> new XMLMapperBuilder(
					input,
					configuration,
					"mapper/followup/FollowUpMapper.xml",
					configuration.getSqlFragments()).parse())
					.doesNotThrowAnyException();
		}
	}
}
