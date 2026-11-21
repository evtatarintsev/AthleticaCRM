import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  DashboardSettings,
  DashboardWidgetDebtors,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { Avatar } from "@/ui/Avatar";
import { apiQuery } from "@/query/queries";
import { debtorsRequest, debtorsShowAllSearch } from "./dashboardSettings";
import { WidgetCard } from "./WidgetCard";

/** Виджет главной «Должники»: клиенты с задолженностью, отсортированные по имени. */
export function DebtorsWidgetCard({
  api,
  branchId,
  settings,
  widget,
  title,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly settings: DashboardSettings;
  readonly widget: DashboardWidgetDebtors;
  readonly title: string;
}) {
  const { t, format } = useI18n();
  const query = useQuery(apiQuery(api, branchId, "clients/list", debtorsRequest(widget)));
  const items = query.data?.clients;
  const total = query.data?.total ?? 0;
  return (
    <WidgetCard
      api={api}
      settings={settings}
      widget={widget}
      title={title}
      count={total}
      state={
        query.isPending
          ? "pending"
          : query.isError
            ? "error"
            : (items?.length ?? 0) === 0
              ? "empty"
              : "ready"
      }
      emptyKey="home.debtorsEmpty"
      errorKey="home.debtorsError"
      footer={
        !query.isPending && !query.isError && total > (items?.length ?? 0) ? (
          <Link
            to="/clients"
            search={debtorsShowAllSearch()}
            className="mx-1.5 mb-1.5 block rounded-xl px-2.5 py-2 text-center text-sm font-medium text-primary hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring"
          >
            {t("home.widgetShowAll", { total })}
          </Link>
        ) : null
      }
    >
      <ul>
        {items?.map((client) => (
          <li key={client.id}>
            <Link
              to="/clients/$clientId"
              params={{ clientId: client.id }}
              className="flex items-center gap-3 rounded-xl px-2.5 py-2.5 hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring"
            >
              <Avatar api={api} uploadId={client.avatarId} name={client.name} className="size-8" />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">{client.name}</span>
              <span
                className={cn(
                  "shrink-0 whitespace-nowrap text-sm font-semibold tabular-nums",
                  client.balance.minorUnits < 0 && "text-destructive",
                )}
              >
                {format.money(client.balance)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </WidgetCard>
  );
}
