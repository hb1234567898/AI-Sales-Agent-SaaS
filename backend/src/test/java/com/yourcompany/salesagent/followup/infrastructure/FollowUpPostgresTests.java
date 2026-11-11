package com.yourcompany.salesagent.followup.infrastructure;

import static org.assertj.core.api.Assertions.*;
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
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;
import com.yourcompany.salesagent.followup.api.*;

@EnabledIfEnvironmentVariable(named = "FOLLOW_UP_TEST_DB_URL", matches = ".+")
class FollowUpPostgresTests {
    @Test void tasksRulesAndRemindersPersistWithDeduplicationAndTenantIsolation() throws Exception {
        var ds = new UnpooledDataSource("org.postgresql.Driver", System.getenv("FOLLOW_UP_TEST_DB_URL"),
                System.getenv().getOrDefault("FOLLOW_UP_TEST_DB_USER", "opportunity_test"), System.getenv().getOrDefault("FOLLOW_UP_TEST_DB_PASSWORD", ""));
        try (var connection = ds.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    var schema = "followup_test_" + UUID.randomUUID().toString().replace("-", "");
                    statement.execute("CREATE SCHEMA " + schema);
                    statement.execute("SET search_path TO " + schema);
                    for (var migration : new String[] {"V1__initial_schema.sql", "V17__proactive_follow_ups.sql"}) {
                        try (var input = getClass().getResourceAsStream("/db/migration/" + migration)) { statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8)); }
                    }
                }
                var org = UUID.randomUUID(); var member = UUID.randomUUID(); var user = UUID.randomUUID();
                var customer = UUID.randomUUID(); var idle = UUID.randomUUID(); var foreignOrg = UUID.randomUUID();
                execute(connection, "INSERT INTO organization(id, slug, name) VALUES (?, 'followup-test', 'Test')", org);
                execute(connection, "INSERT INTO app_user(id, email, display_name, auth_provider, auth_subject) VALUES (?, 'followup@test.local', 'Sales', 'LOCAL', 'test')", user);
                execute(connection, "INSERT INTO organization_member(id, organization_id, user_id, role) VALUES (?, ?, ?, 'OWNER')", member, org, user);
                execute(connection, "INSERT INTO customer(id, organization_id, owner_member_id, name, created_at, attributes) VALUES (?, ?, ?, 'Manual customer', '2026-10-01', '{}')", customer, org, member);
                execute(connection, "INSERT INTO customer(id, organization_id, owner_member_id, name, created_at, attributes) VALUES (?, ?, ?, 'Idle customer', '2026-10-01', '{\"score\":85,\"nextAction\":\"Confirm proposal\"}')", idle, org, member);
                var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
                config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
                config.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
                config.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
                var pagination = new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL)); config.addInterceptor(pagination);
                for (var name : new String[] {"FollowUpMapper", "FollowUpAutomationMapper"}) {
                    try (var xml = getClass().getResourceAsStream("/mapper/followup/" + name + ".xml")) {
                        new XMLMapperBuilder(xml, config, "mapper/followup/" + name + ".xml", config.getSqlFragments()).parse();
                    }
                }
                var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(connection);
                var tasks = session.getMapper(FollowUpMapper.class); var automation = session.getMapper(FollowUpAutomationMapper.class);
                var now = Instant.parse("2026-11-11T02:00:00Z"); var id = UUID.randomUUID();
                var input = new FollowUpUpsertRequest(customer, member, "Call customer", now.plusSeconds(600), 60, "Test", "CALL", null);
                assertThat(tasks.insert(org, id, input, now)).isEqualTo(1);
                assertThat(tasks.selectFollowUp(org, id).getRecommendedAction()).containsEntry("title", "Call customer");
                assertThat(tasks.selectFollowUp(foreignOrg, id)).isNull();
                assertThat(tasks.customerExists(org, customer, member)).isEqualTo(1);
                assertThat(tasks.ownerExists(org, member)).isEqualTo(1);
                assertThat(tasks.selectOwners(org, member)).hasSize(1);
                assertThat(tasks.selectCustomers(org, "Manual", member)).hasSize(1);
                var edit = new FollowUpUpsertRequest(customer, member, "Updated call", now.plusSeconds(600), 80, "Updated", "EMAIL", 0L);
                assertThat(tasks.update(org, id, edit, now)).isEqualTo(1);
                assertThat(tasks.update(org, id, edit, now)).isZero();
                assertThat(tasks.selectFollowUps(new Page<>(1, 1), org, member, "TODAY", now, now.minusSeconds(3600), now.plusSeconds(86400)).getTotal()).isEqualTo(1);
                assertThat(tasks.selectMetrics(org, member, now, now.minusSeconds(3600), now.plusSeconds(86400)).pending()).isEqualTo(1);
                assertThat(automation.createReminders(org, member, now)).isEqualTo(1);
                assertThat(automation.createReminders(org, member, now)).isZero();
                assertThat(automation.countReminders(org, member, now)).isEqualTo(1);
                assertThat(automation.countReminders(foreignOrg, member, now)).isZero();
                var reminder = automation.selectReminders(org, member, now).get(0);
                assertThat(reminder.getFollowUpId()).isEqualTo(id);
                assertThat(automation.readReminder(org, member, reminder.getId(), now)).isEqualTo(1);
                assertThat(automation.countReminders(org, member, now)).isZero();
                var overdue = now.plusSeconds(900);
                assertThat(automation.createReminders(org, member, overdue)).isEqualTo(1);
                assertThat(automation.selectReminders(org, member, overdue).get(0).getKind()).isEqualTo("OVERDUE");
                assertThat(tasks.snooze(org, id, new FollowUpSnoozeRequest(now.plusSeconds(86400), "Tomorrow", 1L), now)).isEqualTo(1);
                assertThat(automation.countReminders(org, member, overdue)).isZero();
                assertThat(tasks.changeStatus(org, id, 0L, "COMPLETED", now)).isZero();
                assertThat(tasks.changeStatus(org, id, 2L, "COMPLETED", now)).isEqualTo(1);
                assertThat(tasks.selectMetrics(org, member, now, now, now.plusSeconds(86400)).completed()).isEqualTo(1);
                var ruleInput = new FollowUpRuleRequest(true, 7, 9, 5, true, 30, -1);
                assertThat(automation.insertRule(org, member, ruleInput, now)).isEqualTo(1);
                assertThat(automation.insertRule(org, member, ruleInput, now)).isZero();
                var rule = automation.lockRule(org, member); assertThat(rule.isEnabled()).isTrue();
                assertThat(automation.selectActor(org, member).getUserId()).isEqualTo(user);
                assertThat(automation.selectDueRules(LocalDate.parse("2026-11-11"), 9)).hasSize(1);
                var batch = UUID.randomUUID(); var day = LocalDate.parse("2026-11-11");
                assertThat(automation.insertRun(batch, rule, day, now)).isEqualTo(1);
                assertThat(automation.insertRun(UUID.randomUUID(), rule, day, now)).isZero();
                assertThat(automation.createTasks(batch, rule, now, now.minusSeconds(7 * 86400))).isEqualTo(1);
                assertThat(automation.createTasks(batch, rule, now, now.minusSeconds(7 * 86400))).isZero();
                assertThat(automation.selectRunCustomers(org, batch)).containsExactly(idle);
                var generated = tasks.selectFollowUps(new Page<>(1, 10), org, member, "ALL", now, now, now.plusSeconds(86400)).getRecords().get(0);
                assertThat(generated.getPriority()).isEqualTo(85); assertThat(generated.getOrigin()).isEqualTo("RULE");
                assertThat(generated.getRecommendedAction()).containsEntry("title", "Confirm proposal");
                assertThat(automation.prepared(org, batch, 1, "QUEUED", now)).isEqualTo(1);
                assertThat(automation.selectRun(org, member, day).getCreatedCount()).isEqualTo(1);
                assertThat(automation.selectQueuedRuns()).hasSize(1);
                assertThat(automation.claimRun(org, batch, now)).isEqualTo(1);
                assertThat(automation.claimRun(org, batch, now)).isZero();
                assertThat(automation.finishRun(org, batch, "COMPLETED", null, null, now)).isEqualTo(1);
                assertThat(automation.selectLatestRun(org, member).getStatus()).isEqualTo("COMPLETED");
                assertThat(automation.selectDueRules(day, 10)).isEmpty();
                assertThat(automation.updateRule(org, member, new FollowUpRuleRequest(false, 7, 9, 5, false, 15, 0), now)).isEqualTo(1);
            } finally { connection.rollback(); }
        }
    }
    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) { for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]); statement.executeUpdate(); }
    }
}
