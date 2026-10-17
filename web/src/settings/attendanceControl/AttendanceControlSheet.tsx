import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { UpdateAttendanceControlSettingsRequest } from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, SelectField, type SelectOption } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Варианты времени до создания задачи, в минутах. */
const DELAY_PRESETS: readonly number[] = [30, 60, 120, 180, 240, 360, 720, 1440];

/** Панель настроек «Контроль посещаемости», открытая при [open]. */
export function AttendanceControlSheet({
  api,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("attendanceControl.title")}>
      <AttendanceControlPanel api={api} />
    </EditSheet>
  );
}

/**
 * Загружает настройки контроля посещаемости организации и показывает форму; после
 * сохранения обновляет кэш запроса и закрывает панель.
 */
function AttendanceControlPanel({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const query = apiQuery(api, branchId, "org/attendance-control");
  const settings = useQuery(query);

  if (settings.data === undefined) {
    return (
      <EditSheetBody>
        {settings.isError && <FormAlert message={t("attendanceControl.loadError")} />}
        {settings.isPending && (
          <div className="space-y-4">
            <Skeleton className="h-6 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        )}
      </EditSheetBody>
    );
  }
  return (
    <AttendanceControlForm
      enabled={settings.data.overdueTaskEnabled}
      delayMinutes={settings.data.overdueTaskDelayMinutes}
      onSave={async (values) => {
        const result = await api.call("org/attendance-control/update", values);
        if (!result.ok) {
          return apiErrorMessage(t, result.error);
        }
        queryClient.setQueryData(query.queryKey, result.value);
        toast.success(t("attendanceControl.saved"));
        return null;
      }}
    />
  );
}

/**
 * Форма: переключатель задачи по незакрытому журналу и время до её создания.
 * Выбор времени недоступен, пока задача выключена; сохранённое значение вне
 * [DELAY_PRESETS] показывается отдельным вариантом.
 */
function AttendanceControlForm({
  enabled,
  delayMinutes,
  onSave,
}: {
  readonly enabled: boolean;
  readonly delayMinutes: number;
  readonly onSave: (values: UpdateAttendanceControlSettingsRequest) => Promise<string | null>;
}) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [failure, setFailure] = useState<string | null>(null);
  const presets = DELAY_PRESETS.includes(delayMinutes)
    ? DELAY_PRESETS
    : [...DELAY_PRESETS, delayMinutes].sort((a, b) => a - b);
  const options: readonly SelectOption<string>[] = presets.map((minutes) => ({
    value: String(minutes),
    label:
      minutes % 60 === 0
        ? t("attendanceControl.hours", { count: minutes / 60 })
        : t("attendanceControl.minutes", { count: minutes }),
  }));
  const form = useForm({
    defaultValues: { enabled, delay: String(delayMinutes) },
    onSubmit: async ({ value }) => {
      setFailure(null);
      const error = await onSave({
        overdueTaskEnabled: value.enabled,
        overdueTaskDelayMinutes: Number(value.delay),
      });
      if (error === null) {
        close();
      } else {
        setFailure(error);
      }
    },
  });

  return (
    <form.Subscribe
      selector={(state) => ({
        dirty: state.isDirty,
        submitting: state.isSubmitting,
        enabled: state.values.enabled,
      })}
    >
      {({ dirty, submitting, enabled: taskEnabled }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="enabled">
            {(field) => (
              <CheckboxField
                field={field}
                label={t("attendanceControl.overdueTaskEnabled")}
                hint={t("attendanceControl.overdueTaskEnabledHint")}
              />
            )}
          </form.Field>
          <form.Field name="delay">
            {(field) => (
              <SelectField
                field={field}
                label={t("attendanceControl.overdueTaskDelay")}
                options={options}
                disabled={!taskEnabled}
              />
            )}
          </form.Field>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
