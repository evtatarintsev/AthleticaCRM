-- liquibase formatted sql

--changeset etatarintsev:0048-task-tracker
CREATE TABLE tasks (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    org_id uuid NOT NULL REFERENCES organizations(id),
    created_by uuid NOT NULL REFERENCES employees(id),
    assignee_id uuid REFERENCES employees(id),
    client_id uuid REFERENCES clients(id),
    title text NOT NULL,
    description text NOT NULL,
    status text NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PAUSED', 'COMPLETED')),
    due_date timestamptz,
    due_date_end timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    updated_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE INDEX tasks_org_assignee_idx ON tasks(org_id, assignee_id);
CREATE INDEX tasks_org_created_by_idx ON tasks(org_id, created_by);
CREATE INDEX tasks_org_status_idx ON tasks(org_id, status);
CREATE INDEX tasks_org_due_date_idx ON tasks(org_id, due_date);
CREATE INDEX tasks_client_idx ON tasks(client_id) WHERE client_id IS NOT NULL;

CREATE TABLE task_attachments (
    id uuid PRIMARY KEY DEFAULT uuidv7(),
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    upload_id uuid NOT NULL REFERENCES uploads(id),
    uploaded_at timestamptz NOT NULL DEFAULT NOW(),
    uploaded_by uuid NOT NULL REFERENCES employees(id)
);

CREATE INDEX task_attachments_task_idx ON task_attachments(task_id);
