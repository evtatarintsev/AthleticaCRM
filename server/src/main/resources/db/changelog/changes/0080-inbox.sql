--liquibase formatted sql

--changeset dev:0080-inbox

-- Список «Входящих»: беседы организации, свежие первыми.
CREATE INDEX idx_conversations_org_last_message
    ON conversations (org_id, last_message_at DESC)
    WHERE last_message_at IS NOT NULL;

-- Горизонт прочтения появляется в интерфейсе впервые: существующую переписку считаем прочитанной,
-- чтобы история не превратилась в непрочитанные.
INSERT INTO conversation_read_state (conversation_id, org_id, last_read_at)
SELECT id, org_id, now()
FROM conversations
WHERE last_message_at IS NOT NULL
ON CONFLICT (conversation_id) DO UPDATE SET last_read_at = GREATEST(conversation_read_state.last_read_at, excluded.last_read_at);
