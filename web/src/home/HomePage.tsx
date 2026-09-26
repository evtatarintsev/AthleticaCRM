import { useQuery } from "@tanstack/react-query";
import { SettingsIcon } from "lucide-react";
import { useMemo, useState } from "react";
import type { ApiClient } from "@/api/client";
import { displaySettingsQuery, todayLocalDate } from "@/clients/clientsQueries";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { BirthdaysWidgetCard } from "./BirthdaysWidgetCard";
import { orderedVisible, resolveTitle, withDashboardDefaults } from "./dashboardSettings";
import { DashboardSettingsDialog } from "./DashboardSettingsDialog";
import { DebtorsWidgetCard } from "./DebtorsWidgetCard";
import { SessionsWidgetCard } from "./SessionsWidgetCard";

/**
 * Главная веб-клиента: настраиваемые виджеты (занятия сегодня, должники, дни рождения),
 * паритет с `HomeScreen` KMP-клиента. На широком экране виджеты стоят в ряд, на узком —
 * друг под другом. Настройки открываются шестерёнкой в заголовке.
 */
export function HomePage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const display = useQuery(displaySettingsQuery(api));
  const dashboard = useMemo(
    () => (display.data === undefined ? undefined : withDashboardDefaults(display.data.dashboard)),
    [display.data],
  );
  const [settingsOpen, setSettingsOpen] = useState(false);
  const today = todayLocalDate();

  return (
    <section>
      <PageHeader
        title={t("home.title")}
        actions={
          <Button
            variant="outline"
            size="icon"
            aria-label={t("home.dashboardSettingsTitle")}
            onClick={() => {
              setSettingsOpen(true);
            }}
          >
            <SettingsIcon aria-hidden />
          </Button>
        }
      />
      {dashboard === undefined ? (
        <div className="flex flex-col gap-4 lg:flex-row">
          <Skeleton className="h-64 flex-1" />
          <Skeleton className="h-64 flex-1" />
          <Skeleton className="h-64 flex-1" />
        </div>
      ) : (
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
          {orderedVisible(dashboard).map((widget) => {
            const title = resolveTitle(t, widget);
            switch (widget.type) {
              case "sessions":
                return (
                  <SessionsWidgetCard key={widget.id} api={api} branchId={branchId} title={title} />
                );
              case "debtors":
                return (
                  <DebtorsWidgetCard
                    key={widget.id}
                    api={api}
                    branchId={branchId}
                    widget={widget}
                    title={title}
                  />
                );
              case "birthdays":
                return (
                  <BirthdaysWidgetCard
                    key={widget.id}
                    api={api}
                    branchId={branchId}
                    widget={widget}
                    today={today}
                    title={title}
                  />
                );
            }
          })}
        </div>
      )}
      {dashboard !== undefined && (
        <DashboardSettingsDialog
          api={api}
          open={settingsOpen}
          settings={dashboard}
          onOpenChange={setSettingsOpen}
        />
      )}
    </section>
  );
}
