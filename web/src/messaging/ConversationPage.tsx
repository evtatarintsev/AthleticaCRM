import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDownIcon, SendIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  ChannelIntegrationId,
  ChannelIntegrationSchema,
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
import { PageHeader } from "@/ui/PageHeader";
import { isContactCompatible } from "./compatibleChannels";

/** Контакты клиента, подходящие для канала [channel]. */
function addressOptions(
  channel: ChannelIntegrationSchema,
  contacts: readonly ClientContactSchema[],
): readonly ClientContactSchema[] {
  return contacts.filter((contact) => isContactCompatible(contact.type, channel.channelType));
}

/** Канал доступен для отправки, если это IN_APP или у клиента есть подходящий контакт. */
function isAvailable(
  channel: ChannelIntegrationSchema,
  contacts: readonly ClientContactSchema[],
): boolean {
  return channel.channelType === "IN_APP" || addressOptions(channel, contacts).length > 0;
}

/** Канал, выбранный по умолчанию, пока пользователь не выбрал свой: первый доступный. */
function defaultChannel(
  channels: readonly ChannelIntegrationSchema[],
  contacts: readonly ClientContactSchema[],
): ChannelIntegrationSchema | null {
  return channels.find((channel) => isAvailable(channel, contacts)) ?? channels[0] ?? null;
}

/**
 * Переписка с клиентом (паритет с `ConversationScreen` KMP-клиента): лента сообщений,
 * выбор канала и адреса-получателя, отправка. Контакты редактируются на карточке клиента —
 * здесь они только определяют доступность каналов.
 */
export function ConversationPage({
  api,
  clientId,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const conversationQuery = apiQuery(api, branchId, "messaging/conversation", { clientId });
  const conversation = useQuery(conversationQuery);
  const channelsQuery = apiQuery(api, branchId, "channels/list");
  const channelsResult = useQuery(channelsQuery);
  const channels = channelsResult.data?.channels.filter((channel) => channel.enabled) ?? [];

  const [selectedChannelId, setSelectedChannelId] = useState<ChannelIntegrationId | null>(null);
  const [selectedContactId, setSelectedContactId] = useState<ClientContactId | null>(null);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  const contacts = conversation.data?.contacts ?? [];

  const selectedChannel =
    (selectedChannelId === null
      ? defaultChannel(channels, contacts)
      : channels.find((channel) => channel.id === selectedChannelId)) ?? null;
  const options = selectedChannel === null ? [] : addressOptions(selectedChannel, contacts);
  const effectiveContactId = selectedContactId ?? options[0]?.id ?? null;
  const available = selectedChannel !== null && isAvailable(selectedChannel, contacts);
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
  return (
    <section className="flex h-[calc(100vh-8rem)] max-w-2xl flex-col">
      <PageHeader title={t("messaging.title")} />
      {failed && <FormAlert message={t("messaging.loadError")} />}
      {!failed && (conversation.isPending || channelsResult.isPending) && (
        <div className="space-y-2">
          <Skeleton className="h-16 w-2/3" />
          <Skeleton className="ml-auto h-16 w-2/3" />
        </div>
      )}
      {!failed && conversation.data !== undefined && (
        <>
          <div className="min-h-0 flex-1 space-y-2 overflow-y-auto py-2">
            {conversation.data.messages.length === 0 ? (
              <p className="py-12 text-center text-muted-foreground">
                {t("messaging.emptyThread")}
              </p>
            ) : (
              conversation.data.messages.map((message) => (
                <MessageBubble key={message.id} message={message} />
              ))
            )}
          </div>
          <div className="space-y-2 border-t pt-3">
            {channels.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("messaging.addressNone")}</p>
            ) : (
              <div className="flex flex-wrap items-center gap-2">
                <ChannelPicker
                  channels={channels}
                  contacts={contacts}
                  selected={selectedChannel}
                  onSelect={(channel) => {
                    setSelectedChannelId(channel.id);
                    setSelectedContactId(addressOptions(channel, contacts)[0]?.id ?? null);
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
                value={draft}
                placeholder={t("messaging.placeholder")}
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
        </>
      )}
    </section>
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

/** Выбор канала отправки: недоступные (без подходящего контакта, кроме IN_APP) отключены. */
function ChannelPicker({
  channels,
  contacts,
  selected,
  onSelect,
}: {
  readonly channels: readonly ChannelIntegrationSchema[];
  readonly contacts: readonly ClientContactSchema[];
  readonly selected: ChannelIntegrationSchema | null;
  readonly onSelect: (channel: ChannelIntegrationSchema) => void;
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
              disabled={!isAvailable(channel, contacts)}
            >
              {channel.name} ({t(`channels.provider.${channel.config.type}`)})
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
