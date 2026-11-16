import { Link } from "@tanstack/react-router";
import {
  ArchiveIcon,
  ArchiveRestoreIcon,
  MessageSquareIcon,
  MoreHorizontalIcon,
  PencilIcon,
  PhoneIcon,
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
    <div className="relative flex flex-wrap items-center gap-x-4 gap-y-3">
      <div className="flex min-w-0 flex-[1_1_360px] items-center gap-3.5 max-sm:pr-12">
        <Avatar
          api={api}
          uploadId={client.avatarId}
          name={client.name}
          className="size-12 text-base sm:size-14 sm:text-lg"
        />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl leading-tight font-bold tracking-tight sm:text-[28px]">
              {client.name}
            </h1>
            {archived && (
              <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground">
                {t("clients.view.archived")}
              </span>
            )}
          </div>
          <p className="mt-1 flex flex-wrap gap-x-3.5 gap-y-1 text-sm text-foreground/80">
            <span>
              {[
                t(`gender.${client.gender}`),
                ...(client.birthday === null
                  ? []
                  : [t("clientCard.meta.age", { count: ageOn(client.birthday, new Date()) })]),
              ].join(" · ")}
            </span>
            {leadSourceName !== null && (
              <span className="text-muted-foreground">
                {t("clientCard.meta.source", { name: leadSourceName })}
              </span>
            )}
            {phone !== undefined && (
              <a
                className="inline-flex items-center gap-1 font-medium text-primary hover:underline"
                href={`tel:${phone.value}`}
              >
                <PhoneIcon aria-hidden className="size-3.5" />
                {phone.value}
              </a>
            )}
          </p>
        </div>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-20 flex gap-2 border-t bg-card p-3 empty:hidden max-sm:[&>*]:flex-1 sm:static sm:border-0 sm:bg-transparent sm:p-0">
        {!archived && (
          <Button onClick={onIssueSubscription}>
            <TicketIcon aria-hidden />
            {t("clients.detail.issueSubscription")}
          </Button>
        )}
        {chatEnabled && (
          <Button variant="outline" onClick={onOpenChat}>
            <MessageSquareIcon aria-hidden />
            {t("messaging.open")}
          </Button>
        )}
      </div>
      <div className="absolute top-0 right-0 sm:static">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button size="icon" variant="outline" aria-label={t("clientCard.action.more")}>
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
