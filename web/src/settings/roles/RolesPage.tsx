import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { RoleItem } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { RoleDialog, type RoleTerms } from "./RoleDialog";

/** Что редактируется сейчас: ничего, новая роль или существующая. */
type Editing =
  | { readonly kind: "none" }
  | { readonly kind: "create" }
  | { readonly kind: "edit"; readonly role: RoleItem };

/** Роли организации с набором прав; создание и изменение роли в диалоге. */
export function RolesPage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const rolesQuery = apiQuery(api, branchId, "employees/roles");
  const roles = useQuery(rolesQuery);
  const [editing, setEditing] = useState<Editing>({ kind: "none" });

  const save = async (terms: RoleTerms, role: RoleItem | null): Promise<string | null> => {
    const result =
      role === null
        ? await api.call("employees/roles/create", { id: uuidv7(), ...terms })
        : await api.call("employees/roles/update", { id: role.id, ...terms });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await queryClient.invalidateQueries({ queryKey: rolesQuery.queryKey });
    return null;
  };

  return (
    <section className="max-w-3xl">
      <PageHeader
        title={t("roles.title")}
        actions={
          <Button
            onClick={() => {
              setEditing({ kind: "create" });
            }}
          >
            <PlusIcon aria-hidden />
            {t("roles.add")}
          </Button>
        }
      />
      {roles.isPending && <Skeleton className="h-32 w-full" />}
      {roles.isError && <FormAlert message={t("directory.loadError")} />}
      {roles.data?.roles.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">{t("roles.empty")}</p>
      )}
      {roles.data !== undefined && roles.data.roles.length > 0 && (
        <ul className="space-y-2">
          {roles.data.roles.map((role) => (
            <li key={role.id}>
              <button
                type="button"
                aria-label={t("directory.edit", { name: role.name })}
                onClick={() => {
                  setEditing({ kind: "edit", role });
                }}
                className="w-full space-y-2 rounded-lg border bg-card p-4 text-left outline-none hover:bg-accent/40 focus-visible:ring-[3px] focus-visible:ring-ring/50"
              >
                <span className="block font-medium break-words">{role.name}</span>
                <span className="flex flex-wrap gap-1.5">
                  {role.permissions.length === 0 ? (
                    <span className="text-sm text-muted-foreground">
                      {t("roles.noPermissions")}
                    </span>
                  ) : (
                    role.permissions.map((permission) => (
                      <span
                        key={permission}
                        className="rounded-full border px-2 py-0.5 text-xs text-muted-foreground"
                      >
                        {t(`permission.${permission}.label`)}
                      </span>
                    ))
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {editing.kind !== "none" && (
        <RoleDialog
          initial={editing.kind === "edit" ? editing.role : null}
          onSave={(terms) => save(terms, editing.kind === "edit" ? editing.role : null)}
          onClose={() => {
            setEditing({ kind: "none" });
          }}
        />
      )}
    </section>
  );
}
