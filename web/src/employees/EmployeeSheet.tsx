import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useMemo, useState, type ReactNode } from "react";
import { AvatarPicker } from "@/account/AvatarPicker";
import type { ApiClient } from "@/api/client";
import {
  EmployeeIdSchema,
  type BranchDetailResponse,
  type BranchId,
  type EmployeeDetailResponse,
  type RoleItem,
  type UserPermission,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ChecklistSheet } from "@/ui/ChecklistSheet";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { RoleChip } from "./EmployeeBadges";
import {
  EMPTY_EMPLOYEE_FORM,
  employeeContactSchema,
  employeeFormValuesOf,
  permissionLists,
  type EmployeeFormValues,
  type PermissionOverrides,
} from "./employeeForm";
import { PermissionsSheet } from "./PermissionsSheet";

/** Вложенная панель формы сотрудника, открытая сейчас. */
type EmployeePicker = "roles" | "permissions" | "branches" | null;

/** Свойства панели сотрудника. */
interface EmployeeSheetProps {
  /** Клиент API. */
  readonly api: ApiClient;
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Редактируемый сотрудник; `null` — создание нового. */
  readonly employee: EmployeeDetailResponse | null;
}

/**
 * Панель справа для создания или редактирования сотрудника [EmployeeSheetProps.employee]:
 * фото, контакты, роли, права и доступ к филиалам. Роли, права и филиалы выбираются во
 * вложенных панелях и сохраняются вместе с остальными полями одним запросом.
 */
export function EmployeeSheet({ api, open, onOpenChange, employee }: EmployeeSheetProps) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={open}
      onOpenChange={onOpenChange}
      title={employee === null ? t("employees.create") : t("employees.edit")}
    >
      <EmployeeSheetContent api={api} employee={employee} />
    </EditSheet>
  );
}

/** Загружает роли и филиалы организации, затем показывает форму. */
function EmployeeSheetContent({
  api,
  employee,
}: {
  readonly api: ApiClient;
  readonly employee: EmployeeDetailResponse | null;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const roles = useQuery({
    ...apiQuery(api, branchId, "employees/roles"),
    select: (r) => r.roles,
  });
  const branches = useQuery({
    ...apiQuery(api, branchId, "branches/list"),
    select: (r) => r.branches,
  });

  if (roles.isError || branches.isError) {
    return (
      <EditSheetBody>
        <FormAlert message={t("directory.loadError")} />
      </EditSheetBody>
    );
  }
  if (roles.data === undefined || branches.data === undefined) {
    return (
      <EditSheetBody>
        <Skeleton className="mx-auto size-24 rounded-full" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-40 w-full" />
      </EditSheetBody>
    );
  }
  return <EmployeeForm api={api} employee={employee} roles={roles.data} branches={branches.data} />;
}

/**
 * Форма сотрудника. При создании доступ ко всем филиалам отправляется выключенным, при
 * редактировании — текущим значением сотрудника: флаг из формы не меняется.
 */
function EmployeeForm({
  api,
  employee,
  roles,
  branches,
}: {
  readonly api: ApiClient;
  readonly employee: EmployeeDetailResponse | null;
  readonly roles: readonly RoleItem[];
  readonly branches: readonly BranchDetailResponse[];
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close } = useEditSheet();
  const branchId = useSession(api).currentBranch.id;
  const schema = useMemo(() => employeeContactSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const [picker, setPicker] = useState<EmployeePicker>(null);

  const defaultValues: EmployeeFormValues =
    employee === null ? EMPTY_EMPLOYEE_FORM : employeeFormValuesOf(employee);
  const form = useForm({
    defaultValues,
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const phoneNo = parsed.data.phoneNo.trim();
      const input = {
        name: parsed.data.name,
        phoneNo: phoneNo === "" ? null : phoneNo,
        email: parsed.data.email,
        avatarId: value.avatarId,
        roleIds: value.roleIds,
        ...permissionLists(value.permissions),
        branchIds: value.branchIds,
      };
      const result =
        employee === null
          ? await api.call("employees/create", {
              id: EmployeeIdSchema.parse(uuidv7()),
              ...input,
              allBranchesAccess: false,
            })
          : await api.call("employees/update", {
              id: employee.id,
              ...input,
              allBranchesAccess: employee.allBranchesAccess,
            });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: apiQuery(api, branchId, "employees/list").queryKey,
        }),
        employee === null
          ? Promise.resolve()
          : queryClient.invalidateQueries({
              queryKey: apiQuery(api, branchId, "employees/detail", { id: employee.id }).queryKey,
            }),
      ]);
      close();
    },
  });

  const openPicker = (next: EmployeePicker) => () => {
    setPicker(next);
  };
  const pickerOpenChange = (which: EmployeePicker) => (next: boolean) => {
    setPicker(next ? which : null);
  };

  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          submitLabel={employee === null ? t("action.create") : t("action.save")}
          onSubmit={() => {
            void form.handleSubmit();
          }}
          nested={
            <form.Subscribe
              selector={(state) => ({
                roleIds: state.values.roleIds,
                permissions: state.values.permissions,
                branchIds: state.values.branchIds,
              })}
            >
              {(values) => (
                <>
                  <ChecklistSheet
                    open={picker === "roles"}
                    onOpenChange={pickerOpenChange("roles")}
                    title={t("employees.columnRoles")}
                    items={roles}
                    selected={values.roleIds}
                    emptyText={t("employees.rolesEmpty")}
                    searchLabel={t("employees.searchRoles")}
                    submitLabel={t("action.done")}
                    onSubmit={(ids) => {
                      form.setFieldValue("roleIds", ids);
                      return Promise.resolve(null);
                    }}
                  />
                  <PermissionsSheet
                    open={picker === "permissions"}
                    onOpenChange={pickerOpenChange("permissions")}
                    value={values.permissions}
                    rolePermissions={rolePermissionsOf(roles, values.roleIds)}
                    onApply={(permissions) => {
                      form.setFieldValue("permissions", permissions);
                    }}
                  />
                  <ChecklistSheet<BranchId>
                    open={picker === "branches"}
                    onOpenChange={pickerOpenChange("branches")}
                    title={t("employees.sectionBranchAccess")}
                    items={branches}
                    selected={values.branchIds}
                    emptyText={t("employees.branchesEmpty")}
                    searchLabel={t("employees.searchBranches")}
                    submitLabel={t("action.done")}
                    onSubmit={(ids) => {
                      form.setFieldValue("branchIds", ids);
                      return Promise.resolve(null);
                    }}
                  />
                </>
              )}
            </form.Subscribe>
          }
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Subscribe selector={(state) => state.values.name}>
            {(name) => (
              <form.Field name="avatarId">
                {(field) => (
                  <AvatarPicker
                    api={api}
                    value={field.state.value}
                    name={name}
                    disabled={submitting}
                    onChange={(avatarId) => {
                      field.handleChange(avatarId);
                    }}
                  />
                )}
              </form.Field>
            )}
          </form.Subscribe>
          <form.Field name="name" validators={{ onSubmit: schema.shape.name }}>
            {(field) => (
              <TextField field={field} label={t("employees.name")} autoComplete="name" required />
            )}
          </form.Field>
          <form.Field name="phoneNo">
            {(field) => (
              <TextField
                field={field}
                label={t("employees.phone")}
                type="tel"
                hint={t("employees.phoneHint")}
                autoComplete="tel"
              />
            )}
          </form.Field>
          <form.Field name="email" validators={{ onSubmit: schema.shape.email }}>
            {(field) => (
              <TextField
                field={field}
                label={t("employees.email")}
                type="email"
                hint={t("employees.emailHint")}
                autoComplete="email"
                required
              />
            )}
          </form.Field>
          <form.Subscribe selector={(state) => state.values.roleIds}>
            {(roleIds) => (
              <FormSection
                title={t("employees.columnRoles")}
                onEdit={roles.length === 0 ? null : openPicker("roles")}
              >
                {roles.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    {t("employees.rolesEmpty")}{" "}
                    <Link
                      to="/settings/roles"
                      className="text-primary underline-offset-4 hover:underline"
                    >
                      {t("roles.add")}
                    </Link>
                  </p>
                ) : (
                  <NameChips
                    items={roles.filter((role) => roleIds.includes(role.id))}
                    emptyText={t("employees.rolesNone")}
                  />
                )}
              </FormSection>
            )}
          </form.Subscribe>
          <form.Subscribe selector={(state) => state.values.permissions}>
            {(permissions) => (
              <FormSection
                title={t("employees.sectionPermissions")}
                onEdit={openPicker("permissions")}
              >
                <PermissionsSummary permissions={permissions} />
              </FormSection>
            )}
          </form.Subscribe>
          <form.Subscribe selector={(state) => state.values.branchIds}>
            {(branchIds) => (
              <FormSection
                title={t("employees.sectionBranchAccess")}
                onEdit={openPicker("branches")}
              >
                {employee?.allBranchesAccess === true && (
                  <p className="text-sm">{t("employees.allBranchesAccess")}</p>
                )}
                <NameChips
                  items={branches.filter((branch) => branchIds.includes(branch.id))}
                  emptyText={t("employees.branchesNone")}
                />
              </FormSection>
            )}
          </form.Subscribe>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}

