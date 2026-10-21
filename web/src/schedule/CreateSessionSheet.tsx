import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  GroupIdSchema,
  HallIdSchema,
  LocalDateSchema,
  LocalTimeSchema,
  SessionIdSchema,
  type EmployeeId,
  type LocalDate,
} from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { SelectField, TextAreaField, TextField, type SelectOption } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/**
 * Панель создания занятия вне расписания; открыта, пока задана дата [date],
 * которой предзаполняется форма. [onClose] закрывает панель.
 */
export function CreateSessionSheet({
  api,
  date,
  onClose,
}: {
  readonly api: ApiClient;
  readonly date: LocalDate | null;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={date !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={t("schedule.create.title")}
    >
      {date !== null && <CreateSessionForm api={api} date={date} />}
    </EditSheet>
  );
}

/**
 * Форма занятия: дата, группа, тренеры (при выборе группы отмечаются её тренеры), зал,
 * время и заметка. Идентификатор занятия создаётся один раз на открытие панели, поэтому
 * повторная отправка не создаёт второе занятие. После создания перезагружает расписание.
 */
function CreateSessionForm({ api, date }: { readonly api: ApiClient; readonly date: LocalDate }) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const [id] = useState(() => SessionIdSchema.parse(uuidv7()));
  const [failure, setFailure] = useState<string | null>(null);
  const groups = useQuery(apiQuery(api, branchId, "groups/list", {}));
  const halls = useQuery({ ...apiQuery(api, branchId, "halls/list"), select: (r) => r.halls });
  const employees = useQuery({
    ...apiQuery(api, branchId, "employees/list"),
    select: (r) => r.employees.filter((employee) => employee.isActive),
  });

  const groupOptions: readonly SelectOption<string>[] = [
    { value: "", label: t("schedule.create.choose") },
    ...(groups.data?.groups ?? []).map((group) => ({ value: group.id, label: group.name })),
  ];
  const hallOptions: readonly SelectOption<string>[] = [
    { value: "", label: t("schedule.create.choose") },
    ...(halls.data ?? []).map((hall) => ({ value: hall.id, label: hall.name })),
  ];

  const initialDate: string = date;
  const noEmployees: readonly EmployeeId[] = [];
  const form = useForm({
    defaultValues: {
      date: initialDate,
      groupId: "",
      employeeIds: noEmployees,
      hallId: "",
      startTime: "",
      endTime: "",
      notes: "",
    },
    onSubmit: async ({ value }) => {
      setFailure(null);
      const result = await api.call("sessions/create", {
        id,
        groupId: GroupIdSchema.parse(value.groupId),
        date: LocalDateSchema.parse(value.date),
        startTime: LocalTimeSchema.parse(value.startTime),
        endTime: LocalTimeSchema.parse(value.endTime),
        hallId: HallIdSchema.parse(value.hallId),
        employeeIds: value.employeeIds,
        notes: value.notes.trim() === "" ? null : value.notes.trim(),
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await queryClient.invalidateQueries({ queryKey: ["api", branchId, "schedule/list"] });
      close();
    },
  });

  return (
    <form.Subscribe
      selector={(state) => ({
        dirty: state.isDirty,
        submitting: state.isSubmitting,
        values: state.values,
      })}
    >
      {({ dirty, submitting, values }) => {
        const timeOrderInvalid =
          values.startTime !== "" && values.endTime !== "" && values.endTime <= values.startTime;
        const incomplete =
          values.date === "" ||
          values.groupId === "" ||
          values.hallId === "" ||
          values.startTime === "" ||
          values.endTime === "";
        return (
          <EditSheetForm
            dirty={dirty}
            submitting={submitting}
            submitDisabled={incomplete || timeOrderInvalid}
            submitLabel={t("schedule.create.submit")}
            onSubmit={() => {
              void form.handleSubmit();
            }}
          >
            {failure !== null && <FormAlert message={failure} />}
            <form.Field name="date">
              {(field) => <TextField field={field} label={t("schedule.create.date")} type="date" />}
            </form.Field>
            <form.Field
              name="groupId"
              listeners={{
                onChange: ({ value }) => {
                  const group = groups.data?.groups.find((g) => g.id === value);
                  form.setFieldValue(
                    "employeeIds",
                    (group?.employees ?? []).map((employee) => employee.id),
                  );
                },
              }}
            >
              {(field) => (
                <SelectField
                  field={field}
                  label={t("schedule.create.group")}
                  options={groupOptions}
                />
              )}
            </form.Field>
            <form.Field name="employeeIds">
              {(field) => (
                <fieldset className="space-y-1.5">
                  <legend className="text-sm font-medium">{t("schedule.create.employees")}</legend>
                  <div className="max-h-48 space-y-1 overflow-y-auto rounded-md border p-2">
                    {(employees.data ?? []).map((employee) => (
                      <label key={employee.id} className="flex items-center gap-2 text-sm">
                        <input
                          type="checkbox"
                          className="size-4 accent-primary"
                          checked={field.state.value.includes(employee.id)}
                          onChange={(event) => {
                            field.handleChange(
                              event.target.checked
                                ? [...field.state.value, employee.id]
                                : field.state.value.filter((id) => id !== employee.id),
                            );
                          }}
                        />
                        {employee.name}
                      </label>
                    ))}
                  </div>
                </fieldset>
              )}
            </form.Field>
            <form.Field name="hallId">
              {(field) => (
                <SelectField
                  field={field}
                  label={t("schedule.create.hall")}
                  options={hallOptions}
                />
              )}
            </form.Field>
            <div className="grid grid-cols-2 gap-4">
              <form.Field name="startTime">
                {(field) => (
                  <TextField field={field} label={t("schedule.create.startTime")} type="time" />
                )}
              </form.Field>
              <form.Field name="endTime">
                {(field) => (
                  <TextField field={field} label={t("schedule.create.endTime")} type="time" />
                )}
              </form.Field>
            </div>
            {timeOrderInvalid && (
              <p className="text-sm text-destructive">{t("schedule.create.timeOrder")}</p>
            )}
            <form.Field name="notes">
              {(field) => (
                <TextAreaField field={field} label={t("schedule.create.notes")} rows={3} />
              )}
            </form.Field>
          </EditSheetForm>
        );
      }}
    </form.Subscribe>
  );
}
