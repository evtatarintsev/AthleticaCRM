import { useForm } from "@tanstack/react-form";
import { useMemo } from "react";
import { z } from "zod";
import type {
  EmployeeId,
  Instant,
  TaskDetailResponse,
  UploadResponse,
} from "@/api/generated/contracts";
import type { PickedClient } from "@/clients/ClientPickerSheet";
import { useI18n } from "@/i18n/context";

/**
 * Значения формы задачи. Создание и редактирование пользуются одной формой: исполнитель
 * и вложения заполняются только при создании, при редактировании они меняются в карточке.
 */
export interface TaskFormValues {
  /** Заголовок; обязателен. */
  readonly title: string;
  /** Описание. */
  readonly description: string;
  /** Клиент, к которому привязана задача; имя хранится, чтобы показывать его без запроса. */
  readonly client: PickedClient | null;
  /** Срок выполнения. */
  readonly dueDate: Instant | null;
  /** Окончание срока выполнения. */
  readonly dueDateEnd: Instant | null;
  /** Исполнитель новой задачи. */
  readonly assigneeId: EmployeeId | null;
  /** Загруженные, но ещё не привязанные к новой задаче файлы. */
  readonly attachments: readonly UploadResponse[];
}

/** Пустая форма новой задачи. */
export const EMPTY_TASK_FORM: TaskFormValues = {
  title: "",
  description: "",
  client: null,
  dueDate: null,
  dueDateEnd: null,
  assigneeId: null,
  attachments: [],
};

/** Значения формы редактирования из карточки задачи [task]. */
export function taskFormValuesOf(task: TaskDetailResponse): TaskFormValues {
  return {
    title: task.title,
    description: task.description,
    client: task.clientId === null ? null : { id: task.clientId, name: task.clientName ?? "" },
    dueDate: task.dueDate,
    dueDateEnd: task.dueDateEnd,
    assigneeId: task.assigneeId,
    attachments: [],
  };
}

/** Схема заголовка: непустой после обрезки пробелов, как в KMP-клиенте. */
export function titleSchema(t: ReturnType<typeof useI18n>["t"]) {
  return z.string().trim().min(1, t("error.required"));
}

/**
 * Форма задачи с начальными значениями [defaultValues]. [onSubmit] получает значения
 * с обрезанным заголовком и вызывается, только если заголовок не пустой.
 */
export function useTaskForm(
  defaultValues: TaskFormValues,
  onSubmit: (values: TaskFormValues) => Promise<void>,
) {
  const { t } = useI18n();
  const schema = useMemo(() => titleSchema(t), [t]);
  return useForm({
    defaultValues,
    onSubmit: async ({ value }) => {
      const title = schema.safeParse(value.title);
      if (!title.success) {
        return;
      }
      await onSubmit({ ...value, title: title.data });
    },
  });
}

/** Форма задачи, созданная [useTaskForm]. */
export type TaskForm = ReturnType<typeof useTaskForm>;
