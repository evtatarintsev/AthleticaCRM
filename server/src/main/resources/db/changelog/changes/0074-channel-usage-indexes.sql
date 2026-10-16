--liquibase formatted sql

--changeset dev:0074-channel-usage-indexes

-- Проверка «через канал уже шли сообщения» перед удалением канала и FK-проверка самого DELETE
-- ищут историю по каналу; без индексов это полный проход по доставкам и входящим.
CREATE INDEX idx_message_deliveries_channel
    ON message_deliveries (channel_integration_id);

CREATE INDEX idx_inbound_receipts_integration
    ON inbound_receipts (integration_id);
