import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon, LinkIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import {
  InstantSchema,
  type InboxItemSchemaClient,
  type ClientId,
  type InboxFilterSchema,
  type InboxItemSchema,
  type InboxItemSchemaUnresolved,
} from "@/api/generated/contracts";
import { ClientPickerSheet, type PickedClient } from "@/clients/ClientPickerSheet";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type I18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { ConversationThread } from "./ConversationThread";
import { invalidateInbox, useInboxPages } from "./inboxQueries";
import {
  chatParam,
  inboxFilter,
  inboxSelection,
  type ChatKey,
  type InboxSearch,
  type InboxSelection,
} from "./inboxSearch";

/** Больше этого числа бейдж непрочитанных показывает «99+». */
const BADGE_LIMIT = 99;

/** Истина, если запись [item] — неразобранный чат [key]. */
function isChat(item: InboxItemSchema, key: ChatKey): item is InboxItemSchemaUnresolved {
  return (
    item.type === "unresolved" &&
    item.integrationId === key.integrationId &&
    item.address === key.address
  );
}

/** Истина, если запись [item] выбрана в [selection]. */
function isSelected(item: InboxItemSchema, selection: InboxSelection): boolean {
  switch (selection.kind) {
    case "client":
      return item.type === "client" && item.clientId === selection.clientId;
    case "chat":
      return isChat(item, selection.chat);
    case "none":
      return false;
  }
}

/** Ключ записи [item] в списке. */
function itemKey(item: InboxItemSchema): string {
  switch (item.type) {
    case "client":
      return `client:${item.clientId}`;
    case "unresolved":
      return `chat:${chatParam(item)}`;
  }
}

/** Момент [value] из ответа сервера в формате языка интерфейса; нераспознанный — как есть. */
function formatMoment(format: I18n["format"], value: string): string {
  const instant = InstantSchema.safeParse(value);
  return instant.success ? format.dateTime(instant.data) : value;
}

/** Имя собеседника неразобранного чата: имя в канале, `@username` или адрес чата. */
function chatTitle(chat: InboxItemSchemaUnresolved): string {
  if (chat.displayName !== "") {
    return chat.displayName;
  }
  return chat.username === null ? chat.address : `@${chat.username}`;
}

/** Подпись пустого списка под фильтром [filter]. */
function emptyLabel(t: I18n["t"], filter: InboxFilterSchema): string {
  switch (filter) {
    case "ALL":
      return t("inbox.empty");
    case "UNREAD":
      return t("inbox.emptyUnread");
    case "UNRESOLVED":
      return t("inbox.emptyUnresolved");
  }
}

/**
 * «Входящие»: беседы клиентов и неразобранные чаты в одной ленте, свежие первыми.
 * Выбранная беседа открывается рядом — с лентой и ответом; неразобранный чат — с сообщениями
 * и привязкой к клиенту, после которой на его месте открывается беседа клиента.
 * Фильтр и выбранная запись хранятся в адресе ([search]); на узком экране список
 * и беседа показываются по очереди.
 */
