import { useForm } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import type { ApiClient } from "@/api/client";
import { GroupIdSchema, type DisciplineId, type EmployeeId } from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { ChecklistSheet } from "@/ui/ChecklistSheet";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { employeeChecklistItem } from "./employeeChecklistItem";
import { GroupChips, GroupChipsSection } from "./GroupChips";
import { groupNameSchema } from "./groupName";
import { useGroupDisciplines, useGroupEmployees } from "./groupsQueries";

/** Значения формы новой группы. */
interface GroupCreateValues {
  /** Название группы, как введено. */
  readonly name: string;
  /** Выбранные дисциплины. */
  readonly disciplineIds: readonly DisciplineId[];
  /** Выбранные тренеры. */
  readonly employeeIds: readonly EmployeeId[];
}

/** Пустая форма новой группы. */
const EMPTY_GROUP: GroupCreateValues = { name: "", disciplineIds: [], employeeIds: [] };

/**
 * Панель справа для создания группы: название, дисциплины и тренеры. Дисциплины и тренеры
 * выбираются во вложенных панелях и сохраняются вместе с названием одним запросом; после
 * создания открывается карточка новой группы, где задаётся расписание.
 */
export function GroupCreateSheet({
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
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("groups.create")}>
      <GroupCreateForm api={api} />
    </EditSheet>
  );
}

/** Форма новой группы; монтируется при каждом открытии панели. */
function GroupCreateForm({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { close } = useEditSheet();
  const branchId = useSession(api).currentBranch.id;
  const disciplines = useGroupDisciplines(api, branchId);
  const employees = useGroupEmployees(api, branchId);
  const schema = useMemo(() => groupNameSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const [picker, setPicker] = useState<"disciplines" | "employees" | null>(null);

  const form = useForm({
    defaultValues: EMPTY_GROUP,
    onSubmit: async ({ value }) => {
      const name = schema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const id = GroupIdSchema.parse(uuidv7());
      const result = await api.call("groups/create", {
        id,
        name: name.data,
        disciplineIds: value.disciplineIds,
        employeeIds: value.employeeIds,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await queryClient.invalidateQueries({ queryKey: ["api", branchId, "groups/list"] });
      close();
      await navigate({ to: "/groups/$groupId", params: { groupId: id } });
    },
  });

  const pickerOpenChange = (kind: "disciplines" | "employees") => (next: boolean) => {
    setPicker(next ? kind : null);
  };
  const employeeItems = (employees.data ?? []).map((e) => employeeChecklistItem(api, e));

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
            <form.Subscribe
              selector={(state) => ({
                disciplineIds: state.values.disciplineIds,
                employeeIds: state.values.employeeIds,
              })}
            >
              {(values) => (
                <>
                  <ChecklistSheet
                    open={picker === "disciplines"}
                    onOpenChange={pickerOpenChange("disciplines")}
                    title={t("groups.picker.disciplinesTitle")}
                    items={disciplines.data ?? []}
                    selected={values.disciplineIds}
                    emptyText={t("groups.picker.empty")}
                    submitLabel={t("action.done")}
                    onSubmit={(ids) => {
                      form.setFieldValue("disciplineIds", ids);
                      return Promise.resolve(null);
                    }}
                  />
                  <ChecklistSheet
                    open={picker === "employees"}
                    onOpenChange={pickerOpenChange("employees")}
                    title={t("groups.picker.employeesTitle")}
                    items={employeeItems}
                    selected={values.employeeIds}
                    emptyText={t("groups.picker.empty")}
                    submitLabel={t("action.done")}
                    onSubmit={(ids) => {
                      form.setFieldValue("employeeIds", ids);
                      return Promise.resolve(null);
                    }}
                  />
                </>
              )}
            </form.Subscribe>
          }
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="name" validators={{ onSubmit: schema }}>
            {(field) => (
              <TextField field={field} label={t("groups.name")} autoComplete="off" required />
            )}
          </form.Field>
          <form.Subscribe selector={(state) => state.values.disciplineIds}>
            {(disciplineIds) => (
              <GroupChipsSection
                title={t("groups.detail.disciplinesTitle")}
                onEdit={() => {
                  setPicker("disciplines");
                }}
              >
                <GroupChips
                  items={(disciplines.data ?? []).filter((d) => disciplineIds.includes(d.id))}
                  emptyText={t("groups.detail.disciplinesEmpty")}
                />
              </GroupChipsSection>
            )}
          </form.Subscribe>
          <form.Subscribe selector={(state) => state.values.employeeIds}>
            {(employeeIds) => (
              <GroupChipsSection
                title={t("groups.detail.employeesTitle")}
                onEdit={() => {
                  setPicker("employees");
                }}
              >
                <GroupChips
                  items={employeeItems.filter((e) => employeeIds.includes(e.id))}
                  emptyText={t("groups.detail.employeesEmpty")}
                />
              </GroupChipsSection>
            )}
          </form.Subscribe>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
