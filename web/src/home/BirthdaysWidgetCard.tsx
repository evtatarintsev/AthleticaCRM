import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  DashboardSettings,
  DashboardWidgetBirthdays,
  LocalDate,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { Avatar } from "@/ui/Avatar";
import { birthdaysRequest, birthdaysShowAllSearch, yearOf } from "./dashboardSettings";
import { ListWidgetCard } from "./ListWidgetCard";

/** Виджет главной «Дни рождения»: клиенты с днём рождения в выбранном окне. */
export function BirthdaysWidgetCard({
  api,
  branchId,
  settings,
  widget,
  today,
  title,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly settings: DashboardSettings;
  readonly widget: DashboardWidgetBirthdays;
  readonly today: LocalDate;
  readonly title: string;
}) {
  const { t } = useI18n();
  const query = useQuery(apiQuery(api, branchId, "clients/list", birthdaysRequest(widget, today)));
  const items = query.data?.clients;
  const total = query.data?.total ?? 0;
  return (
    <ListWidgetCard
      api={api}
      settings={settings}
      widget={widget}
      title={title}
      isPending={query.isPending}
      isError={query.isError}
      items={items}
      emptyKey="home.birthdaysEmpty"
      errorKey="home.birthdaysError"
      renderItem={(client) => (
        <div className="flex items-center justify-between gap-2 px-2">
          <div className="flex min-w-0 flex-1 items-center gap-2">
            <Avatar api={api} uploadId={client.avatarId} name={client.name} className="size-8" />
            <Link
              to="/clients/$clientId"
              params={{ clientId: client.id }}
              className="min-w-0 truncate font-medium hover:underline"
            >
              {client.name}
            </Link>
          </div>
          {client.birthday !== null && (
            <span className="shrink-0 text-sm text-muted-foreground">
              {t("home.birthdaysAge", { age: yearOf(today) - yearOf(client.birthday) })}
            </span>
          )}
        </div>
      )}
      footer={
        !query.isPending && !query.isError && total > (items?.length ?? 0) ? (
          <Link
            to="/clients"
            search={birthdaysShowAllSearch(widget.window)}
            className="block w-full rounded-md px-2 py-1.5 text-center text-sm text-primary hover:bg-accent/50"
          >
            {t("home.widgetShowAll", { total })}
          </Link>
        ) : null
      }
    />
  );
}
