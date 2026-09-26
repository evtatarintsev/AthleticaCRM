import { useForm } from "@tanstack/react-form";
import { useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import type { DisciplineId, EmployeeId, GroupDetailResponse } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { useSession } from "@/query/session";
import { MultiSelectPicker } from "@/ui/MultiSelectPicker";
import { PageHeader } from "@/ui/PageHeader";
import { useGroupDisciplines, useGroupEmployees } from "./groupsQueries";

/** Данные формы группы, готовые к отправке на сервер: без идентификатора. */
export interface GroupSaveInput {
  readonly name: string;
  readonly disciplineIds: readonly DisciplineId[];
  readonly employeeIds: readonly EmployeeId[];
}

/** Схема названия группы с сообщением на языке [t]; обрезается по краям. */
function nameSchema(t: I18n["t"]) {
  return z.string().trim().min(1, t("error.required"));
}

/**
 * Форма создания или редактирования группы: название, дисциплины и тренеры. Расписание
 * задаётся отдельно из карточки группы. [initial] — редактируемая группа; `null` — новая.
 */
export function GroupFormPage({
  api,
  title,
  submitLabel,
  initial,
  onSave,
}: {
  readonly api: ApiClient;
  readonly title: string;
  readonly submitLabel: string;
  readonly initial: GroupDetailResponse | null;
  readonly onSave: (input: GroupSaveInput) => Promise<string | null>;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const disciplines = useGroupDisciplines(api, branchId);
  const employees = useGroupEmployees(api, branchId);
  const schema = nameSchema(t);
  const [failure, setFailure] = useState<string | null>(null);
  const [disciplineIds, setDisciplineIds] = useState<readonly DisciplineId[]>(
    () => initial?.disciplines.map((d) => d.id) ?? [],
  );
  const [employeeIds, setEmployeeIds] = useState<readonly EmployeeId[]>(
    () => initial?.employees.map((e) => e.id) ?? [],
  );
  const [picker, setPicker] = useState<"disciplines" | "employees" | null>(null);

  const form = useForm({
    defaultValues: { name: initial?.name ?? "" },
    onSubmit: async ({ value }) => {
      const name = schema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const error = await onSave({ name: name.data, disciplineIds, employeeIds });
      if (error !== null) {
        setFailure(error);
      }
    },
  });

  const disciplineNames = (disciplines.data ?? []).filter((d) => disciplineIds.includes(d.id));
  const employeeNames = (employees.data ?? []).filter((e) => employeeIds.includes(e.id));

  return (
    <section className="max-w-2xl">
      <PageHeader title={title} />
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

        <form.Field name="name" validators={{ onSubmit: schema }}>
          {(field) => (
            <TextField field={field} label={t("groups.name")} autoComplete="off" required />
          )}
        </form.Field>

        <ChipsField
          label={t("groups.disciplines")}
          items={disciplineNames}
          addLabel={t("groups.detail.addDiscipline")}
          onAdd={() => {
            setPicker("disciplines");
          }}
          onRemove={(id) => {
            setDisciplineIds(disciplineIds.filter((current) => current !== id));
          }}
        />

        <ChipsField
          label={t("groups.employees")}
          items={employeeNames}
          addLabel={t("groups.detail.addEmployee")}
          onAdd={() => {
            setPicker("employees");
          }}
          onRemove={(id) => {
            setEmployeeIds(employeeIds.filter((current) => current !== id));
          }}
        />

        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
            <Button type="submit" disabled={submitting} aria-busy={submitting}>
              {submitLabel}
            </Button>
          )}
        </form.Subscribe>
      </form>

      <MultiSelectPicker
        open={picker === "disciplines"}
        title={t("groups.picker.disciplinesTitle")}
        items={disciplines.data ?? []}
        selected={disciplineIds}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setPicker(open ? "disciplines" : null);
        }}
        onApply={setDisciplineIds}
      />
      <MultiSelectPicker
        open={picker === "employees"}
        title={t("groups.picker.employeesTitle")}
        items={employees.data ?? []}
        selected={employeeIds}
        emptyText={t("groups.picker.empty")}
        onOpenChange={(open) => {
          setPicker(open ? "employees" : null);
        }}
        onApply={setEmployeeIds}
      />
    </section>
  );
}

/** Поле-список чипов [items] с крестиком удаления и кнопкой добавления. */
function ChipsField<Id extends string>({
  label,
  items,
  addLabel,
  onAdd,
  onRemove,
}: {
  readonly label: string;
  readonly items: readonly { readonly id: Id; readonly name: string }[];
  readonly addLabel: string;
  readonly onAdd: () => void;
  readonly onRemove: (id: Id) => void;
}) {
  const { t } = useI18n();
  return (
    <div className="space-y-1.5">
      <span className="text-sm font-medium">{label}</span>
      <div className="flex flex-wrap gap-2">
        {items.map((item) => (
          <span
            key={item.id}
            className="inline-flex items-center gap-1 rounded-full border bg-accent px-3 py-1 text-xs font-medium"
          >
            {item.name}
            <button
              type="button"
              aria-label={t("groups.removeChip", { name: item.name })}
              onClick={() => {
                onRemove(item.id);
              }}
              className="ml-1"
            >
              ×
            </button>
          </span>
        ))}
        <button
          type="button"
          onClick={onAdd}
          className="rounded-full border border-dashed px-3 py-1 text-xs font-medium text-muted-foreground hover:bg-accent"
        >
          {addLabel}
        </button>
      </div>
    </div>
  );
}
