package com.yourcompany.salesagent.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

class AssistantConversationMapperXmlTests {

	@Test
	void parsesMapperXmlWithConstructorResultMappings() throws Exception {
		var configuration = new MybatisConfiguration();
		configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
		configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
		configuration.addMapper(AssistantConversationMapper.class);
		try (var input = getClass().getResourceAsStream("/mapper/assistant/AssistantConversationMapper.xml")) {
			assertThatCode(() -> new XMLMapperBuilder(
					input,
					configuration,
					"mapper/assistant/AssistantConversationMapper.xml",
					configuration.getSqlFragments()).parse())
					.doesNotThrowAnyException();
		}

		assertThat(configuration.getResultMap("com.yourcompany.salesagent.assistant.infrastructure.AssistantConversationMapper.ConversationMap")
				.getConstructorResultMappings()).hasSize(11);
		assertThat(configuration.getResultMap("com.yourcompany.salesagent.assistant.infrastructure.AssistantConversationMapper.MessageMap")
				.getConstructorResultMappings()).hasSize(9);
	}
}
