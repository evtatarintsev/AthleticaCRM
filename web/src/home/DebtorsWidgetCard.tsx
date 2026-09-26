import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import type { BranchId, DashboardWidgetDebtors } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { debtorsRequest, debtorsShowAllSearch } from "./dashboardSettings";
import { ListWidgetCard } from "./ListWidgetCard";

/** Виджет главной «Должники»: клиенты с задолженностью, отсортированные по имени. */
export function DebtorsWidgetCard({
  api,
  branchId,
  widget,
  title,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly widget: DashboardWidgetDebtors;
  readonly title: string;
}) {
  const { t, format } = useI18n();
  const query = useQuery(apiQuery(api, branchId, "clients/list", debtorsRequest(widget)));
  const items = query.data?.clients;
  const total = query.data?.total ?? 0;
  return (
    <ListWidgetCard
      title={title}
      isPending={query.isPending}
      isError={query.isError}
      items={items}
      emptyKey="home.debtorsEmpty"
      errorKey="home.debtorsError"
      renderItem={(client) => (
        <div className="flex items-center justify-between gap-2 px-2">
          <Link
            to="/clients/$clientId"
            params={{ clientId: client.id }}
            className="min-w-0 truncate text-primary hover:underline"
          >
            {client.name}
          </Link>
          <span
            className={cn("shrink-0 text-sm", client.balance.minorUnits < 0 && "text-destructive")}
          >
            {format.money(client.balance)}
          </span>
        </div>
      )}
      footer={
        !query.isPending && !query.isError && total > (items?.length ?? 0) ? (
          <Link
            to="/clients"
            search={debtorsShowAllSearch()}
            className="block w-full rounded-md px-2 py-1.5 text-center text-sm text-primary hover:bg-accent/50"
          >
            {t("home.widgetShowAll", { total })}
          </Link>
        ) : null
      }
    />
  );
}
