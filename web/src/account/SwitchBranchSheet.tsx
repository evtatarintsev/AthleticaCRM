import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId } from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { myBranchesQuery, sessionQuery } from "@/query/queries";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { useSwitchBranch } from "./useSwitchBranch";

/** Панель смены филиала, открытая при [open]. */
export function SwitchBranchSheet({
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
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("branch.title")}>
      <SwitchBranchForm api={api} />
    </EditSheet>
  );
}

/**
 * Выбор филиала из доступных пользователю: текущий отмечен, другой выбирается в списке
 * и применяется кнопкой «Сохранить». Если выбран текущий, «Сохранить» просто закрывает панель.
 */
function SwitchBranchForm({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const branches = useQuery(myBranchesQuery(api));
  const switching = useSwitchBranch(api);
  const [selected, setSelected] = useState<BranchId>(me.currentBranch.id);

  return (
    <EditSheetForm
      dirty={selected !== me.currentBranch.id}
      submitting={switching.isPending}
      onSubmit={() => {
        const branch = branches.data?.branches.find((b) => b.id === selected);
        if (branch === undefined || branch.id === me.currentBranch.id) {
          close();
          return;
        }
        switching.mutate(branch, { onSuccess: close });
      }}
    >
      {branches.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      )}
      {branches.isError && <FormAlert message={t("branch.loadError")} />}
      {branches.data !== undefined && (
        <fieldset className="space-y-2" disabled={switching.isPending}>
          <legend className="sr-only">{t("branch.title")}</legend>
          {branches.data.branches.map((branch) => {
            const current = branch.id === me.currentBranch.id;
            return (
              <label
                key={branch.id}
                className={cn(
                  "flex cursor-pointer items-center gap-3 rounded-md border px-3 py-3 transition-colors hover:bg-accent/50",
                  selected === branch.id && "border-primary bg-primary/5",
                )}
              >
                <input
                  type="radio"
                  name="branch"
                  value={branch.id}
                  checked={selected === branch.id}
                  onChange={() => {
                    setSelected(branch.id);
                  }}
                  className="accent-primary"
                />
                <span className="min-w-0 flex-1 truncate">{branch.name}</span>
                {current && (
                  <span className="text-xs text-muted-foreground">{t("branch.current")}</span>
                )}
              </label>
            );
          })}
        </fieldset>
      )}
    </EditSheetForm>
  );
}
