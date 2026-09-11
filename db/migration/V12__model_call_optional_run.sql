-- V12: 模型调用不一定都来自 Agent 运行，例如客户档案里的手动聊天分析。
ALTER TABLE model_call
    ALTER COLUMN run_id DROP NOT NULL;
