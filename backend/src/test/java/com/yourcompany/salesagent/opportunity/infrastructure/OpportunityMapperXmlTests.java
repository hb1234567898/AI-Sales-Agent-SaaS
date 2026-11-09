package com.yourcompany.salesagent.opportunity.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

class OpportunityMapperXmlTests {
	@Test
	void parsesAllQueriesAndKeepsTenantAndVersionPredicates() throws Exception {
		var configuration = new MybatisConfiguration();
		configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
		configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
		configuration.addMapper(OpportunityMapper.class);
		try (var input = getClass().getResourceAsStream("/mapper/opportunity/OpportunityMapper.xml")) {
			new XMLMapperBuilder(input, configuration, "mapper/opportunity/OpportunityMapper.xml", configuration.getSqlFragments()).parse();
		}
		var parameters = new HashMap<String, Object>();
		parameters.put("org", UUID.randomUUID()); parameters.put("id", UUID.randomUUID()); parameters.put("now", Instant.now());
		parameters.put("input", new com.yourcompany.salesagent.opportunity.api.OpportunityStageRequest(
				com.yourcompany.salesagent.opportunity.domain.OpportunityStage.WON, 1L, null));
		var sql = configuration.getMappedStatement(OpportunityMapper.class.getName() + ".changeStage").getBoundSql(parameters).getSql();
		assertThat(sql).contains("organization_id = ?", "version = ?", "status = 'OPEN'", "closed_at");
		var metrics = configuration.getMappedStatement(OpportunityMapper.class.getName() + ".selectMetrics").getBoundSql(parameters).getSql();
		assertThat(metrics).contains("CAST(? AS timestamptz)", "o.status = 'OPEN'");
	}
}
