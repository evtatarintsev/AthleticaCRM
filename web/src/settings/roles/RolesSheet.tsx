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
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { RoleSheet, type RoleTerms } from "./RoleSheet";

/**
 * Что редактируется во вложенной панели: новая роль или существующая. Значение остаётся
 * после закрытия панели, чтобы её содержимое не пропадало во время анимации закрытия.
 */
type Editing = { readonly kind: "create" } | { readonly kind: "edit"; readonly role: RoleItem };

/** Панель ролей организации, открытая при [open]. */
export function RolesSheet({
  api,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("roles.title")} size="lg">
      <RolesPanel api={api} />
    </EditSheet>
  );
}

/**
 * Роли организации карточками с правами-ярлыками; создание и изменение роли открываются
 * панелью поверх.
 */
function RolesPanel({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const rolesQuery = apiQuery(api, branchId, "employees/roles");
  const roles = useQuery(rolesQuery);
  const [editing, setEditing] = useState<Editing>({ kind: "create" });
  const [editorOpen, setEditorOpen] = useState(false);

  const save = async (terms: RoleTerms): Promise<string | null> => {
    const result =
      editing.kind === "create"
        ? await api.call("employees/roles/create", { id: uuidv7(), ...terms })
        : await api.call("employees/roles/update", { id: editing.role.id, ...terms });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await queryClient.invalidateQueries({ queryKey: rolesQuery.queryKey });
    return null;
  };

  const openEditor = (next: Editing) => {
    setEditing(next);
    setEditorOpen(true);
  };

  return (
    <>
      <EditSheetBody>
        <div className="flex justify-end">
          <Button
            onClick={() => {
              openEditor({ kind: "create" });
            }}
          >
            <PlusIcon aria-hidden />
            {t("roles.add")}
          </Button>
        </div>
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
                    openEditor({ kind: "edit", role });
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
      </EditSheetBody>
      <RoleSheet
        open={editorOpen}
        onOpenChange={setEditorOpen}
        initial={editing.kind === "edit" ? editing.role : null}
        onSave={save}
      />
    </>
  );
}
