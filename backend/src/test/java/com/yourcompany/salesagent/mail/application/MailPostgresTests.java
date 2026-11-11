package com.yourcompany.salesagent.mail.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.PlatformTransactionManager;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yourcompany.salesagent.shared.persistence.UuidTypeHandler;
import com.yourcompany.salesagent.mail.api.*;
import com.yourcompany.salesagent.mail.infrastructure.*;
import com.yourcompany.salesagent.mail.transport.*;
import com.yourcompany.salesagent.auth.security.AuthPrincipal;
import com.yourcompany.salesagent.agent.infrastructure.AgentWorkflowMapper;
import com.yourcompany.salesagent.approval.infrastructure.ApprovalMapper;
import com.yourcompany.salesagent.tool.infrastructure.ToolExecutionMapper;
import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.tool.email.EmailConfigurationService;
import com.yourcompany.salesagent.file.application.FileStorageService;
import com.yourcompany.salesagent.interaction.application.ChatAnalysisService;

@EnabledIfEnvironmentVariable(named="MAIL_TEST_DB_URL",matches=".+")
class MailPostgresTests {
    @Test void receivesDeduplicatesApprovesRepliesAndClosesFollowUpInPostgres() throws Exception {
        var ds=new UnpooledDataSource("org.postgresql.Driver",System.getenv("MAIL_TEST_DB_URL"),"opportunity_test","");
        try(var connection=ds.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try(var sql=connection.createStatement()) {
                    var schema="mail_test_"+UUID.randomUUID().toString().replace("-",""); sql.execute("CREATE SCHEMA "+schema); sql.execute("SET search_path TO "+schema);
                    for(var migration:new String[]{"V1__initial_schema.sql","V17__proactive_follow_ups.sql","V18__email_reply_loop.sql"}) {
                        try(var input=getClass().getResourceAsStream("/db/migration/"+migration)) { sql.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8)); }
                    }
                }
                var org=UUID.randomUUID(); var member=UUID.randomUUID(); var user=UUID.randomUUID(); var customer=UUID.randomUUID();
                execute(connection,"INSERT INTO organization(id,slug,name) VALUES (?,'mail-test','Test')",org);
                execute(connection,"INSERT INTO app_user(id,email,display_name,auth_provider,auth_subject) VALUES (?,'sales@test.local','Sales','LOCAL','test')",user);
                execute(connection,"INSERT INTO organization_member(id,organization_id,user_id,role) VALUES (?,?,?,'OWNER')",member,org,user);
                execute(connection,"INSERT INTO customer(id,organization_id,owner_member_id,name) VALUES (?,?,?,'Buyer')",customer,org,member);
                execute(connection,"INSERT INTO customer_contact(organization_id,customer_id,full_name,email) VALUES (?,?,'Buyer','buyer@test.local')",org,customer);
                var config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),ds));
                config.getTypeHandlerRegistry().register(UUID.class,JdbcType.OTHER,UuidTypeHandler.class); config.getTypeHandlerRegistry().register(UUID.class,UuidTypeHandler.class);
                var pagination=new MybatisPlusInterceptor(); pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL)); config.addInterceptor(pagination);
                for(var resource:new String[]{"mail/MailMapper","agent/AgentWorkflowMapper","approval/ApprovalMapper","tool/ToolExecutionMapper"}) {
                    try(var xml=getClass().getResourceAsStream("/mapper/"+resource+".xml")) { new XMLMapperBuilder(xml,config,resource,config.getSqlFragments()).parse(); }
                }
                var session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(connection);
                var mapper=session.getMapper(MailMapper.class); var agent=session.getMapper(AgentWorkflowMapper.class); var approvals=session.getMapper(ApprovalMapper.class); var tools=session.getMapper(ToolExecutionMapper.class);
                var now=Instant.now(); var clock=Clock.fixed(now,ZoneOffset.UTC); var actor=new AuthPrincipal(null,user,org,member,"sales@test.local","Sales","Team","OWNER",now.plusSeconds(900));
                var account=new MailAccountRow(); account.setOrganizationId(org); account.setHost("imap.test.local"); account.setUsername("sales@test.local"); account.setEncryptedPassword("encrypted"); account.setPort(993); account.setSsl(true); account.setFolder("INBOX"); account.setEnabled(true);
                assertThat(mapper.insertAccount(account,now)).isEqualTo(1); assertThat(mapper.insertAccount(account,now)).isZero();
                var token=UUID.randomUUID(); assertThat(mapper.claim(org,token,now)).isEqualTo(1); assertThat(mapper.claim(org,UUID.randomUUID(),now)).isZero();
                assertThat(mapper.updateAccount(account,0,now)).isZero(); assertThat(mapper.finishSync(org,token,10,30,null,now)).isEqualTo(1);
                assertThat(mapper.account(org).getLastUid()).isEqualTo(30); assertThat(mapper.updateAccount(account,0,now)).isEqualTo(1);
                assertThat(mapper.dueAccounts(now.plusSeconds(90))).contains(org);
                var sync=new MailSyncService(mapper,mock(MailAccountService.class),mock(ImapMailboxClient.class),mock(PlatformTransactionManager.class),clock);
                var incoming=new InboundMail("<incoming@test.local>","buyer@test.local",List.of("sales@test.local"),"Quote","Please confirm delivery",List.of("requirements.pdf"),List.of(),now,false);
                assertThat(sync.importMessage(org,incoming)).isEqualTo(1); assertThat(sync.importMessage(org,incoming)).isEqualTo(-1);
                var page=mapper.page(new Page<>(1,20),org,member,"PENDING"); assertThat(page.getTotal()).isEqualTo(1);
                var mail=mapper.message(org,page.getRecords().get(0).getId()); assertThat(mail.getInteractionId()).isNotNull();
                assertThat(agent.selectCandidates(org,now.minusSeconds(3600),List.of(customer),1).get(0).getInteractionId()).isEqualTo(mail.getInteractionId());
                assertThat(mapper.message(UUID.randomUUID(),mail.getId())).isNull();
                assertThat(mapper.thread(org,mail.getThreadId(),UUID.randomUUID())).isEmpty();
                assertThat(count(connection,"SELECT count(*) FROM follow_up")).isEqualTo(1); assertThat(count(connection,"SELECT count(*) FROM interaction")).isEqualTo(1);
                var files=mock(FileStorageService.class); when(files.preview(List.of())).thenReturn(List.of());
                var conversations=new MailConversationService(mapper,sync,agent,files,mock(EmailConfigurationService.class),mock(ChatAnalysisService.class),clock);
                var proposed=conversations.reply(actor,mail.getId(),new MailReplyRequest("Re: Quote","We will confirm tomorrow",List.of(),0));
                assertThat(mapper.message(org,mail.getId()).getApprovalStatus()).isEqualTo("PENDING");
                assertThat(mapper.reserveReply(org,mail.getId(),1)).isZero(); assertThat(mapper.handle(org,mail.getId(),1)).isZero();
                var approval=approvals.selectApproval(org,proposed.approvalId());
                assertThat(approvals.updateDecision(org,approval.getId(),member,"APPROVED","Checked",0L,now)).isEqualTo(1);
                assertThat(tools.markActionApproved(org,approval.getActionRequestId(),now)).isEqualTo(1);
                assertThat(tools.lockActionRequestForExecution(org,approval.getActionRequestId(),now)).isEqualTo(1);
                assertThat(tools.lockActionRequestForExecution(org,approval.getActionRequestId(),now)).isZero();
                var action=tools.selectActionRequestForExecution(org,approval.getActionRequestId());
                var delivery=new MailDeliveryService(mapper,clock);
                var context=new ToolExecutionContext(org,action.getId(),proposed.runId(),null,customer,action.getIdempotencyKey(),1);
                var sent=delivery.prepare(context,"sales@test.local",List.of("buyer@test.local"),"Re: Quote","We will confirm tomorrow",List.of(),action.getPayload());
                delivery.sent(sent);
                assertThat(mapper.message(org,sent.getId()).getStatus()).isEqualTo("SENT");
                assertThat(mapper.message(org,mail.getId()).getStatus()).isEqualTo("REPLIED");
                assertThat(count(connection,"SELECT count(*) FROM follow_up WHERE status='COMPLETED'")).isEqualTo(1);
                assertThat(count(connection,"SELECT count(*) FROM interaction")).isEqualTo(2);
                assertThat(mapper.parent(org,List.of(sent.getMessageId()),"buyer@test.local",List.of("sales@test.local")).getId()).isEqualTo(sent.getId());
                assertThat(mapper.parent(org,List.of(sent.getMessageId()),"attacker@test.local",List.of("sales@test.local"))).isNull();
                assertThat(mapper.thread(org,mail.getThreadId(),member)).hasSize(2);
                assertThat(sync.importMessage(org,new InboundMail("<second@test.local>","buyer@test.local",List.of("sales@test.local"),"Re: Quote","Thanks",List.of(),List.of(sent.getMessageId()),now.plusSeconds(1),false))).isEqualTo(1);
                assertThat(mapper.thread(org,mail.getThreadId(),member)).hasSize(3);
                var second=mapper.page(new Page<>(1,20),org,member,"PENDING").getRecords().get(0);
                conversations.handle(actor,second.getId(),0);
                assertThat(mapper.message(org,second.getId()).getStatus()).isEqualTo("HANDLED");
                var unmatched=new InboundMail("<unknown@test.local>","unknown@test.local",List.of("sales@test.local"),"Hello","Question",List.of(),List.of(),now,false);
                assertThat(sync.importMessage(org,unmatched)).isZero(); var unknown=mapper.page(new Page<>(1,20),org,null,"UNMATCHED").getRecords().get(0);
                conversations.link(actor,unknown.getId(),new MailLinkRequest(customer,0)); assertThat(mapper.message(org,unknown.getId()).getInteractionId()).isNotNull();
                var retry=conversations.reply(actor,unknown.getId(),new MailReplyRequest("Re: Hello","Thank you",List.of(),1));
                var retryApproval=approvals.selectApproval(org,retry.approvalId());
                approvals.updateDecision(org,retryApproval.getId(),member,"APPROVED","Checked",0L,now);
                tools.markActionApproved(org,retryApproval.getActionRequestId(),now);
                tools.lockActionRequestForExecution(org,retryApproval.getActionRequestId(),now);
                var retryAction=tools.selectActionRequestForExecution(org,retryApproval.getActionRequestId());
                var uncertain=delivery.prepare(new ToolExecutionContext(org,retryAction.getId(),retry.runId(),null,customer,retryAction.getIdempotencyKey(),1),"sales@test.local",List.of("unknown@test.local"),"Re: Hello","Thank you",List.of(),retryAction.getPayload());
                delivery.unknown(uncertain); tools.updateActionFailed(org,retryAction.getId(),"UNKNOWN","Check mailbox",now);
                assertThat(mapper.message(org,unknown.getId()).isDeliveryUncertain()).isTrue();
                assertThat(mapper.reserveReply(org,unknown.getId(),2)).isZero();
                conversations.handle(actor,unknown.getId(),2);
                assertThat(mapper.message(org,unknown.getId()).getStatus()).isEqualTo("HANDLED");
            } finally { connection.rollback(); }
        }
    }
    private static void execute(Connection connection,String sql,Object... values) throws SQLException { try(var statement=connection.prepareStatement(sql)) { for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); statement.executeUpdate(); } }
    private static long count(Connection connection,String sql) throws SQLException { try(var statement=connection.createStatement();var rs=statement.executeQuery(sql)) { rs.next();return rs.getLong(1); } }
}
