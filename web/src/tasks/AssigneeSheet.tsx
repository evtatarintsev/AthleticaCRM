import type { ApiClient } from "@/api/client";
import type { EmployeeId } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { Avatar } from "@/ui/Avatar";
import { ChoiceSheet } from "@/ui/ChoiceSheet";
import { useEmployees } from "./tasksQueries";

/**
 * Панель выбора исполнителя задачи: сотрудники филиала с аватарами и первым пунктом
 * «Не назначен». [current] отмечается галочкой; `undefined` — не отмечено ничего.
 * Что значит выбор, решает [onChoose]: `null` в ответе — успех, строка — текст ошибки.
 */
export function AssigneeSheet({
  api,
  open,
  onOpenChange,
  current,
  onChoose,
}: {
  readonly api: ApiClient;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly current?: EmployeeId | null;
  readonly onChoose: (assigneeId: EmployeeId | null) => Promise<string | null>;
}) {
  const { t } = useI18n();
  const employees = useEmployees(api);
  const items = (employees.data ?? []).map((employee) => ({
    id: employee.id,
    name: employee.name,
    avatar: (
      <Avatar api={api} uploadId={employee.avatarId} name={employee.name} className="size-5" />
    ),
  }));
  return (
    <ChoiceSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("tasks.field.assignee")}
      items={items}
      {...(current === undefined ? {} : { current })}
      noneLabel={t("tasks.assigneeUnassigned")}
      emptyText={t("tasks.noEmployees")}
      onChoose={onChoose}
    />
  );
}
