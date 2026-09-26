import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PlusIcon, TrashIcon } from "lucide-react";
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
import { ConfirmDialog } from "@/ui/ConfirmDialog";

/** Строка документа: ссылка на подписанный файл и удаление. */
function DocRow({
  api,
  doc,
  onAskDelete,
}: {
  readonly api: ApiClient;
  readonly doc: ClientDoc;
  readonly onAskDelete: (doc: ClientDoc) => void;
}) {
  const { t } = useI18n();
  const info = useQuery(uploadInfoQuery(api, doc.uploadId));
  return (
    <li className="flex items-center gap-2 py-2 text-sm">
      {info.data === undefined ? (
        <span className="flex-1 truncate">{doc.name}</span>
      ) : (
        <a
          href={info.data.url}
          target="_blank"
          rel="noreferrer"
          className="flex-1 truncate text-primary underline"
        >
          {doc.name}
        </a>
      )}
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        aria-label={t("clients.detail.deleteDoc", { name: doc.name })}
        onClick={() => {
          onAskDelete(doc);
        }}
      >
        <TrashIcon aria-hidden className="text-destructive" />
      </Button>
    </li>
  );
}

/** Документы клиента: загрузка файла, список с подписанными ссылками, удаление с подтверждением. */
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
        <ul className="divide-y">
          {docs.map((doc) => (
            <DocRow
              key={doc.id}
              api={api}
              doc={doc}
              onAskDelete={(d) => {
                setDocToDelete(d);
              }}
            />
          ))}
        </ul>
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
