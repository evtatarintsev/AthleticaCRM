import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDownIcon, SendIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  ChannelIntegrationId,
  ClientChannelSchema,
  ClientContactId,
  ClientContactSchema,
  ClientId,
  MessageSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type I18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Канал, выбранный по умолчанию, пока пользователь не выбрал свой: первый доступный. */
function defaultChannel(channels: readonly ClientChannelSchema[]): ClientChannelSchema | null {
  return channels.find((channel) => channel.available) ?? channels[0] ?? null;
}

/**
 * Панель «Чат» с клиентом [clientId] справа поверх карточки клиента. Содержимое монтируется
 * заново при каждом открытии, поэтому черновик и выбор канала не переживают закрытие;
 * закрытие с неотправленным текстом спрашивает подтверждение.
 */
export function ConversationSheet({
  api,
  clientId,
  open,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("messaging.title")} size="lg">
      <Conversation api={api} clientId={clientId} />
    </EditSheet>
  );
}

/**
 * Переписка с клиентом (паритет с `ConversationScreen` KMP-клиента): лента сообщений,
 * выбор канала и адреса-получателя, отправка. Доступность каналов и адреса считает сервер
 * (`messaging/channels`). Контакты редактируются на карточке клиента —
 * здесь они только определяют доступность каналов. Неотправленный текст сообщается панели
 * как несохранённые изменения.
 */
function Conversation({ api, clientId }: { readonly api: ApiClient; readonly clientId: ClientId }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { reportGuard } = useEditSheet();
  const branchId = useSession(api).currentBranch.id;
  const conversationQuery = apiQuery(api, branchId, "messaging/conversation", { clientId });
  const conversation = useQuery(conversationQuery);
  const channelsQuery = apiQuery(api, branchId, "messaging/channels", { clientId });
  const channelsResult = useQuery(channelsQuery);
  const channels = channelsResult.data?.channels ?? [];

  const [selectedChannelId, setSelectedChannelId] = useState<ChannelIntegrationId | null>(null);
  const [selectedContactId, setSelectedContactId] = useState<ClientContactId | null>(null);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const feedRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const ready = conversation.data !== undefined && channelsResult.data !== undefined;
  const messageCount = conversation.data?.messages.length ?? 0;

  useEffect(() => {
    reportGuard({ dirty: draft.trim() !== "", submitting: sending });
  }, [reportGuard, draft, sending]);

  useEffect(() => {
    if (ready) {
      inputRef.current?.focus();
    }
  }, [ready]);

  useEffect(() => {
    const feed = feedRef.current;
    if (feed !== null) {
      feed.scrollTop = feed.scrollHeight;
    }
  }, [ready, messageCount]);

  const selectedChannel =
    (selectedChannelId === null
      ? defaultChannel(channels)
      : channels.find((channel) => channel.id === selectedChannelId)) ?? null;
  const options = selectedChannel?.contacts ?? [];
  const effectiveContactId = selectedContactId ?? options[0]?.id ?? null;
  const available = selectedChannel?.available ?? false;
  const canSend = !sending && draft.trim() !== "" && available;

  const send = async (): Promise<void> => {
    if (selectedChannel === null || !canSend) {
      return;
    }
    setSending(true);
    setSendError(null);
    const result = await api.call("messaging/send", {
      clientId,
      channelIntegrationId: selectedChannel.id,
      body: draft.trim(),
      contactId: effectiveContactId,
    });
    setSending(false);
    if (!result.ok) {
      setSendError(apiErrorMessage(t, result.error));
      return;
    }
    queryClient.setQueryData(conversationQuery.queryKey, result.value);
    setDraft("");
  };

  const failed = conversation.isError || channelsResult.isError;
  if (failed) {
    return (
      <div className="p-4">
        <FormAlert message={t("messaging.loadError")} />
      </div>
    );
  }
  if (!ready) {
    return (
      <div className="space-y-2 p-4">
        <Skeleton className="h-16 w-2/3" />
        <Skeleton className="ml-auto h-16 w-2/3" />
      </div>
    );
  }
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div ref={feedRef} className="min-h-0 flex-1 space-y-2 overflow-y-auto p-4">
        {conversation.data.messages.length === 0 ? (
          <p className="py-12 text-center text-muted-foreground">{t("messaging.emptyThread")}</p>
        ) : (
          conversation.data.messages.map((message) => (
            <MessageBubble key={message.id} message={message} />
          ))
        )}
      </div>
      <div className="space-y-2 border-t p-4">
        {channels.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("messaging.addressNone")}</p>
        ) : (
          <div className="flex flex-wrap items-center gap-2">
            <ChannelPicker
              channels={channels}
              selected={selectedChannel}
              onSelect={(channel) => {
                setSelectedChannelId(channel.id);
                setSelectedContactId(channel.contacts[0]?.id ?? null);
                setSendError(null);
              }}
            />
            {selectedChannel !== null && !available && (
              <span className="text-sm text-destructive">{t("messaging.addressNone")}</span>
            )}
            {selectedChannel !== null && available && options.length > 1 && (
              <AddressPicker
                options={options}
                selectedId={effectiveContactId}
                onSelect={(contact) => {
                  setSelectedContactId(contact.id);
                  setSendError(null);
                }}
              />
            )}
          </div>
        )}
        {sendError !== null && <p className="text-sm text-destructive">{sendError}</p>}
        <div className="flex items-center gap-2">
          <Input
            ref={inputRef}
            value={draft}
            placeholder={t("messaging.placeholder")}
            aria-label={t("messaging.placeholder")}
            onChange={(event) => {
              setDraft(event.target.value);
              setSendError(null);
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter" && canSend) {
                void send();
              }
            }}
            className="flex-1"
          />
          <Button
            variant="outline"
            size="icon"
            aria-label={t("messaging.send")}
            disabled={!canSend}
            onClick={() => {
              void send();
            }}
          >
            <SendIcon aria-hidden />
          </Button>
        </div>
      </div>
    </div>
  );
}

