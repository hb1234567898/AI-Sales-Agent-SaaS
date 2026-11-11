package com.yourcompany.salesagent.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
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
import com.yourcompany.salesagent.knowledge.api.KnowledgeDocumentRequest;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;

@EnabledIfEnvironmentVariable(named = "KNOWLEDGE_TEST_DB_URL", matches = ".+")
class KnowledgePostgresTests {

	@Test
	void retrievesChineseChunksWithTenantPrivacyStateAndRevisionIsolation() throws Exception {
		var dataSource = new UnpooledDataSource("org.postgresql.Driver", System.getenv("KNOWLEDGE_TEST_DB_URL"), "opportunity_test", "");
		try (var connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try {
				var schema = "knowledge_test_" + UUID.randomUUID().toString().replace("-", "");
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE SCHEMA " + schema);
					statement.execute("SET search_path TO " + schema);
					for (var migration : new String[] { "V1__initial_schema.sql", "V11__uploaded_files.sql", "V16__enterprise_knowledge.sql" }) {
						try (var input = getClass().getResourceAsStream("/db/migration/" + migration)) {
							statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
						}
					}
				}
				var org = UUID.randomUUID(); var member = UUID.randomUUID(); var user = UUID.randomUUID();
				var peer = UUID.randomUUID(); var peerUser = UUID.randomUUID(); var file = UUID.randomUUID();
				var document = UUID.randomUUID(); var privateDoc = UUID.randomUUID(); var chunk = UUID.randomUUID();
				var foreignOrg = UUID.randomUUID();
				execute(connection, "INSERT INTO organization(id, slug, name) VALUES (?, 'knowledge-test', 'Test')", org);
				execute(connection, "INSERT INTO app_user(id, email, display_name, auth_provider, auth_subject) VALUES (?, 'author@test.local', 'Author', 'LOCAL', 'author')", user);
				execute(connection, "INSERT INTO app_user(id, email, display_name, auth_provider, auth_subject) VALUES (?, 'peer@test.local', 'Peer', 'LOCAL', 'peer')", peerUser);
				execute(connection, "INSERT INTO organization_member(id, organization_id, user_id, role) VALUES (?, ?, ?, 'OWNER')", member, org, user);
				execute(connection, "INSERT INTO organization_member(id, organization_id, user_id, role) VALUES (?, ?, ?, 'SALES')", peer, org, peerUser);
				execute(connection, "INSERT INTO uploaded_file(id, organization_id, uploaded_by_member_id, original_filename, content_type, size_bytes, sha256, content) VALUES (?, ?, ?, 'policy.txt', 'text/plain', 1, ?, ?)", file, org, member, "0".repeat(64), new byte[] { 1 });
				var configuration = new MybatisConfiguration();
				configuration.setMapUnderscoreToCamelCase(true);
				configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
				configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER, UuidTypeHandler.class);
				configuration.getTypeHandlerRegistry().register(UUID.class, UuidTypeHandler.class);
				var pagination = new MybatisPlusInterceptor();
				pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
				configuration.addInterceptor(pagination);
				configuration.addMapper(KnowledgeMapper.class);
				try (var xml = getClass().getResourceAsStream("/mapper/knowledge/KnowledgeMapper.xml")) {
					new XMLMapperBuilder(xml, configuration, "mapper/knowledge/KnowledgeMapper.xml", configuration.getSqlFragments()).parse();
				}
				var session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(connection);
				var mapper = session.getMapper(KnowledgeMapper.class);
				var now = Instant.parse("2026-11-09T07:00:00Z");
				assertThat(mapper.insertDocument(org, member, document, file, new KnowledgeDocumentRequest("报价流程", "POLICY", "TEAM", null), now)).isEqualTo(1);
				assertThat(mapper.insertDocument(org, member, privateDoc, file, new KnowledgeDocumentRequest("个人笔记", "OTHER", "PRIVATE", null), now)).isEqualTo(1);
				assertThat(mapper.selectDocument(org, member, document).getStatus()).isEqualTo("PROCESSING");
				assertThat(mapper.selectDocument(org, peer, privateDoc)).isNull();
				assertThat(mapper.selectDocument(foreignOrg, member, document)).isNull();
				assertThat(mapper.selectDocuments(new Page<>(1, 10), org, peer, "", "").getTotal()).isEqualTo(1);
				assertThat(mapper.selectDocuments(new Page<>(1, 10), org, member, "", "").getTotal()).isEqualTo(2);
				assertThat(mapper.lockDocument(org, member, document).getTitle()).isEqualTo("报价流程");
				assertThat(mapper.insertChunk(org, chunk, document, 1, 0, 2, "报价需销售经理审批", "报价 审批 经理")).isEqualTo(1);
				mapper.insertChunk(org, UUID.randomUUID(), privateDoc, 1, 0, null, "私人报价规则", "报价 私人");
				assertThat(mapper.selectCandidates(org, peer, "报价", null)).isEmpty();
				mapper.completeProcessing(org, document, 1, 1, 10, null, now);
				mapper.completeProcessing(org, privateDoc, 1, 1, 10, null, now);
				assertThat(mapper.selectDocument(org, member, document).getStatus()).isEqualTo("READY");
				assertThat(mapper.selectCandidates(org, peer, "报价 | 审批", null)).hasSize(1);
				assertThat(mapper.selectCandidates(org, member, "报价", null)).hasSize(2);
				assertThat(mapper.selectCandidates(foreignOrg, member, "报价", null)).isEmpty();
				assertThat(mapper.selectCandidates(org, peer, "报价", privateDoc)).isEmpty();
				assertThat(mapper.selectChunks(new Page<>(1, 10), org, peer, document).getRecords().get(0).pageNumber()).isEqualTo(2);
				assertThat(mapper.sourceAvailable(org, peer, chunk, 1)).isEqualTo(1);
				assertThat(mapper.updateState(org, document, false, 1, now)).isEqualTo(1);
				assertThat(mapper.updateState(org, document, true, 1, now)).isZero();
				assertThat(mapper.sourceAvailable(org, peer, chunk, 1)).isZero();
				assertThat(mapper.selectCandidates(org, peer, "报价", null)).isEmpty();
				mapper.updateState(org, document, true, 2, now);
				assertThat(mapper.beginProcessing(org, document, file, 3, now)).isEqualTo(1);
				assertThat(mapper.selectDocument(org, member, document).getRevision()).isEqualTo(2);
				assertThat(mapper.completeProcessing(org, document, 1, 1, 10, null, now)).isZero();
				assertThat(mapper.deleteChunks(org, document)).isEqualTo(1);
				mapper.insertChunk(org, UUID.randomUUID(), document, 2, 0, 1, "更新后的报价流程", "报价 更新");
				mapper.completeProcessing(org, document, 2, 1, 10, null, now);
				assertThat(mapper.sourceAvailable(org, peer, chunk, 1)).isZero();
				assertThat(mapper.selectCandidates(org, peer, "报价", null).get(0).revision()).isEqualTo(2);
				assertThat(mapper.updateMetadata(org, document, new KnowledgeDocumentRequest("保密", "POLICY", "PRIVATE", 5L), now)).isEqualTo(1);
				assertThat(mapper.selectCandidates(org, peer, "报价", null)).isEmpty();
				assertThat(mapper.deleteDocument(org, document, 6, now)).isEqualTo(1);
				assertThat(mapper.selectDocument(org, member, document)).isNull();
				assertThat(mapper.selectCandidates(org, member, "更新", null)).isEmpty();
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
