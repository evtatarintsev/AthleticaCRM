import { Link } from "@tanstack/react-router";
import { AvatarPicker } from "@/account/AvatarPicker";
import type { ApiClient } from "@/api/client";
import {
  UserPermissionSchema,
  type BranchDetailResponse,
  type BranchId,
  type RoleItem,
  type UserPermission,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import type { EmployeeExtras } from "./employeeExtras";

/** Свойства [EmployeeFormFields]. */
interface EmployeeFormFieldsProps {
  readonly api: ApiClient;
  /** Текущее имя из формы — источник инициалов, пока аватар не загружен. */
  readonly name: string;
  readonly extras: EmployeeExtras;
  readonly onExtrasChange: (extras: EmployeeExtras) => void;
  readonly roles: readonly RoleItem[];
  readonly branches: readonly BranchDetailResponse[];
  readonly disabled: boolean;
}

/**
 * Аватар, роли, явно выданные и отозванные права и доступ к филиалам — общая часть формы
 * сотрудника для создания и редактирования. Право не может быть одновременно выдано и отозвано:
 * включение одного списка снимает то же право с другого.
 */
export function EmployeeFormFields({
  api,
  name,
  extras,
  onExtrasChange,
  roles,
  branches,
  disabled,
}: EmployeeFormFieldsProps) {
  const { t } = useI18n();

  const toggleRole = (id: string, checked: boolean) => {
    const roleIds = new Set(extras.roleIds);
    if (checked) {
      roleIds.add(id);
    } else {
      roleIds.delete(id);
    }
    onExtrasChange({ ...extras, roleIds });
  };

  const setGranted = (permission: UserPermission, checked: boolean) => {
    const grantedPermissions = new Set(extras.grantedPermissions);
    const revokedPermissions = new Set(extras.revokedPermissions);
    if (checked) {
      grantedPermissions.add(permission);
      revokedPermissions.delete(permission);
    } else {
      grantedPermissions.delete(permission);
    }
    onExtrasChange({ ...extras, grantedPermissions, revokedPermissions });
  };

  const setRevoked = (permission: UserPermission, checked: boolean) => {
    const grantedPermissions = new Set(extras.grantedPermissions);
    const revokedPermissions = new Set(extras.revokedPermissions);
    if (checked) {
      revokedPermissions.add(permission);
      grantedPermissions.delete(permission);
    } else {
      revokedPermissions.delete(permission);
    }
    onExtrasChange({ ...extras, grantedPermissions, revokedPermissions });
  };

  const toggleBranch = (id: BranchId, checked: boolean) => {
    const branchIds = new Set(extras.branchIds);
    if (checked) {
      branchIds.add(id);
    } else {
      branchIds.delete(id);
    }
    onExtrasChange({ ...extras, branchIds });
  };

  return (
    <div className="space-y-6">
      <AvatarPicker
        api={api}
        value={extras.avatarId}
        name={name}
        disabled={disabled}
        onChange={(avatarId) => {
          onExtrasChange({ ...extras, avatarId });
        }}
      />
      {roles.length > 0 ? (
        <fieldset className="space-y-1.5">
          <legend className="mb-1 text-sm font-medium">{t("employees.columnRoles")}</legend>
          <div className="space-y-1">
            {roles.map((role) => (
              <label
                key={role.id}
                className="flex cursor-pointer items-center justify-between gap-3 rounded-md border p-2.5 hover:bg-accent/50"
              >
                <span className="text-sm font-medium">{role.name}</span>
                <input
                  type="checkbox"
                  checked={extras.roleIds.has(role.id)}
                  disabled={disabled}
                  onChange={(event) => {
                    toggleRole(role.id, event.target.checked);
                  }}
                  className="size-4 shrink-0 accent-primary"
                />
              </label>
            ))}
          </div>
        </fieldset>
      ) : (
        <p className="text-sm text-muted-foreground">
          {t("employees.rolesEmpty")}{" "}
          <Link to="/settings/roles" className="text-primary underline-offset-4 hover:underline">
            {t("roles.add")}
          </Link>
        </p>
      )}
      <fieldset className="space-y-1.5">
        <legend className="mb-1 text-sm font-medium">
          {t("employees.sectionGrantedPermissions")}
        </legend>
        <PermissionList
          permissions={extras.grantedPermissions}
          onToggle={setGranted}
          disabled={disabled}
        />
      </fieldset>
      <fieldset className="space-y-1.5">
        <legend className="mb-1 text-sm font-medium">
          {t("employees.sectionRevokedPermissions")}
        </legend>
        <PermissionList
          permissions={extras.revokedPermissions}
          onToggle={setRevoked}
          disabled={disabled}
        />
      </fieldset>
      {branches.length > 0 && (
        <fieldset className="space-y-1.5">
          <legend className="mb-1 text-sm font-medium">{t("employees.sectionBranchAccess")}</legend>
          <label className="flex cursor-pointer items-center justify-between gap-3 rounded-md border p-2.5 hover:bg-accent/50">
            <span className="text-sm font-medium">{t("employees.allBranches")}</span>
            <input
              type="checkbox"
              checked={extras.allBranchesAccess}
              disabled={disabled}
              onChange={(event) => {
                onExtrasChange({ ...extras, allBranchesAccess: event.target.checked });
              }}
              className="size-4 shrink-0 accent-primary"
            />
          </label>
          {!extras.allBranchesAccess && (
            <div className="space-y-1">
              {branches.map((branch) => (
                <label
                  key={branch.id}
                  className="flex cursor-pointer items-center justify-between gap-3 rounded-md border p-2.5 hover:bg-accent/50"
                >
                  <span className="text-sm font-medium">{branch.name}</span>
                  <input
                    type="checkbox"
                    checked={extras.branchIds.has(branch.id)}
                    disabled={disabled}
                    onChange={(event) => {
                      toggleBranch(branch.id, event.target.checked);
                    }}
                    className="size-4 shrink-0 accent-primary"
                  />
                </label>
              ))}
            </div>
          )}
        </fieldset>
      )}
    </div>
  );
}

/** Список переключателей прав [UserPermissionSchema.options] с названием и описанием каждого. */
function PermissionList({
  permissions,
  onToggle,
  disabled,
}: {
  readonly permissions: ReadonlySet<UserPermission>;
  readonly onToggle: (permission: UserPermission, checked: boolean) => void;
  readonly disabled: boolean;
}) {
  const { t } = useI18n();
  return (
    <div className="space-y-1">
      {UserPermissionSchema.options.map((permission) => (
        <label
          key={permission}
          className="flex cursor-pointer items-start gap-3 rounded-md p-2 hover:bg-accent/50"
        >
          <input
            type="checkbox"
            checked={permissions.has(permission)}
            disabled={disabled}
            onChange={(event) => {
              onToggle(permission, event.target.checked);
            }}
            className="mt-0.5 size-4 shrink-0 accent-primary"
          />
          <span className="space-y-0.5">
            <span className="block text-sm font-medium">{t(`permission.${permission}.name`)}</span>
            <span className="block text-xs text-muted-foreground">
              {t(`permission.${permission}.description`)}
            </span>
          </span>
        </label>
      ))}
    </div>
  );
}
