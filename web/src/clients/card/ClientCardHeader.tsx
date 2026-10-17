import { Link } from "@tanstack/react-router";
import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  CakeIcon,
  ClockAlertIcon,
  MessageSquareIcon,
  MoreHorizontalIcon,
  PencilIcon,
  TicketIcon,
  TicketXIcon,
  WalletIcon,
} from "lucide-react";
import type { ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type { ClientDetailResponse } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { Avatar } from "@/ui/Avatar";
import type { ClientAlert } from "./clientCardAlerts";

/** Полных лет на дату [today] при дате рождения [birthday] (`YYYY-MM-DD`). */
function ageOn(birthday: string, today: Date): number {
  const [year = 0, month = 1, day = 1] = birthday.split("-").map(Number);
  const beforeBirthday =
    today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day);
  return today.getFullYear() - year - (beforeBirthday ? 1 : 0);
}

/**
 * Шапка карточки: кто клиент (аватар, имя, пол, возраст, источник), быстрая информация
 * (баланс, абонемент, посещения), основные действия кнопками, остальные — в меню «Ещё»,
 * ниже — предупреждения.
 */
export function ClientCardHeader({
  api,
  client,
  leadSourceName,
  quickInfo,
  alerts,
  onToggleArchived,
  onIssueSubscription,
  onOpenChat,
}: {
  readonly api: ApiClient;
  readonly client: ClientDetailResponse;
  readonly leadSourceName: string | null;
  /** Плитки быстрой информации под именем. */
  readonly quickInfo: ReactNode;
  readonly alerts: readonly ClientAlert[];
  readonly onToggleArchived: (archived: boolean) => void;
  /** Открывает панель выдачи абонемента. */
  readonly onIssueSubscription: () => void;
  /** Открывает панель «Чат» с клиентом. */
  readonly onOpenChat: () => void;
}) {
  const { t } = useI18n();
  const archived = client.state === "ARCHIVED";
  const clientId = client.id;

  return (
    <div className="space-y-4 rounded-lg border bg-card p-4">
      <div className="flex flex-wrap items-start gap-4">
        <Avatar
          api={api}
          uploadId={client.avatarId}
          name={client.name}
          className="size-12 text-base sm:size-16 sm:text-xl"
        />
        <div className="min-w-0 flex-1 space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">{client.name}</h1>
            {archived && (
              <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground">
                {t("clients.view.archived")}
              </span>
            )}
          </div>
          <p className="flex flex-wrap gap-x-3 gap-y-1 text-sm text-muted-foreground">
            <span>{t(`gender.${client.gender}`)}</span>
            {client.birthday !== null && (
              <span>{t("clientCard.meta.age", { count: ageOn(client.birthday, new Date()) })}</span>
            )}
            {leadSourceName !== null && (
              <span>{t("clientCard.meta.source", { name: leadSourceName })}</span>
            )}
          </p>
        </div>
      </div>

      {quickInfo}

      <div className="flex flex-wrap gap-2">
        {!archived && (
          <Button size="sm" onClick={onIssueSubscription}>
            <TicketIcon aria-hidden />
            {t("clients.detail.issueSubscription")}
          </Button>
        )}
        <Button size="sm" variant="outline" onClick={onOpenChat}>
          <MessageSquareIcon aria-hidden />
          {t("messaging.open")}
        </Button>
        {!archived && (
          <Button size="sm" variant="outline" className="max-sm:hidden" asChild>
            <Link to="/clients/$clientId/edit" params={{ clientId }}>
              <PencilIcon aria-hidden />
              {t("action.edit")}
            </Link>
          </Button>
        )}
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button size="sm" variant="outline">
              <MoreHorizontalIcon aria-hidden />
              {t("clientCard.action.more")}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {!archived && (
              <DropdownMenuItem className="sm:hidden" asChild>
                <Link to="/clients/$clientId/edit" params={{ clientId }}>
                  <PencilIcon aria-hidden />
                  {t("action.edit")}
                </Link>
              </DropdownMenuItem>
            )}
            <DropdownMenuItem
              onSelect={() => {
                onToggleArchived(!archived);
              }}
            >
              {archived ? <ArchiveRestoreIcon aria-hidden /> : <ArchiveIcon aria-hidden />}
              {t(archived ? "action.restore" : "action.archive")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {alerts.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label={t("clientCard.alerts")}>
          {alerts.map((alert) => (
            <AlertChip key={alert.type} alert={alert} />
          ))}
        </ul>
      )}
    </div>
  );
}

/** Важность предупреждения: определяет цвет. */
type Severity = "danger" | "warning" | "info";

/** Предупреждение в шапке: иконка, текст и цвет по важности. */
function AlertChip({ alert }: { readonly alert: ClientAlert }) {
  const { t, format } = useI18n();
  const view = ((): { severity: Severity; icon: ReactNode; text: string } => {
    switch (alert.type) {
      case "archived":
        return {
          severity: "info",
          icon: <ArchiveIcon aria-hidden />,
          text: t("clients.detail.archivedBanner"),
        };
      case "debt":
        return {
          severity: "danger",
          icon: <WalletIcon aria-hidden />,
          text: t("clientCard.alert.debt", { amount: format.money(alert.amount) }),
        };
      case "noActiveSubscription":
        return {
          severity: "warning",
          icon: <TicketXIcon aria-hidden />,
          text: t("clientCard.alert.noActiveSubscription"),
        };
      case "overdueTasks":
        return {
          severity: "danger",
          icon: <ClockAlertIcon aria-hidden />,
          text: t("clientCard.alert.overdueTasks", { count: alert.count }),
        };
      case "birthday":
        return {
          severity: "info",
          icon: <CakeIcon aria-hidden />,
          text: t("clientCard.alert.birthday", { date: format.date(alert.on) }),
        };
    }
  })();
  return (
    <li
      className={cn(
        "flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-sm [&_svg]:size-4 [&_svg]:shrink-0",
        view.severity === "danger" &&
          "border-red-200 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-300",
        view.severity === "warning" &&
          "border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200",
        view.severity === "info" &&
          "border-sky-200 bg-sky-50 text-sky-900 dark:border-sky-900 dark:bg-sky-950/40 dark:text-sky-200",
      )}
    >
      {view.icon}
      {view.text}
    </li>
  );
}
