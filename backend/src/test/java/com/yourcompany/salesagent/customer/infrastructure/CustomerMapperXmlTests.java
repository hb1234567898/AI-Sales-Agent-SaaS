package com.yourcompany.salesagent.customer.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

class CustomerMapperXmlTests {

	@Test
	void parsesMapperXml() throws Exception {
		var configuration = new MybatisConfiguration();
		configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
		configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
		configuration.addMapper(CustomerMapper.class);
		try (var input = getClass().getResourceAsStream("/mapper/customer/CustomerMapper.xml")) {
			assertThatCode(() -> new XMLMapperBuilder(
					input,
					configuration,
					"mapper/customer/CustomerMapper.xml",
					configuration.getSqlFragments()).parse())
					.doesNotThrowAnyException();
		}
	}
}
