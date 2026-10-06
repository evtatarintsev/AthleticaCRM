import type { ApiClient } from "@/api/client";
import type { EmployeeId, UploadId } from "@/api/generated/contracts";
import { Avatar } from "@/ui/Avatar";
import type { ChecklistItem } from "@/ui/ChecklistSheet";

/** Тренер [employee] как запись чипов и панели выбора: имя с аватаром. */
export function employeeChecklistItem(
  api: ApiClient,
  employee: {
    readonly id: EmployeeId;
    readonly name: string;
    readonly avatarId: UploadId | null;
  },
): ChecklistItem<EmployeeId> {
  return {
    id: employee.id,
    name: employee.name,
    avatar: (
      <Avatar api={api} uploadId={employee.avatarId} name={employee.name} className="size-5" />
    ),
  };
}