/** Пузырь сообщения: исходящее справа со статусами доставки, входящее слева. */
function MessageBubble({ message }: { readonly message: MessageSchema }) {
  const { t } = useI18n();
  const outbound = message.type === "outbound";
  return (
    <div className={cn("flex", outbound ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[80%] space-y-1 rounded-xl px-3 py-2",
          outbound ? "bg-primary/10" : "bg-muted",
        )}
      >
        <p className="text-sm break-words whitespace-pre-wrap">{message.body}</p>
        {message.type === "outbound" &&
          message.deliveries.map((delivery, index) => (
            <div key={index} className="space-y-0.5">
              <p className="text-xs text-muted-foreground">
                {deliveryStateLabel(t, delivery.state)}
              </p>
              {delivery.errorMessage !== null && (
                <p className="text-xs text-destructive">{delivery.errorMessage}</p>
              )}
            </div>
          ))}
      </div>
    </div>
  );
}

/** Подпись статуса доставки сообщения. */
function deliveryStateLabel(
  t: I18n["t"],
  state: "PENDING" | "SENT" | "DELIVERED" | "FAILED",
): string {
  switch (state) {
    case "PENDING":
      return t("messaging.deliveryPending");
    case "SENT":
      return t("messaging.deliverySent");
    case "DELIVERED":
      return t("messaging.deliveryDelivered");
    case "FAILED":
      return t("messaging.deliveryFailed");
  }
}

/** Выбор канала отправки: недоступные (нет подходящего контакта или связки) отключены. */
function ChannelPicker({
  channels,
  selected,
  onSelect,
}: {
  readonly channels: readonly ClientChannelSchema[];
  readonly selected: ClientChannelSchema | null;
  readonly onSelect: (channel: ClientChannelSchema) => void;
}) {
  const { t } = useI18n();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm">
          {t("messaging.channel")}: {selected === null ? "—" : selected.name}
          <ChevronDownIcon aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuRadioGroup
          value={selected?.id ?? ""}
          onValueChange={(value) => {
            const channel = channels.find((c) => c.id === value);
            if (channel !== undefined) {
              onSelect(channel);
            }
          }}
        >
          {channels.map((channel) => (
            <DropdownMenuRadioItem
              key={channel.id}
              value={channel.id}
              disabled={!channel.available}
            >
              {channel.name} ({t(`channels.provider.${channel.provider}`)})
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/** Выбор адреса-получателя, когда под канал подходит несколько контактов. */
function AddressPicker({
  options,
  selectedId,
  onSelect,
}: {
  readonly options: readonly ClientContactSchema[];
  readonly selectedId: ClientContactId | null;
  readonly onSelect: (contact: ClientContactSchema) => void;
}) {
  const { t } = useI18n();
  const selected = options.find((contact) => contact.id === selectedId) ?? options[0];
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm">
          {t("messaging.address")}: {selected?.value ?? "—"}
          <ChevronDownIcon aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuRadioGroup
          value={selected?.id ?? ""}
          onValueChange={(value) => {
            const contact = options.find((c) => c.id === value);
            if (contact !== undefined) {
              onSelect(contact);
            }
          }}
        >
          {options.map((contact) => (
            <DropdownMenuRadioItem key={contact.id} value={contact.id}>
              {contact.value}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
