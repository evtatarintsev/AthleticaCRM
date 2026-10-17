import { z } from "zod";
import {
  ChannelIntegrationIdSchema,
  ClientIdSchema,
  type ChannelIntegrationId,
  type ClientId,
  type InboxFilterSchema,
} from "@/api/generated/contracts";

/** Search-параметры страницы «Входящие»: фильтр и выбранная беседа или чат. */
export const InboxSearchSchema = z.object({
  filter: z.enum(["unread", "unresolved"]).optional().catch(undefined),
  client: ClientIdSchema.optional().catch(undefined),
  chat: z.string().optional().catch(undefined),
});

/** Search-параметры адреса «Входящих». */
export type InboxSearch = z.output<typeof InboxSearchSchema>;

/** Чат связанного канала: интеграция и адрес в ней. */
export interface ChatKey {
  readonly integrationId: ChannelIntegrationId;
  readonly address: string;
}

/** Выбранная запись «Входящих». */
export type InboxSelection =
  | { readonly kind: "client"; readonly clientId: ClientId }
  | { readonly kind: "chat"; readonly chat: ChatKey }
  | { readonly kind: "none" };

/** Фильтр запроса списка из search-параметров [search]. */
export function inboxFilter(search: InboxSearch): InboxFilterSchema {
  switch (search.filter) {
    case "unread":
      return "UNREAD";
    case "unresolved":
      return "UNRESOLVED";
    case undefined:
      return "ALL";
  }
}

/** Значение search-параметра `chat` для чата [chat]: `интеграция:адрес`. */
export function chatParam(chat: ChatKey): string {
  return `${chat.integrationId}:${chat.address}`;
}

/** Выбранная запись из search-параметров [search]; нераспознанный чат — ничего не выбрано. */
export function inboxSelection(search: InboxSearch): InboxSelection {
  if (search.client !== undefined) {
    return { kind: "client", clientId: search.client };
  }
  if (search.chat === undefined) {
    return { kind: "none" };
  }
  const separator = search.chat.indexOf(":");
  const integrationId = ChannelIntegrationIdSchema.safeParse(search.chat.slice(0, separator));
  if (separator < 0 || !integrationId.success) {
    return { kind: "none" };
  }
  return {
    kind: "chat",
    chat: { integrationId: integrationId.data, address: search.chat.slice(separator + 1) },
  };
}
