import type { ApiClient } from "@/api/client";
import type { ClientId } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { EditSheet } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { ConversationThread } from "./ConversationThread";

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
      <SheetConversation api={api} clientId={clientId} />
    </EditSheet>
  );
}

/** Переписка внутри панели: черновик сообщается панели как несохранённые изменения. */
function SheetConversation({
  api,
  clientId,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
}) {
  const { reportGuard } = useEditSheet();
  return <ConversationThread api={api} clientId={clientId} onGuardChange={reportGuard} />;
}
