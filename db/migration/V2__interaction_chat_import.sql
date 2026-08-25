-- 允许把微信、WhatsApp 等聊天记录作为独立的客户互动类型保存。
-- 初版保留整段聊天原文，不对不同平台的导出格式进行不可靠的自动拆分。
ALTER TABLE interaction
    DROP CONSTRAINT ck_interaction_type;

ALTER TABLE interaction
    ADD CONSTRAINT ck_interaction_type CHECK (type IN (
        'EMAIL_SENT', 'EMAIL_RECEIVED', 'EMAIL_OPENED', 'CALL', 'MEETING',
        'NOTE', 'CHAT_IMPORT', 'TASK_CREATED', 'TASK_COMPLETED', 'CRM_UPDATE'
    ));

COMMENT ON COLUMN interaction.body_text IS '互动正文或导入的聊天原文；访问和保留策略应按客户敏感数据处理。';
COMMENT ON COLUMN interaction.source IS '互动来源，例如 INTERNAL、WECHAT、WHATSAPP 或 OTHER。';