export function InboxPage({
  api,
  search,
  onSearchChange,
}: {
  readonly api: ApiClient;
  readonly search: InboxSearch;
  readonly onSearchChange: (next: InboxSearch) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const filter = inboxFilter(search);
  const pages = useInboxPages(api, branchId, filter);
  const items = pages.data?.pages.flatMap((page) => page.items) ?? [];
  const selection = inboxSelection(search);
  const [picking, setPicking] = useState(false);

  const select = (item: InboxItemSchema): void => {
    switch (item.type) {
      case "client":
        onSearchChange({ filter: search.filter, client: item.clientId });
        return;
      case "unresolved":
        onSearchChange({ filter: search.filter, chat: chatParam(item) });
        return;
    }
  };
  const back = (): void => {
    onSearchChange({ filter: search.filter });
  };

  const assign = async (chat: ChatKey, client: PickedClient): Promise<string | null> => {
    const result = await api.call("messaging/triage/assign", {
      integrationId: chat.integrationId,
      address: chat.address,
      clientId: client.id,
    });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    toast.success(t("inbox.linked", { name: client.name }));
    onSearchChange({ client: client.id });
    await invalidateInbox(queryClient, branchId);
    return null;
  };

  const selectedChat =
    selection.kind === "chat"
      ? (items.find((item): item is InboxItemSchemaUnresolved => isChat(item, selection.chat)) ??
        null)
      : null;
  const selectedClientName =
    selection.kind === "client"
      ? (items.find(
          (item): item is InboxItemSchemaClient =>
            item.type === "client" && item.clientId === selection.clientId,
        )?.clientName ?? null)
      : null;
  const hasSelection = selection.kind !== "none";

  return (
    <section className="flex h-[calc(100vh-8rem)] flex-col">
      <PageHeader title={t("inbox.title")} />
      <FilterTabs
        filter={filter}
        onChange={(next) => {
          onSearchChange({
            ...(next === undefined ? {} : { filter: next }),
            ...(search.client === undefined ? {} : { client: search.client }),
            ...(search.chat === undefined ? {} : { chat: search.chat }),
          });
        }}
      />
      <div className="grid min-h-0 flex-1 gap-4 md:grid-cols-[minmax(0,22rem)_minmax(0,1fr)]">
        <div className={cn("min-h-0 overflow-y-auto", hasSelection && "hidden md:block")}>
          {pages.isError && <FormAlert message={t("inbox.loadError")} />}
          {pages.isPending && (
            <div className="space-y-2">
              <Skeleton className="h-16 w-full" />
              <Skeleton className="h-16 w-full" />
            </div>
          )}
          {pages.data !== undefined && items.length === 0 && (
            <p className="py-12 text-center text-muted-foreground">{emptyLabel(t, filter)}</p>
          )}
          {items.length > 0 && (
            <ul className="divide-y rounded-lg border bg-card" aria-label={t("inbox.list")}>
              {items.map((item) => (
                <li key={itemKey(item)}>
                  <InboxRow
                    item={item}
                    active={isSelected(item, selection)}
                    onSelect={() => {
                      select(item);
                    }}
                  />
                </li>
              ))}
            </ul>
          )}
          {pages.hasNextPage && (
            <div className="mt-4 flex justify-center">
              <Button
                variant="outline"
                disabled={pages.isFetchingNextPage}
                onClick={() => {
                  void pages.fetchNextPage();
                }}
              >
                {t("inbox.loadMore")}
              </Button>
            </div>
          )}
        </div>
        <div className={cn("min-h-0 flex-col", hasSelection ? "flex" : "hidden md:flex")}>
          {selection.kind === "client" && (
            <ClientPanel
              api={api}
              clientId={selection.clientId}
              clientName={selectedClientName}
              onBack={back}
            />
          )}
          {selection.kind === "chat" && selectedChat !== null && (
            <ChatPanel
              api={api}
              chat={selectedChat}
              onBack={back}
              onLink={() => {
                setPicking(true);
              }}
            />
          )}
          {(selection.kind === "none" || (selection.kind === "chat" && selectedChat === null)) && (
            <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
              {t("inbox.selectItem")}
            </p>
          )}
        </div>
      </div>
      {selectedChat !== null && (
        <ClientPickerSheet
          api={api}
          branchId={branchId}
          open={picking}
          onOpenChange={setPicking}
          title={t("inbox.pickClient")}
          mode="single"
          unavailable={new Map()}
          initialQuery={selectedChat.phone ?? ""}
          onSubmit={async (clients) => {
            const [client] = clients;
            return client === undefined ? null : assign(selectedChat, client);
          }}
        />
      )}
    </section>
  );
}

/** Переключатель фильтра списка. */
function FilterTabs({
  filter,
  onChange,
}: {
  readonly filter: InboxFilterSchema;
  readonly onChange: (next: InboxSearch["filter"]) => void;
}) {
  const { t } = useI18n();
  const tabs = [
    { value: "ALL", search: undefined, label: t("inbox.filterAll") },
    { value: "UNREAD", search: "unread", label: t("inbox.filterUnread") },
    { value: "UNRESOLVED", search: "unresolved", label: t("inbox.filterUnresolved") },
  ] as const;
  return (
    <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label={t("inbox.filter")}>
      {tabs.map((tab) => (
        <Button
          key={tab.value}
          size="sm"
          variant={filter === tab.value ? "default" : "outline"}
          aria-pressed={filter === tab.value}
          onClick={() => {
            onChange(tab.search);
          }}
        >
          {tab.label}
        </Button>
      ))}
    </div>
  );
}

/** Бейдж числа [count]; при нуле не показывается. */
function CountBadge({ count, label }: { readonly count: number; readonly label: string }) {
  if (count === 0) {
    return null;
  }
  return (
    <span
      aria-label={label}
      className="shrink-0 rounded-full bg-primary px-1.5 text-xs font-medium text-primary-foreground"
    >
      {count > BADGE_LIMIT ? `${String(BADGE_LIMIT)}+` : count}
    </span>
  );
}

/** Строка списка: беседа клиента или неразобранный чат. */
function InboxRow({
  item,
  active,
  onSelect,
}: {
  readonly item: InboxItemSchema;
  readonly active: boolean;
  readonly onSelect: () => void;
}) {
  const { t, format } = useI18n();
  const className = cn("w-full px-4 py-3 text-left hover:bg-accent", active && "bg-accent");
  switch (item.type) {
    case "client":
      return (
        <button
          type="button"
          onClick={onSelect}
          aria-current={active ? "true" : undefined}
          className={className}
        >
          <span className="flex items-baseline gap-2">
            <span
              className={cn(
                "min-w-0 flex-1 truncate",
                item.unreadCount > 0 ? "font-semibold" : "font-medium",
              )}
            >
              {item.clientName}
            </span>
            <span className="shrink-0 text-xs text-muted-foreground">
              {formatMoment(format, item.lastMessageAt)}
            </span>
          </span>
          <span className="flex items-center gap-2">
            <span className="min-w-0 flex-1 truncate text-sm text-muted-foreground">
              {item.lastMessageDirection === "OUTBOUND" && `${t("inbox.you")}: `}
              {item.lastMessageBody}
            </span>
            <CountBadge
              count={item.unreadCount}
              label={t("inbox.unreadCount", { count: item.unreadCount })}
            />
          </span>
        </button>
      );
    case "unresolved":
      return (
        <button
          type="button"
          onClick={onSelect}
          aria-current={active ? "true" : undefined}
          className={className}
        >
          <span className="flex items-baseline gap-2">
            <span className="min-w-0 flex-1 truncate font-semibold">{chatTitle(item)}</span>
            <span className="shrink-0 text-xs text-muted-foreground">
              {formatMoment(format, item.lastMessageAt)}
            </span>
          </span>
          <span className="block truncate text-sm text-muted-foreground">
            {item.lastMessageBody}
          </span>
          <span className="block truncate text-xs text-muted-foreground">
            {t("inbox.unresolved")} · {item.channelName} ·{" "}
            {t("inbox.unresolvedCount", { count: item.unresolvedCount })}
          </span>
        </button>
      );
  }
}

/** Кнопка возврата к списку на узком экране. */
function BackButton({ onBack }: { readonly onBack: () => void }) {
  const { t } = useI18n();
  return (
    <Button
      variant="ghost"
      size="icon"
      className="md:hidden"
      aria-label={t("action.back")}
      onClick={onBack}
    >
      <ArrowLeftIcon aria-hidden />
    </Button>
  );
}

/** Правая панель беседы клиента: шапка со ссылкой на карточку и переписка. */
function ClientPanel({
  api,
  clientId,
  clientName,
  onBack,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
  readonly clientName: string | null;
  readonly onBack: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="flex min-h-0 flex-1 flex-col rounded-lg border bg-card p-4">
      <div className="mb-2 flex items-center gap-2 border-b pb-3">
        <BackButton onBack={onBack} />
        <h2 className="min-w-0 flex-1 truncate font-semibold">
          {clientName ?? t("messaging.title")}
        </h2>
        <Button variant="outline" size="sm" asChild>
          <Link to="/clients/$clientId" params={{ clientId }}>
            {t("inbox.openClient")}
          </Link>
        </Button>
      </div>
      <ConversationThread key={clientId} api={api} clientId={clientId} />
    </div>
  );
}

/** Правая панель неразобранного чата: данные собеседника, сообщения и привязка к клиенту. */
function ChatPanel({
  api,
  chat,
  onBack,
  onLink,
}: {
  readonly api: ApiClient;
  readonly chat: InboxItemSchemaUnresolved;
  readonly onBack: () => void;
  readonly onLink: () => void;
}) {
  const { t, format } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const messages = useQuery(
    apiQuery(api, branchId, "messaging/triage/chat", {
      integrationId: chat.integrationId,
      address: chat.address,
    }),
  );
  return (
    <div className="min-h-0 flex-1 space-y-4 overflow-y-auto rounded-lg border bg-card p-4">
      <div className="flex flex-wrap items-start gap-3">
        <BackButton onBack={onBack} />
        <div className="min-w-0 flex-1">
          <h2 className="truncate font-semibold">{chatTitle(chat)}</h2>
          <p className="text-sm text-muted-foreground">
            {chat.channelName}
            {chat.username !== null && chat.displayName !== "" && ` · @${chat.username}`}
          </p>
          <p className="text-sm">
            {chat.phone === null ? t("inbox.noPhone") : t("inbox.phone", { phone: chat.phone })}
          </p>
        </div>
        <Button onClick={onLink}>
          <LinkIcon aria-hidden />
          {t("inbox.link")}
        </Button>
      </div>
      {messages.isError && <FormAlert message={t("inbox.loadError")} />}
      {messages.isPending && <Skeleton className="h-24 w-full" />}
      {messages.data !== undefined && (
        <ul className="space-y-2" aria-label={t("inbox.messages")}>
          {messages.data.messages.map((message) => (
            <li key={message.id} className="max-w-prose rounded-lg bg-muted px-3 py-2">
              <p className="whitespace-pre-wrap text-sm">{message.body}</p>
              <p className="text-xs text-muted-foreground">
                {formatMoment(format, message.receivedAt)}
              </p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
