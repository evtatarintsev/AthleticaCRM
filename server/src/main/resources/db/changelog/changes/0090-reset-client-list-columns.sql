--liquibase formatted sql

--changeset dev:0090-reset-client-list-columns
-- Колонки списка клиентов сменились (пол и дата рождения ушли под имя, появился «Абонемент»):
-- сохранённые наборы сбрасываются, умолчание остаётся в ClientsDisplaySettings.
UPDATE user_display_settings
   SET settings = settings #- '{clients,columns}'
 WHERE settings #> '{clients,columns}' IS NOT NULL;
