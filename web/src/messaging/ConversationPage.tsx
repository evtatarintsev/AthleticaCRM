import type { ApiClient } from "@/api/client";
import type { ClientId } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { PageHeader } from "@/ui/PageHeader";
import { ConversationThread } from "./ConversationThread";

/** Страница переписки с клиентом [clientId], открываемая из карточки клиента. */
export function ConversationPage({
  api,
  clientId,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  return (
    <section className="flex h-[calc(100vh-8rem)] max-w-2xl flex-col">
      <PageHeader title={t("messaging.title")} />
      <ConversationThread api={api} clientId={clientId} />
    </section>
  );
}
