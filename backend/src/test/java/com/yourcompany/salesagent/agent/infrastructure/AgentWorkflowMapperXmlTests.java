package com.yourcompany.salesagent.agent.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

class AgentWorkflowMapperXmlTests {

	@Test
	void parsesMapperXml() throws Exception {
		var configuration = new MybatisConfiguration();
		configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
		configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
		configuration.addMapper(AgentWorkflowMapper.class);
		try (var input = getClass().getResourceAsStream("/mapper/agent/AgentWorkflowMapper.xml")) {
			assertThatCode(() -> new XMLMapperBuilder(
					input,
					configuration,
					"mapper/agent/AgentWorkflowMapper.xml",
					configuration.getSqlFragments()).parse())
					.doesNotThrowAnyException();
		}
	}
}
