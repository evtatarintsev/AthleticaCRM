import { useQueries, useQueryClient } from "@tanstack/react-query";
import { PlusIcon } from "lucide-react";
import { useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import { ClientDocIdSchema, type ClientDoc, type ClientId } from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { uploadInfoQuery } from "@/query/queries";
import { uuidv7 } from "@/lib/uuid";
import { AttachmentList } from "@/ui/attachments/AttachmentList";
import { ConfirmDialog } from "@/ui/ConfirmDialog";

/**
 * Документы клиента: загрузка файла, миниатюры и просмотр по подписанным ссылкам,
 * удаление с подтверждением.
 */
export function ClientDocumentsSection({
  api,
  clientId,
  docs,
  onChanged,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
  readonly docs: readonly ClientDoc[];
  /** Вызывается после успешной загрузки или удаления документа: карточка клиента перечитывается. */
  readonly onChanged: () => Promise<void>;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [docToDelete, setDocToDelete] = useState<ClientDoc | null>(null);
  const files = useQueries({ queries: docs.map((doc) => uploadInfoQuery(api, doc.uploadId)) });

  const upload = async (file: File) => {
    setUploading(true);
    setError(null);
    const uploaded = await uploadFile(api, file);
    if (!uploaded.ok) {
      setUploading(false);
      setError(apiErrorMessage(t, uploaded.error));
      return;
    }
    queryClient.setQueryData(uploadInfoQuery(api, uploaded.value.id).queryKey, uploaded.value);
    const attached = await api.call("clients/docs/attach", {
      docId: ClientDocIdSchema.parse(uuidv7()),
      clientId,
      uploadId: uploaded.value.id,
      name: file.name,
    });
    setUploading(false);
    if (!attached.ok) {
      setError(apiErrorMessage(t, attached.error));
      return;
    }
    await onChanged();
  };

  const deleteDoc = async (doc: ClientDoc) => {
    const result = await api.call("clients/docs/delete", { clientId, docId: doc.id });
    if (result.ok) {
      await onChanged();
    }
  };

  return (
    <div className="space-y-2 rounded-lg border bg-card p-4">
      <h2 className="text-base font-semibold">{t("clients.detail.documents")}</h2>

      {error !== null && <FormAlert message={error} />}

      {docs.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("clients.detail.noDocuments")}</p>
      ) : (
        <AttachmentList
          items={docs.map((doc, index) => ({
            key: doc.id,
            name: doc.name,
            file: files[index]?.data ?? null,
          }))}
          onRemove={(item) => {
            setDocToDelete(docs.find((doc) => doc.id === item.key) ?? null);
          }}
          removeLabel={(name) => t("clients.detail.deleteDoc", { name })}
        />
      )}

      <input
        ref={input}
        type="file"
        tabIndex={-1}
        aria-hidden
        className="sr-only"
        onChange={(event) => {
          const file = event.target.files?.item(0) ?? null;
          event.target.value = "";
          if (file !== null) {
            void upload(file);
          }
        }}
      />
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={uploading}
        aria-busy={uploading}
        onClick={() => {
          input.current?.click();
        }}
      >
        <PlusIcon aria-hidden />
        {uploading ? t("clients.detail.uploading") : t("clients.detail.uploadDocument")}
      </Button>

      {docToDelete !== null && (
        <ConfirmDialog
          open
          onOpenChange={(open) => {
            if (!open) {
              setDocToDelete(null);
            }
          }}
          title={t("clients.detail.deleteDocTitle")}
          description={t("clients.detail.deleteDocMessage", { name: docToDelete.name })}
          confirmLabel={t("action.delete")}
          onConfirm={() => deleteDoc(docToDelete)}
        />
      )}
    </div>
  );
}
