import { useQueryClient } from "@tanstack/react-query";
import { PlusIcon } from "lucide-react";
import { useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import { TaskIdSchema, type UploadResponse } from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { ClientPickerSheet } from "@/clients/ClientPickerSheet";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { AttachmentList } from "@/ui/attachments/AttachmentList";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { AssigneeSheet } from "./AssigneeSheet";
import { PickerField, TaskFields } from "./TaskFields";
import { EMPTY_TASK_FORM, useTaskForm } from "./taskFormValues";
import { useEmployees } from "./tasksQueries";

/** Вложенная панель формы создания, открытая сейчас. */
type CreatePicker = "client" | "assignee" | null;

/**
 * Панель создания задачи: заголовок, описание, клиент, исполнитель, сроки и вложения.
 * После создания закрывается; новая задача появляется в списке.
 */
export function TaskCreateSheet({
  api,
  open,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("tasks.newTitle")}>
      <TaskCreateForm api={api} />
    </EditSheet>
  );
}

/** Форма создания задачи; монтируется заново при каждом открытии панели. */
function TaskCreateForm({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close } = useEditSheet();
  const branchId = useSession(api).currentBranch.id;
  const employees = useEmployees(api);
  const [failure, setFailure] = useState<string | null>(null);
  const [picker, setPicker] = useState<CreatePicker>(null);

  const form = useTaskForm(EMPTY_TASK_FORM, async (values) => {
    setFailure(null);
    const taskId = TaskIdSchema.parse(uuidv7());
    const created = await api.call("tasks/create", {
      id: taskId,
      title: values.title,
      description: values.description,
      clientId: values.client?.id ?? null,
      dueDate: values.dueDate,
      dueDateEnd: values.dueDateEnd,
      assigneeId: values.assigneeId,
    });
    if (!created.ok) {
      setFailure(apiErrorMessage(t, created.error));
      return;
    }
    await Promise.all(
      values.attachments.map((attachment) =>
        api.call("tasks/attach", { taskId, uploadId: attachment.id }),
      ),
    );
    await queryClient.invalidateQueries({ queryKey: ["api", branchId, "tasks/list"] });
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
          submitLabel={t("action.create")}
          onSubmit={() => {
            void form.handleSubmit();
          }}
          nested={
            <>
              <ClientPickerSheet
                api={api}
                branchId={branchId}
                open={picker === "client"}
                onOpenChange={(next) => {
                  setPicker(next ? "client" : null);
                }}
                title={t("tasks.field.client")}
                mode="single"
                unavailable={new Map()}
                onSubmit={([client]) => {
                  form.setFieldValue("client", client ?? null);
                  return Promise.resolve(null);
                }}
              />
              <form.Subscribe selector={(state) => state.values.assigneeId}>
                {(assigneeId) => (
                  <AssigneeSheet
                    api={api}
                    open={picker === "assignee"}
                    onOpenChange={(next) => {
                      setPicker(next ? "assignee" : null);
                    }}
                    current={assigneeId}
                    onChoose={(next) => {
                      form.setFieldValue("assigneeId", next);
                      return Promise.resolve(null);
                    }}
                  />
                )}
              </form.Subscribe>
            </>
          }
        >
          {failure !== null && <FormAlert message={failure} />}
          <TaskFields
            form={form}
            onPickClient={() => {
              setPicker("client");
            }}
          />
          <form.Field name="assigneeId">
            {(field) => (
              <PickerField
                label={t("tasks.field.assignee")}
                value={
                  (employees.data ?? []).find((employee) => employee.id === field.state.value)
                    ?.name ?? null
                }
                emptyText={t("tasks.assigneeUnassigned")}
                onOpen={() => {
                  setPicker("assignee");
                }}
              />
            )}
          </form.Field>
          <form.Field name="attachments">
            {(field) => (
              <NewAttachments
                api={api}
                attachments={field.state.value}
                onChange={(attachments) => {
                  field.handleChange(attachments);
                }}
                onError={setFailure}
              />
            )}
          </form.Field>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}

/**
 * Вложения новой задачи: файлы загружаются сразу, а привязываются к задаче после её
 * создания. [onError] получает текст ошибки загрузки.
 */
function NewAttachments({
  api,
  attachments,
  onChange,
  onError,
}: {
  readonly api: ApiClient;
  readonly attachments: readonly UploadResponse[];
  readonly onChange: (attachments: readonly UploadResponse[]) => void;
  readonly onError: (message: string) => void;
}) {
  const { t } = useI18n();
  const [uploading, setUploading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const upload = async (file: File) => {
    setUploading(true);
    const uploaded = await uploadFile(api, file);
    setUploading(false);
    if (!uploaded.ok) {
      onError(apiErrorMessage(t, uploaded.error));
      return;
    }
    onChange([...attachments, uploaded.value]);
  };

  return (
    <div className="space-y-2">
      <span className="text-sm font-medium">{t("tasks.field.attachments")}</span>
      {attachments.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("tasks.noAttachments")}</p>
      ) : (
        <AttachmentList
          items={attachments.map((attachment) => ({
            key: attachment.id,
            name: attachment.originalName,
            file: attachment,
          }))}
          onRemove={(item) => {
            onChange(attachments.filter((a) => a.id !== item.key));
          }}
          removeLabel={(name) => t("tasks.removeAttachment", { name })}
        />
      )}
      <input
        ref={fileInput}
        type="file"
        tabIndex={-1}
        aria-hidden
        className="sr-only"
        onChange={(event) => {
          const file = event.target.files?.item(0) ?? null;
          event.target.value = "";
          if (file !== null) {
            void upload(file);
          }
        }}
      />
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={uploading}
        aria-busy={uploading}
        onClick={() => {
          fileInput.current?.click();
        }}
      >
        <PlusIcon aria-hidden />
        {uploading ? t("tasks.uploading") : t("tasks.attachFile")}
      </Button>
    </div>
  );
}
