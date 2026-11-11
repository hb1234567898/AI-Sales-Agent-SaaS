package com.yourcompany.salesagent.mail.transport;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import jakarta.mail.*;
import jakarta.mail.internet.*;

public class MailContentReader {
    private static final Pattern IDS = Pattern.compile("<[^<>\\s]{1,490}>");
    public static List<String> messageIds(String value) {
        var ids = new LinkedHashSet<String>(); var matcher = IDS.matcher(value == null ? "" : value);
        while (matcher.find() && ids.size() < 30) ids.add(matcher.group()); return List.copyOf(ids);
    }
    public InboundMail read(Message message, String fallbackId, Instant now) throws MessagingException, IOException {
        var from = addresses(message.getFrom()); var to = addresses(message.getAllRecipients());
        var references = new ArrayList<>(messageIds(header(message, "In-Reply-To")));
        for (var id : messageIds(header(message, "References"))) if (!references.contains(id)) references.add(id);
        var ids = messageIds(header(message, "Message-ID")); var names = new ArrayList<String>(); var text = new StringBuilder();
        if (message.getSize() > 2_000_000) text.append("[邮件超过 2MB，请到原邮箱查看正文与附件]");
        else extract(message, text, names, 0, new int[] { 0 });
        var received = message.getReceivedDate(); if (received == null) received = message.getSentDate();
        var occurred = received == null || received.toInstant().isAfter(now.plusSeconds(300)) ? now : received.toInstant();
        var automatic = !header(message, "Auto-Submitted").isBlank() && !header(message, "Auto-Submitted").equalsIgnoreCase("no");
        automatic |= message.isMimeType("multipart/report") || header(message, "Precedence").matches("(?i)(bulk|list|junk)");
        return new InboundMail(ids.isEmpty() ? fallbackId : ids.get(0), from.size() == 1 ? from.get(0) : "", to,
                limit(Optional.ofNullable(message.getSubject()).orElse("(无主题)"), 500), limit(text.toString().strip(), 64000),
                names, references, occurred, automatic);
    }
    private void extract(Part part, StringBuilder text, List<String> names, int depth, int[] count) throws MessagingException, IOException {
        if (depth > 12 || ++count[0] > 60 || text.length() >= 64000) return;
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null) {
            if (names.size() < 30) names.add(limit(part.getFileName() == null ? "附件" : MimeUtility.decodeText(part.getFileName()), 255)); return;
        }
        if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            String raw;
            try (var stream = part.getInputStream()) { raw = new String(stream.readNBytes(262144), charset(part)); }
            text.append(part.isMimeType("text/html") ? htmlText(raw) : raw).append('\n'); return;
        }
        if (part.isMimeType("multipart/*") && part.getContent() instanceof Multipart multipart) {
            if (part.isMimeType("multipart/alternative")) {
                for (int i = 0; i < Math.min(multipart.getCount(), 60); i++) {
                    if (multipart.getBodyPart(i).isMimeType("text/plain")) { extract(multipart.getBodyPart(i), text, names, depth + 1, count); return; }
                }
            }
            for (int i = 0; i < Math.min(multipart.getCount(), 60); i++) extract(multipart.getBodyPart(i), text, names, depth + 1, count);
        }
    }
    private static java.nio.charset.Charset charset(Part part) throws MessagingException {
        try { var name = new ContentType(part.getContentType()).getParameter("charset"); return name == null ? java.nio.charset.StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(name); }
        catch (RuntimeException | ParseException ignored) { return java.nio.charset.StandardCharsets.UTF_8; }
    }
    private static String htmlText(String html) throws IOException {
        var text = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            private boolean skip;
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) { if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) skip = true; }
            @Override public void handleEndTag(HTML.Tag tag, int pos) { if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) skip = false; if (!skip) text.append('\n'); }
            @Override public void handleText(char[] value, int pos) { if (!skip) text.append(value).append(' '); }
        }, true); return text.toString();
    }
    private static List<String> addresses(Address[] addresses) {
        if (addresses == null) return List.of();
        return Arrays.stream(addresses).filter(a -> a instanceof InternetAddress).map(a -> ((InternetAddress) a).getAddress())
                .filter(Objects::nonNull).map(a -> a.strip().toLowerCase(Locale.ROOT)).filter(a -> a.length() <= 320 && !a.contains("\r") && !a.contains("\n")).distinct().limit(50).toList();
    }
    private static String header(Message message, String name) throws MessagingException { var values = message.getHeader(name); return values == null ? "" : String.join(" ", values); }
    public static String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
