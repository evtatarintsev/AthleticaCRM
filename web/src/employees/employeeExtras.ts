import type { BranchId, UploadId, UserPermission } from "@/api/generated/contracts";

/** Роли, права и доступ к филиалам сотрудника — часть формы, не относящаяся к контактам. */
export interface EmployeeExtras {
  readonly avatarId: UploadId | null;
  readonly roleIds: ReadonlySet<string>;
  readonly grantedPermissions: ReadonlySet<UserPermission>;
  readonly revokedPermissions: ReadonlySet<UserPermission>;
  /** `true` — доступ ко всем филиалам; тогда [branchIds] не учитывается. */
  readonly allBranchesAccess: boolean;
  readonly branchIds: ReadonlySet<BranchId>;
}

/** Значения [EmployeeExtras] по умолчанию для нового сотрудника. */
export const EMPTY_EMPLOYEE_EXTRAS: EmployeeExtras = {
  avatarId: null,
  roleIds: new Set(),
  grantedPermissions: new Set(),
  revokedPermissions: new Set(),
  allBranchesAccess: true,
  branchIds: new Set(),
};
