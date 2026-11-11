package com.yourcompany.salesagent.mail.transport;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.junit.jupiter.api.Test;

class MailContentReaderTests {
    private MimeMessage message() throws Exception { var m=new MimeMessage(Session.getInstance(new Properties())); m.setFrom("buyer@example.test"); m.setRecipients(Message.RecipientType.TO,"sales@example.test"); return m; }
    @Test void extractsThreadHeadersAndTreatsHtmlOnlyAsText() throws Exception {
        var m=message(); m.setHeader("Message-ID","<reply@example.test>"); m.setHeader("In-Reply-To","<sent@example.test>");
        m.setContent("<p>确认预算 20 万</p><script>alert('not executed')</script><img src='https://example.test/pixel'>","text/html; charset=UTF-8");
        m.saveChanges(); m.setHeader("Message-ID","<reply@example.test>");
        var result=new MailContentReader().read(m,"<fallback@local.invalid>",Instant.now());
        assertThat(result.messageId()).isEqualTo("<reply@example.test>"); assertThat(result.references()).containsExactly("<sent@example.test>");
        assertThat(result.body()).contains("确认预算").doesNotContain("alert", "<img", "<script");
    }
    @Test void keepsOnlyOneAlternativeBodyAndDoesNotReadAttachments() throws Exception {
        var m=message(); var multipart=new MimeMultipart("mixed");
        var plain=new MimeBodyPart(); plain.setText("hello","UTF-8"); multipart.addBodyPart(plain);
        var attachment=new MimeBodyPart(); attachment.setFileName("quote.pdf"); attachment.setDisposition(Part.ATTACHMENT); attachment.setText("private binary"); multipart.addBodyPart(attachment);
        m.setContent(multipart);
        m.saveChanges();
        var result=new MailContentReader().read(m,"<fallback@local.invalid>",Instant.now());
        assertThat(result.body()).isEqualTo("hello"); assertThat(result.attachmentNames()).containsExactly("quote.pdf");
    }
    @Test void identifiesAutomaticMailAndRejectsHeaderInjectionAsThreadIds() throws Exception {
        var m=message(); m.setText("away"); m.setHeader("Auto-Submitted","auto-replied");
        assertThat(new MailContentReader().read(m,"<fallback@local.invalid>",Instant.now()).automatic()).isTrue();
        assertThat(MailContentReader.messageIds("<id@example.test>\r\nX-Injected: test <id@example.test>")).containsExactly("<id@example.test>");
    }
}
