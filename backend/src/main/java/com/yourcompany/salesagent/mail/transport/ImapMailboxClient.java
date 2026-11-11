package com.yourcompany.salesagent.mail.transport;

import java.time.Instant;
import java.util.*;
import jakarta.mail.*;
import org.springframework.stereotype.Component;
import com.yourcompany.salesagent.mail.infrastructure.MailAccountRow;

@Component
public class ImapMailboxClient {
    public record Batch(long validity, long lastUid, List<InboundMail> messages) {}
    public void test(MailAccountRow account, String password) throws MessagingException {
        try (var store = connect(account, password)) {
            try (var folder = store.getFolder(account.getFolder())) { folder.open(Folder.READ_ONLY); }
        }
    }
    public Batch fetch(MailAccountRow account, String password, Instant now) throws Exception {
        var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        try (var store = connect(account, password); var folder = store.getFolder(account.getFolder())) {
            folder.open(Folder.READ_ONLY);
            if (!(folder instanceof UIDFolder uids)) throw new MessagingException("Mailbox does not support persistent UIDs");
            var validity = uids.getUIDValidity(); var last = account.getUidValidity() == validity ? account.getLastUid() : 0;
            var count = folder.getMessageCount(); if (count == 0) return new Batch(validity, last, List.of());
            var highest = uids.getUID(folder.getMessage(count));
            if (last >= highest) return new Batch(validity, last, List.of());
            int start = Math.max(1, count - 199);
            if (last > 0) {
                int low = 1, high = count;
                while (low < high) {
                    int middle = low + (high - low) / 2;
                    if (uids.getUID(folder.getMessage(middle)) <= last) low = middle + 1;
                    else high = middle;
                }
                start = low;
            }
            var batch = folder.getMessages(start, Math.min(count, start + 49));
            var profile = new FetchProfile(); profile.add(UIDFolder.FetchProfileItem.UID); profile.add(FetchProfile.Item.ENVELOPE);
            profile.add("Message-ID"); profile.add("In-Reply-To"); profile.add("References"); folder.fetch(batch, profile);
            var messages = new ArrayList<InboundMail>();
            var reader = new MailContentReader();
            long processed = last == 0 ? Math.max(0, uids.getUID(batch[0]) - 1) : last;
            for (var message : batch) {
                if (System.nanoTime() > deadline) return new Batch(validity, processed, List.copyOf(messages));
                if (message == null || message.isExpunged()) continue;
                var identity = UUID.nameUUIDFromBytes((account.getHost()+"/"+account.getUsername()+"/"+account.getFolder()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var fallback = "<imap-" + identity + "-" + validity + "-" + uids.getUID(message) + "@local.invalid>";
                messages.add(reader.read(message, fallback, now));
                processed = uids.getUID(message);
            }
            return new Batch(validity, processed, List.copyOf(messages));
        }
    }
    private Store connect(MailAccountRow account, String password) throws MessagingException {
        var protocol = account.isSsl() ? "imaps" : "imap"; var props = new Properties(); var prefix = "mail." + protocol + ".";
        props.put(prefix + "connectiontimeout", "10000"); props.put(prefix + "timeout", "10000"); props.put(prefix + "writetimeout", "10000");
        props.put(prefix + "peek", "true"); props.put(prefix + "ssl.checkserveridentity", "true");
        props.put(prefix + "starttls.enable", String.valueOf(!account.isSsl())); props.put(prefix + "starttls.required", String.valueOf(!account.isSsl()));
        var store = Session.getInstance(props).getStore(protocol);
        try { store.connect(account.getHost(), account.getPort(), account.getUsername(), password); return store; }
        catch (MessagingException error) { try { store.close(); } catch (MessagingException ignored) {} throw error; }
    }
}
