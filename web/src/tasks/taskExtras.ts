import type { EmployeeId, Instant, UploadResponse } from "@/api/generated/contracts";

/**
 * Часть формы создания задачи вне TanStack Form: исполнитель, сроки и уже загруженные,
 * но ещё не привязанные к задаче вложения. Паритет с `TaskForm` KMP-клиента.
 */
export interface TaskExtras {
  readonly assigneeId: EmployeeId | null;
  readonly dueDate: Instant | null;
  readonly dueDateEnd: Instant | null;
  readonly attachments: readonly UploadResponse[];
}

/** Пустая форма создания задачи. */
export const EMPTY_TASK_EXTRAS: TaskExtras = {
  assigneeId: null,
  dueDate: null,
  dueDateEnd: null,
  attachments: [],
};
