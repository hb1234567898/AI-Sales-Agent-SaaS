package com.yourcompany.salesagent.mail.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import com.yourcompany.salesagent.tool.email.*;
import com.yourcompany.salesagent.tool.domain.ToolExecutionContext;
import com.yourcompany.salesagent.mail.infrastructure.MailMessageRow;
import com.yourcompany.salesagent.file.application.FileStorageService;

class MailSendSafetyTests {
    @Test void smtpErrorsAreUnknownAndNeverAutomaticallyRetried() {
        var sender=mock(JavaMailSender.class); var config=mock(EmailConfigurationService.class); var delivery=mock(MailDeliveryService.class);
        var runtime=new EmailRuntimeConfiguration("smtp.test.local",587,"sales@test.local","secret","sales@test.local",true,true,true);
        when(config.requireRuntimeConfiguration(any())).thenReturn(runtime); when(config.mailSender(runtime)).thenReturn(sender);
        var tracked=new MailMessageRow(); tracked.setMessageId("<tracked@test.local>"); when(delivery.prepare(any(),any(),any(),any(),any(),any(),any())).thenReturn(tracked);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("sensitive transport detail")).when(sender).send(any(MimeMessage.class));
        var tool=new SendEmailTool(sender,config,mock(FileStorageService.class),delivery);
        var context=new ToolExecutionContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,UUID.randomUUID(),"key",1);
        var result=tool.execute(context,Map.of("to","buyer@test.local","subject","Quote","body","hello"));
        assertThat(result.success()).isFalse(); assertThat(result.message()).contains("核对").doesNotContain("sensitive");
        verify(sender,times(1)).send(any(MimeMessage.class)); verify(delivery).unknown(tracked); verify(delivery,never()).sent(any());
    }
    @Test void preexistingDeliveryRecordsPreventAnotherSmtpSend() {
        var sender=mock(JavaMailSender.class); var config=mock(EmailConfigurationService.class); var delivery=mock(MailDeliveryService.class);
        when(config.requireRuntimeConfiguration(any())).thenReturn(new EmailRuntimeConfiguration("smtp.test.local",587,"sales@test.local","secret","sales@test.local",true,true,true));
        when(delivery.prepare(any(),any(),any(),any(),any(),any(),any())).thenThrow(new MailWorkflowException("该邮件动作已有发送记录"));
        var tool=new SendEmailTool(sender,config,mock(FileStorageService.class),delivery);
        var result=tool.execute(new ToolExecutionContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),null,UUID.randomUUID(),"key",1),Map.of("to","buyer@test.local"));
        assertThat(result.success()).isFalse(); verifyNoInteractions(sender);
    }
}
