import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PencilIcon, PlusIcon } from "lucide-react";
import { useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  TaskIdSchema,
  type EmployeeId,
  type TaskDetailResponse,
  type TaskId,
  type TaskStatus,
  type UploadId,
} from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SheetFooter } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { AttachmentList } from "@/ui/attachments/AttachmentList";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { AssigneeSheet } from "./AssigneeSheet";
import { PickerField } from "./TaskFields";
import { TaskEditSheet } from "./TaskEditSheet";
import { TaskStatusBadge } from "./TaskStatusBadge";
import { taskStatusLabelKey } from "./taskStatus";

/** Все статусы задач в порядке меню смены статуса. */
const ALL_STATUSES: readonly TaskStatus[] = ["PENDING", "IN_PROGRESS", "PAUSED", "COMPLETED"];

/**
 * Заглушка идентификатора для ключа запроса закрытой панели: запрос с ней не отправляется
 * (`enabled: false`), но ключ должен иметь тот же тип.
 */
const NO_TASK = TaskIdSchema.parse("00000000-0000-0000-0000-000000000000");

/** Вложенная панель карточки, открытая сейчас. */
type CardPicker = "assignee" | "edit" | null;

/**
 * Панель карточки задачи [taskId] поверх списка; `null` — панель закрыта. Статус,
 * исполнитель и вложения меняются сразу; заголовок, описание, клиент и сроки —
 * во вложенной панели редактирования.
 */
export function TaskSheet({
  api,
  taskId,
  onClose,
}: {
  readonly api: ApiClient;
  readonly taskId: TaskId | null;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const detail = useQuery({
    ...apiQuery(api, branchId, "tasks/detail", { taskId: taskId ?? NO_TASK }),
    enabled: taskId !== null,
  });
  return (
    <EditSheet
      open={taskId !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={detail.data?.title ?? t("tasks.cardTitle")}
      size="lg"
    >
      {detail.isPending && (
        <EditSheetBody>
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-32 w-full" />
        </EditSheetBody>
      )}
      {detail.isError && (
        <EditSheetBody>
          <FormAlert message={t("tasks.loadError")} />
        </EditSheetBody>
      )}
      {detail.data !== undefined && <TaskCard api={api} task={detail.data} />}
    </EditSheet>
  );
}

/** Содержимое карточки загруженной задачи [task]. */
function TaskCard({ api, task }: { readonly api: ApiClient; readonly task: TaskDetailResponse }) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [picker, setPicker] = useState<CardPicker>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const reload = () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: apiQuery(api, branchId, "tasks/detail", { taskId: task.id }).queryKey,
      }),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "tasks/list"] }),
    ]);

  const changeStatus = async (status: TaskStatus) => {
    setError(null);
    const result = await api.call("tasks/status", { taskIds: [task.id], status });
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await reload();
  };

  const assign = async (assigneeId: EmployeeId | null): Promise<string | null> => {
    const result =
      assigneeId === null
        ? await api.call("tasks/unassign", { taskIds: [task.id] })
        : await api.call("tasks/assign", { taskIds: [task.id], assigneeId });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await reload();
    return null;
  };

  const attach = async (file: File) => {
    setError(null);
    setUploading(true);
    const uploaded = await uploadFile(api, file);
    if (!uploaded.ok) {
      setUploading(false);
      setError(apiErrorMessage(t, uploaded.error));
      return;
    }
    const attached = await api.call("tasks/attach", {
      taskId: task.id,
      uploadId: uploaded.value.id,
    });
    setUploading(false);
    if (!attached.ok) {
      setError(apiErrorMessage(t, attached.error));
      return;
    }
    await reload();
  };

  const detach = async (uploadId: UploadId) => {
    setError(null);
    const result = await api.call("tasks/detach", { taskId: task.id, uploadId });
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await reload();
  };

  return (
    <>
      <EditSheetBody>
        {error !== null && <FormAlert message={error} />}
        <div className="flex items-center justify-between gap-3">
          <TaskStatusBadge status={task.status} />
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="sm">
                {t("tasks.changeStatus")}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {ALL_STATUSES.map((status) => (
                <DropdownMenuItem
                  key={status}
                  onSelect={() => {
                    void changeStatus(status);
                  }}
                >
                  {t(taskStatusLabelKey(status))}
                </DropdownMenuItem>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <PickerField
          label={t("tasks.field.assignee")}
          value={task.assigneeName}
          emptyText={t("tasks.assigneeUnassigned")}
          onOpen={() => {
            setPicker("assignee");
          }}
        />

        <ReadOnlyField label={t("tasks.field.client")} value={task.clientName} />
        <ReadOnlyField
          label={t("tasks.field.dueDate")}
          value={task.dueDate === null ? null : format.dateTime(task.dueDate)}
        />
        <ReadOnlyField
          label={t("tasks.field.dueDateEnd")}
          value={task.dueDateEnd === null ? null : format.dateTime(task.dueDateEnd)}
        />
        {task.description !== "" && (
          <div className="space-y-1">
            <span className="text-sm font-medium text-muted-foreground">
              {t("tasks.field.description")}
            </span>
            <p className="text-sm whitespace-pre-wrap">{task.description}</p>
          </div>
        )}

        <div className="space-y-2">
          <span className="text-sm font-medium">{t("tasks.field.attachments")}</span>
          {task.attachments.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("tasks.noAttachments")}</p>
          ) : (
            <AttachmentList
              items={task.attachments.map((attachment) => ({
                key: attachment.id,
                name: attachment.originalName,
                file: attachment,
              }))}
              onRemove={(item) => {
                if (item.file !== null) {
                  void detach(item.file.id);
                }
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
                void attach(file);
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
      </EditSheetBody>
      <SheetFooter className="flex-row justify-end border-t">
        <Button
          type="button"
          onClick={() => {
            setPicker("edit");
          }}
        >
          <PencilIcon aria-hidden />
          {t("action.edit")}
        </Button>
      </SheetFooter>

      <AssigneeSheet
        api={api}
        open={picker === "assignee"}
        onOpenChange={(open) => {
          setPicker(open ? "assignee" : null);
        }}
        current={task.assigneeId}
        onChoose={assign}
      />
      <TaskEditSheet
        api={api}
        task={task}
        open={picker === "edit"}
        onOpenChange={(open) => {
          setPicker(open ? "edit" : null);
        }}
      />
    </>
  );
}

/** Поле карточки только для чтения: подпись [label] и значение [value] или прочерк. */
function ReadOnlyField({
  label,
  value,
}: {
  readonly label: string;
  readonly value: string | null;
}) {
  return (
    <div className="space-y-1">
      <span className="text-sm font-medium text-muted-foreground">{label}</span>
      <p className="text-sm">{value ?? "—"}</p>
    </div>
  );
}
