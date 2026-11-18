import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { Cake } from "lucide-react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  DashboardSettings,
  DashboardWidgetBirthdays,
  LocalDate,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { Avatar } from "@/ui/Avatar";
import { birthdaysRequest, birthdaysShowAllSearch } from "./dashboardSettings";
import { isBirthdayToday, upcomingAge } from "./homeTime";
import { WidgetCard } from "./WidgetCard";

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
  const { t, format } = useI18n();
  const query = useQuery(apiQuery(api, branchId, "clients/list", birthdaysRequest(widget, today)));
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
      emptyKey="home.birthdaysEmpty"
      errorKey="home.birthdaysError"
      footer={
        !query.isPending && !query.isError && total > (items?.length ?? 0) ? (
          <Link
            to="/clients"
            search={birthdaysShowAllSearch(widget.window)}
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
              className={cn(
                "flex items-center gap-3 rounded-xl px-2.5 py-2.5 hover:bg-accent focus-visible:outline-2 focus-visible:outline-ring",
                client.birthday !== null &&
                  isBirthdayToday(client.birthday, today) &&
                  "bg-gradient-to-r from-pink-50 to-transparent dark:from-pink-950/60",
              )}
            >
              <Avatar api={api} uploadId={client.avatarId} name={client.name} className="size-8" />
              <span className="min-w-0 flex-1 text-sm">
                <span className="block truncate font-medium">{client.name}</span>
                {client.birthday !== null && (
                  <span className="block text-xs text-muted-foreground">
                    {t("home.birthdaysAge", { count: upcomingAge(client.birthday, today) })}
                  </span>
                )}
              </span>
              {client.birthday !== null &&
                (isBirthdayToday(client.birthday, today) ? (
                  <span className="inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-full bg-pink-100 px-2 py-0.5 text-xs font-semibold text-pink-700 dark:bg-pink-950 dark:text-pink-300">
                    <Cake className="size-3.5" aria-hidden />
                    {t("home.today")}
                  </span>
                ) : (
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {format.dayMonth(client.birthday)}
                  </span>
                ))}
            </Link>
          </li>
        ))}
      </ul>
    </WidgetCard>
  );
}
