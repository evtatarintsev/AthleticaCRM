import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { TaskDetailResponse } from "@/api/generated/contracts";
import { ClientPickerSheet } from "@/clients/ClientPickerSheet";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { TaskFields } from "./TaskFields";
import { taskFormValuesOf, useTaskForm } from "./taskFormValues";

/**
 * Панель редактирования задачи [task] поверх карточки: заголовок, описание, клиент и
 * сроки. «Сохранить» отправляет все эти поля одним запросом; исполнитель, статус и
 * вложения меняются в карточке и здесь не трогаются.
 */
export function TaskEditSheet({
  api,
  task,
  open,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly task: TaskDetailResponse;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("tasks.editTitle")}>
      <TaskEditForm api={api} task={task} />
    </EditSheet>
  );
}

/** Форма редактирования; монтируется заново при каждом открытии и берёт текущие значения задачи. */
function TaskEditForm({
  api,
  task,
}: {
  readonly api: ApiClient;
  readonly task: TaskDetailResponse;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close } = useEditSheet();
  const branchId = useSession(api).currentBranch.id;
  const [failure, setFailure] = useState<string | null>(null);
  const [clientPickerOpen, setClientPickerOpen] = useState(false);

  const form = useTaskForm(taskFormValuesOf(task), async (values) => {
    setFailure(null);
    const result = await api.call("tasks/update", {
      id: task.id,
      title: values.title,
      description: values.description,
      clientId: values.client?.id ?? null,
      dueDate: values.dueDate,
      dueDateEnd: values.dueDateEnd,
    });
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: apiQuery(api, branchId, "tasks/detail", { taskId: task.id }).queryKey,
      }),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "tasks/list"] }),
    ]);
    close();
  });

  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
          nested={
            <ClientPickerSheet
              api={api}
              branchId={branchId}
              open={clientPickerOpen}
              onOpenChange={setClientPickerOpen}
              title={t("tasks.field.client")}
              mode="single"
              unavailable={new Map()}
              onSubmit={([client]) => {
                form.setFieldValue("client", client ?? null);
                return Promise.resolve(null);
              }}
            />
          }
        >
          {failure !== null && <FormAlert message={failure} />}
          <TaskFields
            form={form}
            onPickClient={() => {
              setClientPickerOpen(true);
            }}
          />
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
