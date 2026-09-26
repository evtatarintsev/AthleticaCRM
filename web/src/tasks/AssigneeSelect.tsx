import { useId } from "react";
import type { EmployeeId, EmployeeListItem } from "@/api/generated/contracts";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { useI18n } from "@/i18n/context";

/** Значение «не назначен» в нативном `<select>`: идентификатор сотрудника — непустая строка. */
const UNASSIGNED = "";

/** Выбор исполнителя задачи из сотрудников филиала; пустой вариант — «не назначен». */
export function AssigneeSelect({
  label,
  employees,
  value,
  onChange,
  disabled = false,
}: {
  readonly label: string;
  readonly employees: readonly EmployeeListItem[];
  readonly value: EmployeeId | null;
  readonly onChange: (value: EmployeeId | null) => void;
  readonly disabled?: boolean;
}) {
  const { t } = useI18n();
  const id = useId();
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <NativeSelect
        id={id}
        disabled={disabled}
        value={value ?? UNASSIGNED}
        onChange={(event) => {
          const employee = employees.find((e) => e.id === event.target.value);
          onChange(employee?.id ?? null);
        }}
      >
        <option value={UNASSIGNED}>{t("tasks.assigneeUnassigned")}</option>
        {employees.map((employee) => (
          <option key={employee.id} value={employee.id}>
            {employee.name}
          </option>
        ))}
      </NativeSelect>
    </div>
  );
}
