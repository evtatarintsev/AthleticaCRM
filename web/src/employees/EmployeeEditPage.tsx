import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useMemo, useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchDetailResponse,
  EmployeeDetailResponse,
  EmployeeId,
  RoleItem,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { employeeContactSchema, type EmployeeContactValues } from "./employeeContactForm";
import type { EmployeeExtras } from "./employeeExtras";
import { EmployeeFormFields } from "./EmployeeFormFields";

/** Загружает сотрудника, роли и филиалы, затем показывает форму редактирования. */
export function EmployeeEditPage({ api, employeeId }: { api: ApiClient; employeeId: EmployeeId }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const branchId = useSession(api).currentBranch.id;
  const employeesListQuery = apiQuery(api, branchId, "employees/list");
  const detailQuery = apiQuery(api, branchId, "employees/detail", { id: employeeId });
  const detail = useQuery(detailQuery);
  const rolesQuery = useQuery({
    ...apiQuery(api, branchId, "employees/roles"),
    select: (r) => r.roles,
  });
  const branchesQuery = useQuery({
    ...apiQuery(api, branchId, "branches/list"),
    select: (r) => r.branches,
  });

  const loading = detail.isPending || rolesQuery.isPending || branchesQuery.isPending;
  const loadFailed = detail.isError || rolesQuery.isError || branchesQuery.isError;

  return (
    <section className="max-w-2xl pb-10">
      <PageHeader title={t("employees.edit")} />
      {loadFailed && <FormAlert message={t("directory.loadError")} />}
      {!loadFailed && loading && (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      )}
      {!loadFailed && !loading && (
        <EmployeeEditForm
          api={api}
          employee={detail.data}
          roles={rolesQuery.data}
          branches={branchesQuery.data}
          onSaved={async () => {
            await queryClient.invalidateQueries({ queryKey: detailQuery.queryKey });
            await queryClient.invalidateQueries({ queryKey: employeesListQuery.queryKey });
            await navigate({ to: "/employees/$employeeId", params: { employeeId } });
          }}
        />
      )}
    </section>
  );
}

/** Форма редактирования сотрудника [employee]; вызывает [onSaved] после успешного сохранения. */
function EmployeeEditForm({
  api,
  employee,
  roles,
  branches,
  onSaved,
}: {
  readonly api: ApiClient;
  readonly employee: EmployeeDetailResponse;
  readonly roles: readonly RoleItem[];
  readonly branches: readonly BranchDetailResponse[];
  readonly onSaved: () => Promise<void>;
}) {
  const { t } = useI18n();
  const schema = useMemo(() => employeeContactSchema(t), [t]);
  const [extras, setExtras] = useState<EmployeeExtras>({
    avatarId: employee.avatarId,
    roleIds: new Set(employee.roles.map((role) => role.id)),
    grantedPermissions: new Set(employee.grantedPermissions),
    revokedPermissions: new Set(employee.revokedPermissions),
    allBranchesAccess: employee.allBranchesAccess,
    branchIds: new Set(employee.branchIds),
  });
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: EmployeeContactValues = {
    name: employee.name,
    phoneNo: employee.phoneNo ?? "",
    email: employee.email ?? "",
  };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("employees/update", {
        id: employee.id,
        name: parsed.data.name,
        phoneNo: parsed.data.phoneNo.trim() === "" ? null : parsed.data.phoneNo.trim(),
        email: parsed.data.email,
        avatarId: extras.avatarId,
        roleIds: [...extras.roleIds],
        grantedPermissions: [...extras.grantedPermissions],
        revokedPermissions: [...extras.revokedPermissions],
        allBranchesAccess: extras.allBranchesAccess,
        branchIds: [...extras.branchIds],
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await onSaved();
    },
  });

  return (
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
      <form.Field name="name">
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
      <form.Field name="email">
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
      <form.Subscribe selector={(state) => [state.values.name, state.isSubmitting] as const}>
        {([name, submitting]) => (
          <EmployeeFormFields
            api={api}
            name={name}
            extras={extras}
            onExtrasChange={setExtras}
            roles={roles}
            branches={branches}
            disabled={submitting}
          />
        )}
      </form.Subscribe>
      <form.Subscribe selector={(state) => state.isSubmitting}>
        {(submitting) => (
          <Button type="submit" disabled={submitting} aria-busy={submitting}>
            {t("action.save")}
          </Button>
        )}
      </form.Subscribe>
    </form>
  );
}
