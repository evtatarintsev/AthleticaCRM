--liquibase formatted sql

--changeset dev:0071-drop-channel-type

-- Колонка channel_type избыточна: вид канала выводится из config (jsonb-дискриминатор `type`).
ALTER TABLE channel_integrations DROP COLUMN channel_type;

-- Вид канала «личный кабинет» (in_app) удалён: сообщения беседы и так видны в ЛК, отдельная интеграция
-- не нужна. Удаляем такие интеграции вместе с их записями доставки (сами сообщения бесед остаются).
DELETE FROM message_deliveries
WHERE channel_integration_id IN (SELECT id FROM channel_integrations WHERE config ->> 'type' = 'in_app');

DELETE FROM channel_integrations WHERE config ->> 'type' = 'in_app';
