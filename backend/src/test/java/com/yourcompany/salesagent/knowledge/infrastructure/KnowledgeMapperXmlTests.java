package com.yourcompany.salesagent.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

class KnowledgeMapperXmlTests {
	@Test
	void allRetrievalQueriesEnforceTenantVisibilityAndDocumentState() throws Exception {
		var config = new MybatisConfiguration();
		config.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
		config.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
		config.addMapper(KnowledgeMapper.class);
		try (var input = getClass().getResourceAsStream("/mapper/knowledge/KnowledgeMapper.xml")) {
			new XMLMapperBuilder(input, config, "mapper/knowledge/KnowledgeMapper.xml", config.getSqlFragments()).parse();
		}
		var params = new HashMap<String, Object>();
		params.put("org", UUID.randomUUID()); params.put("member", UUID.randomUUID()); params.put("searchQuery", "报价");
		for (var name : List.of("selectCandidates", "sourceAvailable")) {
			var sql = config.getMappedStatement(KnowledgeMapper.class.getName() + "." + name).getBoundSql(params).getSql();
			assertThat(sql).contains("d.organization_id = ?", "d.visibility = 'TEAM' OR d.uploaded_by_member_id = ?", "d.status = 'READY'", "d.enabled = true", "c.revision = d.revision");
		}
	}
}
