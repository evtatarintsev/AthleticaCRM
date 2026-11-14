import type { UseQueryResult } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { GroupRosterResponse } from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { membershipGroups } from "./groupOverview";

/**
 * Вкладка «Абонементы»: состав [roster], сгруппированный по названию абонемента, действующего в
 * группе, с числом участников и числом заканчивающихся; участники без абонемента — отдельной группой.
 */
export function GroupMembershipsTab({
  roster,
}: {
  readonly roster: UseQueryResult<GroupRosterResponse>;
}) {
  const { t } = useI18n();
  if (roster.isError) {
    return <FormAlert message={t("groups.clients.loadError")} />;
  }
  if (roster.data === undefined) {
    return <Skeleton className="h-32 w-full" />;
  }
  const groups = membershipGroups(roster.data.clients);
  if (groups.length === 0) {
    return <p className="text-sm text-muted-foreground">{t("groups.detail.clientsEmpty")}</p>;
  }
  return (
    <ul className="divide-y rounded-xl border bg-card">
      {groups.map((group) => {
        const title = group.name ?? t("groups.memberships.none");
        return (
          <li key={group.name ?? ""} className="space-y-1.5 px-4 py-3">
            <h2 className="flex flex-wrap items-baseline gap-x-2 text-sm">
              <span className="font-semibold">{title}</span>
              <span className="text-muted-foreground">
                — {t("groups.memberships.count", { count: group.clients.length })}
              </span>
              {group.ending > 0 && (
                <span className="text-amber-700 dark:text-amber-300">
                  {t("groups.memberships.ending", { count: group.ending })}
                </span>
              )}
            </h2>
            <ul className="flex flex-wrap gap-x-3 gap-y-1 text-sm text-muted-foreground">
              {group.clients.map((client) => (
                <li key={client.id}>
                  <Link
                    to="/clients/$clientId"
                    params={{ clientId: client.id }}
                    className="hover:text-foreground hover:underline"
                  >
                    {client.name}
                  </Link>
                </li>
              ))}
            </ul>
          </li>
        );
      })}
    </ul>
  );
}
