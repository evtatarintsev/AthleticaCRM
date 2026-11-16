import { Link } from "@tanstack/react-router";
import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  MessageSquareIcon,
  MoreHorizontalIcon,
  PencilIcon,
  TicketIcon,
} from "lucide-react";
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
import { Avatar } from "@/ui/Avatar";

/** Полных лет на дату [today] при дате рождения [birthday] (`YYYY-MM-DD`). */
function ageOn(birthday: string, today: Date): number {
  const [year = 0, month = 1, day = 1] = birthday.split("-").map(Number);
  const beforeBirthday =
    today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day);
  return today.getFullYear() - year - (beforeBirthday ? 1 : 0);
}

/** Имя, основные контакты и действия; на телефоне действия закреплены снизу. */
export function ClientCardHeader({
  api,
  client,
  leadSourceName,
  chatEnabled,
  onTaskCreate,
  onAddGroup,
  onSections,
  onToggleArchived,
  onIssueSubscription,
  onOpenChat,
}: {
  readonly api: ApiClient;
  readonly client: ClientDetailResponse;
  readonly leadSourceName: string | null;
  readonly chatEnabled: boolean;
  readonly onTaskCreate: (() => void) | undefined;
  readonly onAddGroup: () => void;
  readonly onSections: () => void;
  readonly onToggleArchived: (archived: boolean) => void;
  /** Открывает панель выдачи абонемента. */
  readonly onIssueSubscription: () => void;
  /** Открывает панель «Чат» с клиентом. */
  readonly onOpenChat: () => void;
}) {
  const { t } = useI18n();
  const archived = client.state === "ARCHIVED";
  const clientId = client.id;

  const phone = client.contacts.find((contact) => contact.type === "PHONE");
  return (
    <div className="relative space-y-4 rounded-lg border bg-card p-4">
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
          {phone !== undefined && (
            <a className="text-sm text-primary hover:underline" href={`tel:${phone.value}`}>
              {phone.value}
            </a>
          )}
        </div>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 flex gap-2 border-t bg-card p-3 sm:static sm:flex-wrap sm:border-0 sm:p-0">
        {!archived && (
          <Button size="sm" onClick={onIssueSubscription}>
            <TicketIcon aria-hidden />
            {t("clients.detail.issueSubscription")}
          </Button>
        )}
        {chatEnabled && (
          <Button size="sm" variant="outline" onClick={onOpenChat}>
            <MessageSquareIcon aria-hidden />
            {t("messaging.open")}
          </Button>
        )}
      </div>
      <div className="absolute right-4 top-4 sm:static">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button size="sm" variant="outline" aria-label={t("clientCard.action.more")}>
              <MoreHorizontalIcon aria-hidden />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {!archived && (
              <DropdownMenuItem asChild>
                <Link to="/clients/$clientId/edit" params={{ clientId }}>
                  <PencilIcon aria-hidden />
                  {t("action.edit")}
                </Link>
              </DropdownMenuItem>
            )}
            <DropdownMenuItem onSelect={onAddGroup}>
              {t("clients.detail.addToGroup")}
            </DropdownMenuItem>
            {onTaskCreate !== undefined && (
              <DropdownMenuItem onSelect={onTaskCreate}>{t("tasks.create")}</DropdownMenuItem>
            )}
            <DropdownMenuItem onSelect={onSections}>
              {t("clientOverview.sections")}
            </DropdownMenuItem>
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
    </div>
  );
}
