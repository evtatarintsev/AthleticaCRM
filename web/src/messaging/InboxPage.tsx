import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LinkIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import {
  InstantSchema,
  type ChannelIntegrationId,
  type TriageChatSchema,
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

/** Чат связанного канала: интеграция и адрес в ней. */
interface ChatKey {
  readonly integrationId: ChannelIntegrationId;
  readonly address: string;
}

/** Истина, если чат [chat] — это чат [key]. */
function isChat(chat: TriageChatSchema, key: ChatKey | null): boolean {
  return key !== null && chat.integrationId === key.integrationId && chat.address === key.address;
}

/** Момент [value] из ответа сервера в формате языка интерфейса; нераспознанный — как есть. */
function formatMoment(format: I18n["format"], value: string): string {
  const instant = InstantSchema.safeParse(value);
  return instant.success ? format.dateTime(instant.data) : value;
}

/** Имя собеседника для заголовка: имя в канале, `@username` или адрес чата. */
function chatTitle(chat: TriageChatSchema): string {
  if (chat.displayName !== "") {
    return chat.displayName;
  }
  return chat.username === null ? chat.address : `@${chat.username}`;
}

/**
 * «Входящие»: чаты связанных каналов, которые не удалось автоматически отнести к клиенту.
 * Сотрудник выбирает чат, читает накопленные сообщения и привязывает чат к клиенту —
 * после этого сообщения попадают в переписку клиента, а чат пропадает из списка.
 */
export function InboxPage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const chatsQuery = apiQuery(api, branchId, "messaging/triage/list");
  const chats = useQuery(chatsQuery);
  const [selectedKey, setSelectedKey] = useState<ChatKey | null>(null);
  const [picking, setPicking] = useState(false);
  const selected = chats.data?.chats.find((chat) => isChat(chat, selectedKey)) ?? null;

  const assign = async (chat: TriageChatSchema, client: PickedClient): Promise<string | null> => {
    const result = await api.call("messaging/triage/assign", {
      integrationId: chat.integrationId,
      address: chat.address,
      clientId: client.id,
    });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    setSelectedKey(null);
    await queryClient.invalidateQueries({ queryKey: chatsQuery.queryKey });
    toast.success(t("inbox.linked", { name: client.name }));
    return null;
  };

  return (
    <section>
      <PageHeader title={t("inbox.title")} />
      {chats.isError && <FormAlert message={t("inbox.loadError")} />}
      {chats.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      )}
      {chats.data?.chats.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">{t("inbox.empty")}</p>
      )}
      {chats.data !== undefined && chats.data.chats.length > 0 && (
        <div className="grid gap-4 md:grid-cols-[minmax(0,20rem)_minmax(0,1fr)]">
          <ul className="divide-y rounded-lg border bg-card" aria-label={t("inbox.chats")}>
            {chats.data.chats.map((chat) => (
              <li key={`${chat.integrationId}:${chat.address}`}>
                <ChatRow
                  chat={chat}
                  active={isChat(chat, selectedKey)}
                  onSelect={() => {
                    setSelectedKey({ integrationId: chat.integrationId, address: chat.address });
                  }}
                />
              </li>
            ))}
          </ul>
          {selected === null ? (
            <p className="rounded-lg border border-dashed p-8 text-center text-sm text-muted-foreground">
              {t("inbox.selectChat")}
            </p>
          ) : (
            <ChatPanel
              api={api}
              chat={selected}
              onLink={() => {
                setPicking(true);
              }}
            />
          )}
        </div>
      )}
      {selected !== null && (
        <ClientPickerSheet
          api={api}
          branchId={branchId}
          open={picking}
          onOpenChange={setPicking}
          title={t("inbox.pickClient")}
          mode="single"
          unavailable={new Map()}
          initialQuery={selected.phone ?? ""}
          onSubmit={async (clients) => {
            const [client] = clients;
            return client === undefined ? null : assign(selected, client);
          }}
        />
      )}
    </section>
  );
}

/** Строка списка чатов: собеседник, канал, число сообщений и последнее сообщение. */
function ChatRow({
  chat,
  active,
  onSelect,
}: {
  readonly chat: TriageChatSchema;
  readonly active: boolean;
  readonly onSelect: () => void;
}) {
  const { t, format } = useI18n();
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={active ? "true" : undefined}
      className={cn("w-full px-4 py-3 text-left hover:bg-accent", active && "bg-accent")}
    >
      <span className="flex items-baseline gap-2">
        <span className="min-w-0 flex-1 truncate font-medium">{chatTitle(chat)}</span>
        <span className="shrink-0 text-xs text-muted-foreground">
          {formatMoment(format, chat.lastMessageAt)}
        </span>
      </span>
      <span className="block truncate text-sm text-muted-foreground">{chat.lastMessageBody}</span>
      <span className="block truncate text-xs text-muted-foreground">
        {chat.channelName} · {t("inbox.unresolvedCount", { count: chat.unresolvedCount })}
      </span>
    </button>
  );
}

/** Правая панель: данные собеседника, накопленные сообщения и привязка к клиенту. */
function ChatPanel({
  api,
  chat,
  onLink,
}: {
  readonly api: ApiClient;
  readonly chat: TriageChatSchema;
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
    <div className="space-y-4 rounded-lg border bg-card p-4">
      <div className="flex flex-wrap items-start gap-3">
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