/** Права, которые дают роли [roleIds] из списка ролей организации [roles]. */
function rolePermissionsOf(
  roles: readonly RoleItem[],
  roleIds: readonly string[],
): ReadonlySet<UserPermission> {
  return new Set(roles.filter((role) => roleIds.includes(role.id)).flatMap((r) => r.permissions));
}

/**
 * Секция формы со сводкой выбора [children] и кнопкой «Изменить», открывающей вложенную
 * панель [onEdit]; без [onEdit] кнопки нет.
 */
function FormSection({
  title,
  onEdit,
  children,
}: {
  readonly title: string;
  readonly onEdit: (() => void) | null;
  readonly children: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <section className="space-y-2 border-t pt-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-medium">{title}</h3>
        {onEdit !== null && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            aria-label={`${t("employees.editSection")}: ${title}`}
            onClick={onEdit}
          >
            {t("employees.editSection")}
          </Button>
        )}
      </div>
      {children}
    </section>
  );
}

/** Выбранные записи [items] ярлыками с названиями; без записей — [emptyText]. */
function NameChips({
  items,
  emptyText,
}: {
  readonly items: readonly { readonly id: string; readonly name: string }[];
  readonly emptyText: string;
}) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">{emptyText}</p>;
  }
  return (
    <div className="flex flex-wrap gap-1.5">
      {items.map((item) => (
        <RoleChip key={item.id} name={item.name} />
      ))}
    </div>
  );
}

/** Сводка явных решений по правам [permissions]: сколько выдано и отозвано. */
function PermissionsSummary({ permissions }: { readonly permissions: PermissionOverrides }) {
  const { t } = useI18n();
  const { grantedPermissions, revokedPermissions } = permissionLists(permissions);
  if (grantedPermissions.length === 0 && revokedPermissions.length === 0) {
    return <p className="text-sm text-muted-foreground">{t("employees.permissionsByRoles")}</p>;
  }
  return (
    <p className="text-sm">
      {t("employees.permissionsSummary", {
        granted: grantedPermissions.length,
        revoked: revokedPermissions.length,
      })}
    </p>
  );
}
