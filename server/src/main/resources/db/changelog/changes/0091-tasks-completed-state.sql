--liquibase formatted sql

--changeset dev:0091-tasks-completed-state
-- Момент завершения есть ровно у завершённой задачи: TaskState.Completed читается с completed_at.
-- Завершённым без момента проставляется момент последнего изменения, у незавершённых момент сбрасывается.
UPDATE tasks SET completed_at = updated_at WHERE status = 'COMPLETED' AND completed_at IS NULL;
UPDATE tasks SET completed_at = NULL WHERE status <> 'COMPLETED' AND completed_at IS NOT NULL;

ALTER TABLE tasks
    ADD CONSTRAINT ck_tasks_completed_at CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL));
