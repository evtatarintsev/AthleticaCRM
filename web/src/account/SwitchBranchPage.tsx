import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { CheckIcon } from "lucide-react";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { myBranchesQuery, sessionQuery } from "@/query/queries";
import { PageHeader } from "@/ui/PageHeader";
import { useSwitchBranch } from "./useSwitchBranch";

/** Выбор филиала из доступных пользователю; текущий отмечен и не выбирается. */
export function SwitchBranchPage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const branches = useQuery(myBranchesQuery(api));
  const switching = useSwitchBranch(api);

  return (
    <section className="max-w-md">
      <PageHeader title={t("branch.title")} />
      {branches.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      )}
      {branches.isError && <FormAlert message={t("branch.loadError")} />}
      {branches.data !== undefined && (
        <ul className="space-y-2">
          {branches.data.branches.map((branch) => {
            const current = branch.id === me.currentBranch.id;
            return (
              <li key={branch.id}>
                <Button
                  type="button"
                  variant="outline"
                  size="lg"
                  disabled={current || switching.isPending}
                  aria-current={current ? "true" : undefined}
                  aria-busy={switching.isPending && switching.variables.id === branch.id}
                  onClick={() => {
                    switching.mutate(branch);
                  }}
                  className="w-full justify-between disabled:opacity-100"
                >
                  <span className="truncate">{branch.name}</span>
                  {current && (
                    <span className="flex items-center gap-1 text-xs text-muted-foreground">
                      <CheckIcon aria-hidden className="text-primary" />
                      {t("branch.current")}
                    </span>
                  )}
                </Button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
