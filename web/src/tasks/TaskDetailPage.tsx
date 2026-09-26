import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon, PlusIcon, XIcon } from "lucide-react";
import { useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import type { EmployeeId, Instant, TaskId, TaskStatus, UploadId } from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { AssigneeSelect } from "./AssigneeSelect";
import { DateTimeField } from "./DateTimeField";
import { TaskStatusBadge } from "./TaskStatusBadge";
import { taskStatusLabelKey } from "./taskStatus";

/** Все статусы задач в порядке меню смены статуса. */
const ALL_STATUSES: readonly TaskStatus[] = ["PENDING", "IN_PROGRESS", "PAUSED", "COMPLETED"];

/**
 * Карточка задачи: статус, исполнитель, клиент, сроки и вложения. Заголовок и описание
 * задаются только при создании — в KMP-клиенте карточка их не редактирует, только читает.
 */
export function TaskDetailPage({
  api,
  taskId,
}: {
  readonly api: ApiClient;
  readonly taskId: TaskId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const detailQuery = apiQuery(api, branchId, "tasks/detail", { taskId });
  const detail = useQuery(detailQuery);
  const employees = useQuery({
    ...apiQuery(api, branchId, "employees/list"),
    select: (r) => r.employees,
  });
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const reload = async () => {
    await queryClient.invalidateQueries({ queryKey: detailQuery.queryKey });
  };

  const changeStatus = async (status: TaskStatus) => {
    const result = await api.call("tasks/status", { taskIds: [taskId], status });
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await reload();
  };

  const setAssignee = async (assigneeId: EmployeeId | null) => {
    const result =
      assigneeId === null
        ? await api.call("tasks/unassign", { taskIds: [taskId] })
        : await api.call("tasks/assign", { taskIds: [taskId], assigneeId });
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await reload();
  };

  const updateDates = async (dueDate: Instant | null, dueDateEnd: Instant | null) => {
    const task = detail.data;
    if (task === undefined) {
      return;
    }
    const result = await api.call("tasks/update", {
      id: taskId,
      title: task.title,
      description: task.description,
      clientId: task.clientId,
      dueDate,
      dueDateEnd,
    });
    if (!result.ok) {
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await reload();
  };

  const attach = async (file: File) => {
    setUploading(true);
    const uploaded = await uploadFile(api, file);
    if (!uploaded.ok) {
      setUploading(false);
      setError(apiErrorMessage(t, uploaded.error));
      return;
    }
    const attached = await api.call("tasks/attach", { taskId, uploadId: uploaded.value.id });
    setUploading(false);
    if (!attached.ok) {
      setError(apiErrorMessage(t, attached.error));
      return;
    }
    await reload();
  };

  const detach = async (uploadId: UploadId) => {
    const result = await api.call("tasks/detach", { taskId, uploadId });
    if (result.ok) {
      await reload();
    }
  };

  return (
    <section className="max-w-2xl pb-10">
      <Button variant="ghost" size="sm" asChild className="-ml-2 mb-4">
        <Link to="/tasks">
          <ArrowLeftIcon aria-hidden />
          {t("action.back")}
        </Link>
      </Button>
      {detail.isPending && (
        <div className="space-y-4">
          <Skeleton className="h-8 w-2/3" />
          <Skeleton className="h-32 w-full" />
        </div>
      )}
      {detail.isError && <FormAlert message={t("tasks.loadError")} />}
      {detail.data !== undefined && (
        <>
          <PageHeader title={detail.data.title} />
          {error !== null && <FormAlert message={error} />}
          <div className="mb-4 flex items-center justify-between gap-3">
            <TaskStatusBadge status={detail.data.status} />
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

          {detail.data.description !== "" && (
            <div className="mb-4 space-y-1">
              <span className="text-sm font-medium text-muted-foreground">
                {t("tasks.field.description")}
              </span>
              <p className="text-sm whitespace-pre-wrap">{detail.data.description}</p>
            </div>
          )}

          <div className="mb-4">
            <AssigneeSelect
              label={t("tasks.field.assignee")}
              employees={employees.data ?? []}
              value={detail.data.assigneeId}
              onChange={(assigneeId) => {
                void setAssignee(assigneeId);
              }}
            />
          </div>

          {detail.data.clientName !== null && (
            <div className="mb-4 space-y-1">
              <span className="text-sm font-medium text-muted-foreground">
                {t("tasks.field.client")}
              </span>
              <p className="text-sm">{detail.data.clientName}</p>
            </div>
          )}

          <div className="mb-4 space-y-4">
            <DateTimeField
              label={t("tasks.field.dueDate")}
              value={detail.data.dueDate}
              onChange={(dueDate) => {
                void updateDates(dueDate, detail.data.dueDateEnd);
              }}
            />
            <DateTimeField
              label={t("tasks.field.dueDateEnd")}
              value={detail.data.dueDateEnd}
              onChange={(dueDateEnd) => {
                void updateDates(detail.data.dueDate, dueDateEnd);
              }}
            />
          </div>

          <div className="space-y-2">
            <span className="text-sm font-medium">{t("tasks.field.attachments")}</span>
            {detail.data.attachments.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("tasks.noAttachments")}</p>
            ) : (
              <ul className="divide-y">
                {detail.data.attachments.map((attachment) => (
                  <li key={attachment.id} className="flex items-center gap-2 py-2 text-sm">
                    <a
                      href={attachment.url}
                      target="_blank"
                      rel="noreferrer"
                      className="flex-1 truncate text-primary underline"
                    >
                      {attachment.originalName}
                    </a>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("tasks.removeAttachment", { name: attachment.originalName })}
                      onClick={() => {
                        void detach(attachment.id);
                      }}
                    >
                      <XIcon aria-hidden />
                    </Button>
                  </li>
                ))}
              </ul>
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
        </>
      )}
    </section>
  );
}
