import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { PlusIcon, XIcon } from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import { TaskIdSchema } from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { TextAreaField, TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { AssigneeSelect } from "./AssigneeSelect";
import { DateTimeField } from "./DateTimeField";
import { EMPTY_TASK_EXTRAS, type TaskExtras } from "./taskExtras";

/** Значения формы задачи, управляемые TanStack Form. */
interface TaskFormValues {
  readonly title: string;
  readonly description: string;
}

/** Схема формы: заголовок — единственное обязательное поле, как в KMP-клиенте. */
function taskFormSchema(t: ReturnType<typeof useI18n>["t"]) {
  return z.object({
    title: z.string().trim().min(1, t("error.required")),
    description: z.string(),
  });
}

/** Создание новой задачи: заголовок, описание, исполнитель, сроки и вложения. */
export function TaskCreatePage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const branchId = useSession(api).currentBranch.id;
  const employees = useQuery({
    ...apiQuery(api, branchId, "employees/list"),
    select: (r) => r.employees,
  });
  const schema = useMemo(() => taskFormSchema(t), [t]);
  const [extras, setExtras] = useState<TaskExtras>(EMPTY_TASK_EXTRAS);
  const [uploading, setUploading] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const defaultValues: TaskFormValues = { title: "", description: "" };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const taskId = TaskIdSchema.parse(uuidv7());
      const created = await api.call("tasks/create", {
        id: taskId,
        title: parsed.data.title,
        description: parsed.data.description,
        clientId: null,
        dueDate: extras.dueDate,
        dueDateEnd: extras.dueDateEnd,
        assigneeId: extras.assigneeId,
      });
      if (!created.ok) {
        setFailure(apiErrorMessage(t, created.error));
        return;
      }
      await Promise.all(
        extras.attachments.map((attachment) =>
          api.call("tasks/attach", { taskId, uploadId: attachment.id }),
        ),
      );
      await queryClient.invalidateQueries({ queryKey: ["api", branchId, "tasks/list"] });
      await navigate({ to: "/tasks/$taskId", params: { taskId } });
    },
  });

  const upload = async (file: File) => {
    setUploading(true);
    const uploaded = await uploadFile(api, file);
    setUploading(false);
    if (!uploaded.ok) {
      setFailure(apiErrorMessage(t, uploaded.error));
      return;
    }
    setExtras((current) => ({ ...current, attachments: [...current.attachments, uploaded.value] }));
  };

  return (
    <section className="max-w-2xl pb-10">
      <PageHeader title={t("tasks.create")} />
      {employees.isError && <FormAlert message={t("directory.loadError")} />}
      {!employees.isError && employees.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      )}
      {!employees.isError && !employees.isPending && (
        <form
          method="post"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
          className="space-y-6"
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="title">
            {(field) => <TextField field={field} label={t("tasks.field.title")} required />}
          </form.Field>
          <form.Field name="description">
            {(field) => (
              <TextAreaField field={field} label={t("tasks.field.description")} rows={3} />
            )}
          </form.Field>
          <AssigneeSelect
            label={t("tasks.field.assignee")}
            employees={employees.data}
            value={extras.assigneeId}
            onChange={(assigneeId) => {
              setExtras((current) => ({ ...current, assigneeId }));
            }}
          />
          <DateTimeField
            label={t("tasks.field.dueDate")}
            value={extras.dueDate}
            onChange={(dueDate) => {
              setExtras((current) => ({ ...current, dueDate }));
            }}
          />
          <DateTimeField
            label={t("tasks.field.dueDateEnd")}
            value={extras.dueDateEnd}
            onChange={(dueDateEnd) => {
              setExtras((current) => ({ ...current, dueDateEnd }));
            }}
          />

          <div className="space-y-2">
            <span className="text-sm font-medium">{t("tasks.field.attachments")}</span>
            {extras.attachments.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("tasks.noAttachments")}</p>
            ) : (
              <ul className="divide-y">
                {extras.attachments.map((attachment) => (
                  <li key={attachment.id} className="flex items-center gap-2 py-2 text-sm">
                    <span className="flex-1 truncate">{attachment.originalName}</span>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("tasks.removeAttachment", { name: attachment.originalName })}
                      onClick={() => {
                        setExtras((current) => ({
                          ...current,
                          attachments: current.attachments.filter((a) => a.id !== attachment.id),
                        }));
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

          <form.Subscribe selector={(state) => state.isSubmitting}>
            {(submitting) => (
              <Button type="submit" disabled={submitting} aria-busy={submitting}>
                {t("tasks.create")}
              </Button>
            )}
          </form.Subscribe>
        </form>
      )}
    </section>
  );
}
