package com.yourcompany.salesagent.mail.infrastructure;

public class MailMessageRow {
    private boolean deliveryUncertain;
    public boolean isDeliveryUncertain() { return deliveryUncertain; }
    public void setDeliveryUncertain(boolean value) { deliveryUncertain = value; }
    private java.util.UUID id;
    public java.util.UUID getId() { return id; }
    public void setId(java.util.UUID value) { id = value; }
    private java.util.UUID organizationId;
    public java.util.UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(java.util.UUID value) { organizationId = value; }
    private java.util.UUID customerId;
    public java.util.UUID getCustomerId() { return customerId; }
    public void setCustomerId(java.util.UUID value) { customerId = value; }
    private String customerName;
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String value) { customerName = value; }
    private java.util.UUID ownerMemberId;
    public java.util.UUID getOwnerMemberId() { return ownerMemberId; }
    public void setOwnerMemberId(java.util.UUID value) { ownerMemberId = value; }
    private java.util.UUID interactionId;
    public java.util.UUID getInteractionId() { return interactionId; }
    public void setInteractionId(java.util.UUID value) { interactionId = value; }
    private java.util.UUID actionRequestId;
    public java.util.UUID getActionRequestId() { return actionRequestId; }
    public void setActionRequestId(java.util.UUID value) { actionRequestId = value; }
    private java.util.UUID threadId;
    public java.util.UUID getThreadId() { return threadId; }
    public void setThreadId(java.util.UUID value) { threadId = value; }
    private java.util.UUID parentId;
    public java.util.UUID getParentId() { return parentId; }
    public void setParentId(java.util.UUID value) { parentId = value; }
    private String direction;
    public String getDirection() { return direction; }
    public void setDirection(String value) { direction = value; }
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    private String messageId;
    public String getMessageId() { return messageId; }
    public void setMessageId(String value) { messageId = value; }
    private String fromAddress;
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String value) { fromAddress = value; }
    private java.util.List<String> recipients;
    public java.util.List<String> getRecipients() { return recipients; }
    public void setRecipients(java.util.List<String> value) { recipients = value; }
    private String subject;
    public String getSubject() { return subject; }
    public void setSubject(String value) { subject = value; }
    private String bodyText;
    public String getBodyText() { return bodyText; }
    public void setBodyText(String value) { bodyText = value; }
    private java.util.List<String> attachmentNames;
    public java.util.List<String> getAttachmentNames() { return attachmentNames; }
    public void setAttachmentNames(java.util.List<String> value) { attachmentNames = value; }
    private String referencesHeader;
    public String getReferencesHeader() { return referencesHeader; }
    public void setReferencesHeader(String value) { referencesHeader = value; }
    private java.time.Instant occurredAt;
    public java.time.Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(java.time.Instant value) { occurredAt = value; }
    private java.time.Instant createdAt;
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant value) { createdAt = value; }
    private java.util.UUID replyApprovalId;
    public java.util.UUID getReplyApprovalId() { return replyApprovalId; }
    public void setReplyApprovalId(java.util.UUID value) { replyApprovalId = value; }
    private String approvalStatus;
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String value) { approvalStatus = value; }
    private String actionStatus;
    public String getActionStatus() { return actionStatus; }
    public void setActionStatus(String value) { actionStatus = value; }
    private long version;
    public long getVersion() { return version; }
    public void setVersion(long value) { version = value; }
}
