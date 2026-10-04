import { z } from "zod";
import {
  UserPermissionSchema,
  type BranchId,
  type EmployeeDetailResponse,
  type UploadId,
  type UserPermission,
} from "@/api/generated/contracts";
import type { I18n } from "@/i18n/context";

/** Явное решение по праву сотрудника поверх ролей: выдать или отозвать. */
export type PermissionOverride = "grant" | "revoke";

/**
 * Явные решения по правам: отсутствие права — «по роли». Одна запись на право, поэтому
 * право не может быть одновременно выдано и отозвано.
 */
export type PermissionOverrides = Readonly<Partial<Record<UserPermission, PermissionOverride>>>;

/** Значения формы сотрудника: общие для создания и редактирования. */
export interface EmployeeFormValues {
  /** Имя; обязательно. */
  readonly name: string;
  /** Телефон; пустая строка — не указан. */
  readonly phoneNo: string;
  /** Email; обязателен. */
  readonly email: string;
  /** Загруженное фото. */
  readonly avatarId: UploadId | null;
  /** Роли сотрудника. */
  readonly roleIds: readonly string[];
  /** Явно выданные и отозванные права. */
  readonly permissions: PermissionOverrides;
  /** Филиалы, к которым у сотрудника есть доступ. */
  readonly branchIds: readonly BranchId[];
}

/** Пустая форма нового сотрудника. */
export const EMPTY_EMPLOYEE_FORM: EmployeeFormValues = {
  name: "",
  phoneNo: "",
  email: "",
  avatarId: null,
  roleIds: [],
  permissions: {},
  branchIds: [],
};

/**
 * Значения формы редактирования из карточки сотрудника [employee]. Право, которое сервер
 * вернул и выданным, и отозванным, считается отозванным.
 */
export function employeeFormValuesOf(employee: EmployeeDetailResponse): EmployeeFormValues {
  const permissions: Partial<Record<UserPermission, PermissionOverride>> = {};
  employee.grantedPermissions.forEach((permission) => {
    permissions[permission] = "grant";
  });
  employee.revokedPermissions.forEach((permission) => {
    permissions[permission] = "revoke";
  });
  return {
    name: employee.name,
    phoneNo: employee.phoneNo ?? "",
    email: employee.email ?? "",
    avatarId: employee.avatarId,
    roleIds: employee.roles.map((role) => role.id),
    permissions,
    branchIds: employee.branchIds,
  };
}

/** Выданные и отозванные права из [overrides] — в том виде, в каком их принимает сервер. */
export function permissionLists(overrides: PermissionOverrides): {
  readonly grantedPermissions: readonly UserPermission[];
  readonly revokedPermissions: readonly UserPermission[];
} {
  return {
    grantedPermissions: UserPermissionSchema.options.filter((p) => overrides[p] === "grant"),
    revokedPermissions: UserPermissionSchema.options.filter((p) => overrides[p] === "revoke"),
  };
}

/** Схема контактных полей с сообщениями на языке [t]: имя и email обязательны. */
export function employeeContactSchema(t: I18n["t"]) {
  return z.object({
    name: z.string().trim().min(1, t("error.required")),
    phoneNo: z.string(),
    email: z
      .string()
      .trim()
      .pipe(z.email(t("error.invalidEmail"))),
  });
}
