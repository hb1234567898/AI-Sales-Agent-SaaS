package com.yourcompany.salesagent.opportunity.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yourcompany.salesagent.opportunity.api.*;
import com.yourcompany.salesagent.opportunity.domain.*;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

@EnabledIfEnvironmentVariable(named = "OPPORTUNITY_TEST_DB_URL", matches = ".+")
class OpportunityPostgresTests {
	@Test
	void persistsQueriesUpdatesAndClosesOpportunitiesInPostgres() throws Exception {
		var url = System.getenv("OPPORTUNITY_TEST_DB_URL");
		var dataSource = new UnpooledDataSource("org.postgresql.Driver", url, "opportunity_test", "");
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try {
				var schema = "opportunity_test_" + UUID.randomUUID().toString().replace("-", "");
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE SCHEMA " + schema);
					statement.execute("SET search_path TO " + schema);
					for (var migration : new String[] { "V1__initial_schema.sql", "V15__opportunity_management.sql" }) {
						try (var input = getClass().getResourceAsStream("/db/migration/" + migration)) {
							statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
						}
					}
				}
				var org = UUID.randomUUID(); var member = UUID.randomUUID(); var user = UUID.randomUUID();
				var customer = UUID.randomUUID(); var id = UUID.randomUUID(); var foreignOrg = UUID.randomUUID();
				execute(connection, "INSERT INTO organization(id, slug, name) VALUES (?, 'opportunity-test', 'Test')", org);
				execute(connection, "INSERT INTO app_user(id, email, display_name, auth_provider, auth_subject) VALUES (?, 'opportunity@test.local', 'Sales', 'LOCAL', 'test')", user);
				execute(connection, "INSERT INTO organization_member(id, organization_id, user_id, role) VALUES (?, ?, ?, 'OWNER')", member, org, user);
				execute(connection, "INSERT INTO customer(id, organization_id, name) VALUES (?, ?, 'Test customer')", customer, org);
				var configuration = new MybatisConfiguration();
				configuration.setMapUnderscoreToCamelCase(true);
				configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
				configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
				configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
				var pagination = new MybatisPlusInterceptor();
				pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
				configuration.addInterceptor(pagination);
				configuration.addMapper(OpportunityMapper.class);
				try (var xml = getClass().getResourceAsStream("/mapper/opportunity/OpportunityMapper.xml")) {
					new XMLMapperBuilder(xml, configuration, "mapper/opportunity/OpportunityMapper.xml", configuration.getSqlFragments()).parse();
				}
				var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection);
				var mapper = session.getMapper(OpportunityMapper.class);
				var now = Instant.parse("2026-11-09T02:00:00Z");
				var input = new OpportunityUpsertRequest(customer, "Platform", member, new BigDecimal("100000"), 20, LocalDate.parse("2026-12-01"), ForecastCategory.PIPELINE, "Demo", null);
				assertThat(mapper.insert(org, id, input, now)).isEqualTo(1);
				assertThat(mapper.selectOne(org, id).getCustomerId()).isEqualTo(customer);
				assertThat(mapper.selectOne(org, id).getStage()).isEqualTo(OpportunityStage.QUALIFIED);
				assertThat(mapper.selectOne(foreignOrg, id)).isNull();
				assertThat(mapper.selectPage(new Page<>(1, 10), org, "Platform", null, member).getTotal()).isEqualTo(1);
				assertThat(mapper.selectMetrics(org, now).weightedAmount()).isEqualByComparingTo("20000");
				var edit = new OpportunityUpsertRequest(customer, "Updated", member, new BigDecimal("120000"), 50, null, ForecastCategory.BEST_CASE, null, 0L);
				assertThat(mapper.update(org, id, edit, now)).isEqualTo(1);
				assertThat(mapper.update(org, id, edit, now)).isZero();
				assertThat(mapper.selectOne(org, id).getForecastCategory()).isEqualTo(ForecastCategory.BEST_CASE);
				var lost = new OpportunityStageRequest(OpportunityStage.LOST, 1L, "Budget cancelled");
				assertThat(mapper.changeStage(org, id, lost, 0, now)).isEqualTo(1);
				assertThat(mapper.insertEvent(org, id, member, OpportunityStage.QUALIFIED, OpportunityStage.LOST, "Closed", now)).isEqualTo(1);
				assertThat(mapper.selectEvents(org, id)).hasSize(1);
				assertThat(mapper.selectEvents(org, id).get(0).actorName()).isEqualTo("Sales");
				assertThat(mapper.selectEvents(foreignOrg, id)).isEmpty();
				assertThat(mapper.selectOne(org, id).getStatus()).isEqualTo("LOST");
				assertThat(mapper.selectOne(org, id).getProbability()).isZero();
				assertThat(mapper.selectMetrics(org, now).activeCount()).isZero();
				assertThat(mapper.changeStage(org, id, new OpportunityStageRequest(OpportunityStage.WON, 2L, null), 100, now)).isZero();
			}
			finally { connection.rollback(); }
		}
	}
	private static void execute(Connection connection, String sql, Object... values) throws SQLException {
		try (var statement = connection.prepareStatement(sql)) {
			for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
			statement.executeUpdate();
		}
	}
}
