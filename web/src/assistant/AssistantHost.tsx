import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PaperclipIcon, SparklesIcon, XIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  AssistantBlock,
  AssistantBlockClientDraft,
  AssistantBlockImportDraft,
  EmployeeId,
  UploadId,
} from "@/api/generated/contracts";
import { uploadFile } from "@/api/upload";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorOf } from "@/query/apiFailure";
import { EditSheet } from "@/ui/EditSheet";
import {
  assistantAllowed,
  assistantCodeMessage,
  assistantDialogQuery,
  assistantErrorMessage,
  assistantStatusQuery,
} from "./assistantQueries";
import { ATTACHMENT_ACCEPT, attachmentProblem } from "./attachments";
import { ImportCompleted, ImportDraftCard } from "./ImportDraftCard";

/**
 * Ход сотрудника в диалоге: ничего не отправляется, запрос в работе (с файлами — показывается
 * «обрабатываю файл») или запрос отклонён с сообщением [message]. Отказ снимается следующим
 * запросом или новыми вложениями.
 */
type Turn =
  | { readonly kind: "idle" }
  | { readonly kind: "sending"; readonly withFiles: boolean }
  | { readonly kind: "failed"; readonly message: string };

/** Ход без запроса и без отказа. */
const IDLE: Turn = { kind: "idle" };

/**
 * Панель помощника и кнопка её открытия на широком экране. Состояние живёт над размонтируемым
 * EditSheet и переживает закрытие и переходы страниц. Открытием управляет раскладка
 * ([open], [onOpenChange]): на узком экране панель открывается из меню аккаунта.
 */
export function AssistantHost({
  api,
  employeeId,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  employeeId: EmployeeId;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  const cache = useQueryClient();
  const status = useQuery(assistantStatusQuery(api, employeeId));
  const allowed = assistantAllowed(status.data);
  const [text, setText] = useState("");
  const [turn, setTurn] = useState<Turn>(IDLE);
  const [files, setFiles] = useState<readonly File[]>([]);
  const [stale, setStale] = useState<AssistantBlockImportDraft["draftId"] | null>(null);
  const picker = useRef<HTMLInputElement>(null);
  /** Уже загруженные файлы: повторная отправка после отказа не плодит копии с ПД в хранилище. */
  const uploadedIds = useRef(new WeakMap<File, UploadId>());
  const inFlight = useRef(false);
  const active = useRef(true);
  const dialogQuery = assistantDialogQuery(api, employeeId);
  const dialog = useQuery({
    ...dialogQuery,
    enabled: allowed && open,
    refetchInterval: (query) =>
      query.state.data?.messages.some((m) => m.status === "processing") === true ? 1000 : false,
  });
  useEffect(() => {
    if (!allowed && open) onOpenChange(false);
  }, [allowed, open, onOpenChange]);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      cache.removeQueries({ queryKey: ["assistant", employeeId] });
    };
  }, [cache, employeeId]);
  const refreshStatus = status.refetch;
  useEffect(() => {
    if (dialog.error === null) return;
    const error = apiErrorOf(dialog.error);
    if (
      error.kind === "business" &&
      ["ASSISTANT_DISABLED", "ASSISTANT_UNAVAILABLE", "ASSISTANT_FORBIDDEN"].includes(error.code)
    ) {
      void refreshStatus();
    }
  }, [dialog.error, refreshStatus]);
  const processing = dialog.data?.messages.some((m) => m.status === "processing") === true;
  const sending = turn.kind === "sending";
  const withFiles = turn.kind === "sending" && turn.withFiles;
  const failure = turn.kind === "failed" ? turn.message : null;
  const busy = sending || processing;
  /** Показывает отказ [message]; по завершении запроса он остаётся на экране. */
  const fail = (message: string) => {
    setTurn({ kind: "failed", message });
  };
  /** Снимает завершённый запрос: отказ остаётся, состояние отправки сбрасывается. */
  const settle = () => {
    setTurn((current) => (current.kind === "sending" ? IDLE : current));
  };
  const problem = attachmentProblem(files);
  const canSend = text.trim() !== "" || files.length > 0;

  /** Добавляет выбранные или перетащенные файлы; проверка набора показывается до отправки. */
  function attach(list: FileList | null) {
    if (list === null || list.length === 0) return;
    setFiles((current) => [...current, ...Array.from(list)]);
    setTurn((current) => (current.kind === "failed" ? IDLE : current));
  }

  /** Квитанция обновляет все списки клиентов, в том числе в других филиалах. */
  async function refreshClients() {
    await cache.invalidateQueries({
      predicate: (query) => query.queryKey.includes("clients/list"),
    });
  }

  /**
   * Прямой запрос без retry: при потере ответа перечитывается только диалог, файлы повторно
   * не отправляются. Вложения сначала загружаются через `/upload` и остаются в поле при отказе;
   * принятый ход (диалог вырос) очищает их. Без ответа сервер мог принять ход и удалить загрузки,
   * поэтому повтор после такого сбоя загружает файлы заново.
   */
  async function send() {
    const current = dialog.data;
    if (current === undefined || inFlight.current || processing || !canSend || problem !== null)
      return;
    inFlight.current = true;
    setTurn({ kind: "sending", withFiles: files.length > 0 });
    try {
      const attachments: UploadId[] = [];
      for (const file of files) {
        const known = uploadedIds.current.get(file);
        if (known !== undefined) {
          attachments.push(known);
          continue;
        }
        const uploaded = await uploadFile(api, file);
        if (!active.current) return;
        if (!uploaded.ok) {
          fail(assistantErrorMessage(t, uploaded.error));
          return;
        }
        uploadedIds.current.set(file, uploaded.value.id);
        attachments.push(uploaded.value.id);
      }
      const result = await api.call("assistant/messages/send", {
        dialogId: current.id,
        text: text.trim(),
        attachments,
      });
      if (!active.current) return;
      if (result.ok) {
        cache.setQueryData(dialogQuery.queryKey, result.value);
        setText("");
        setFiles([]);
        if (
          result.value.messages.some((m) =>
            m.blocks.some((b) => b.type === "client_created" || b.type === "import_completed"),
          )
        )
          await refreshClients();
      } else {
        fail(assistantErrorMessage(t, result.error));
        if (result.error.kind !== "business") uploadedIds.current = new WeakMap();
        const refreshed = await dialog.refetch();
        if ((refreshed.data?.messages.length ?? 0) > current.messages.length) setFiles([]);
      }
      await status.refetch();
    } finally {
      inFlight.current = false;
      settle();
    }
  }

  /** Пакетное подтверждение кнопкой: без модели; устаревшая карточка показывает причину. */
  async function confirmImport(draftId: AssistantBlockImportDraft["draftId"]) {
    if (inFlight.current || processing) return;
    inFlight.current = true;
    setTurn({ kind: "sending", withFiles: false });
    setStale(null);
    try {
      const result = await api.call("assistant/imports/confirm", { draftId });
      if (!active.current) return;
      if (result.ok) await refreshClients();
      else if (
        result.error.kind === "business" &&
        result.error.code === "ASSISTANT_IMPORT_DRAFT_STALE"
      )
        setStale(draftId);
      else fail(assistantErrorMessage(t, result.error));
      await dialog.refetch();
      await status.refetch();
    } finally {
      inFlight.current = false;
      settle();
    }
  }

  /** Подтверждение блокируется синхронно, включая два клика в одном кадре. */
  async function confirm(clientId: AssistantBlockClientDraft["clientId"]) {
    if (inFlight.current || processing) return;
    inFlight.current = true;
    setTurn({ kind: "sending", withFiles: false });
    try {
      const result = await api.call("assistant/drafts/confirm", { clientId });
      if (!active.current) return;
      if (result.ok) await refreshClients();
      else fail(assistantErrorMessage(t, result.error));
      await dialog.refetch();
      await status.refetch();
    } finally {
      inFlight.current = false;
      settle();
    }
  }

  /** Новый диалог начинается только по явному действию сотрудника. */
  async function create() {
    if (inFlight.current || processing) return;
    inFlight.current = true;
    setTurn({ kind: "sending", withFiles: false });
    try {
      const result = await api.call("assistant/dialogs/create");
      if (!active.current) return;
      if (result.ok) {
        cache.setQueryData(dialogQuery.queryKey, result.value);
        setText("");
        setFiles([]);
      } else fail(assistantErrorMessage(t, result.error));
      await status.refetch();
    } finally {
      inFlight.current = false;
      settle();
    }
  }

  return (
    <>
      {allowed && (
        <Button
          variant="ghost"
          size="icon"
          className="hidden lg:inline-flex"
          aria-label={t("assistant.title")}
          onClick={() => {
            onOpenChange(true);
          }}
        >
          <SparklesIcon aria-hidden />
        </Button>
      )}
      <EditSheet
        open={open && allowed}
        onOpenChange={onOpenChange}
        title={t("assistant.title")}
        size="lg"
      >
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          <div className="border-b p-3">
            <Button
              variant="outline"
              disabled={busy}
              onClick={() => {
                void create();
              }}
            >
              {t("assistant.newDialog")}
            </Button>
          </div>
          <div
            role="log"
            aria-label={t("assistant.messages")}
            className="min-h-0 flex-1 space-y-4 overflow-y-auto p-4 [overflow-wrap:anywhere]"
          >
            {dialog.data?.messages.length === 0 && (
              <p className="text-sm text-muted-foreground">{t("assistant.empty")}</p>
            )}
            {dialog.isPending && <p role="status">{t("assistant.loading")}</p>}
            {dialog.isError && (
              <FormAlert message={assistantErrorMessage(t, apiErrorOf(dialog.error))} />
            )}
            {dialog.data?.messages.map((message) => (
              <div
                key={message.id}
                className={message.role === "user" ? "ml-6 rounded-lg bg-muted p-3" : "space-y-3"}
              >
                {message.text !== "" && <p className="whitespace-pre-wrap">{message.text}</p>}
                {message.attachments.length > 0 && (
                  <ul aria-label={t("assistant.attachments")} className="space-y-1 text-sm">
                    {message.attachments.map((name, index) => (
                      <li key={index} className="flex min-w-0 items-center gap-1">
                        <PaperclipIcon aria-hidden className="size-4 shrink-0" />
                        <span className="min-w-0">{name}</span>
                      </li>
                    ))}
                  </ul>
                )}
                {message.blocks.map((block, index) => (
                  <ResponseBlock
                    key={index}
                    block={block}
                    busy={busy}
                    stale={stale}
                    onConfirm={(id) => {
                      void confirm(id);
                    }}
                    onConfirmImport={(id) => {
                      void confirmImport(id);
                    }}
                  />
                ))}
                {message.errorCode !== null && (
                  <FormAlert message={assistantCodeMessage(t, message.errorCode)} />
                )}
              </div>
            ))}
            {busy && (
              <p role="status" aria-live="polite">
                {t(withFiles ? "assistant.processingFile" : "assistant.waiting")}
              </p>
            )}
          </div>
          <form
            className="space-y-3 border-t p-4"
            onSubmit={(event) => {
              event.preventDefault();
              void send();
            }}
            onDragOver={(event) => {
              event.preventDefault();
            }}
            onDrop={(event) => {
              event.preventDefault();
              if (!busy) attach(event.dataTransfer.files);
            }}
          >
            {failure !== null && <FormAlert message={failure} />}
            {problem !== null && <FormAlert message={t(`assistant.attachError.${problem}`)} />}
            {files.length > 0 && (
              <ul aria-label={t("assistant.attachments")} className="space-y-1 text-sm">
                {files.map((file, index) => (
                  <li key={index} className="flex min-w-0 items-center gap-1">
                    <PaperclipIcon aria-hidden className="size-4 shrink-0" />
                    <span className="min-w-0 flex-1">{file.name}</span>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={busy}
                      aria-label={t("assistant.removeAttachment", { name: file.name })}
                      onClick={() => {
                        setFiles((current) => current.filter((_, i) => i !== index));
                      }}
                    >
                      <XIcon aria-hidden />
                    </Button>
                  </li>
                ))}
              </ul>
            )}
            <textarea
              className="w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50"
              aria-label={t("assistant.input")}
              value={text}
              maxLength={4000}
              disabled={busy}
              onChange={(event) => {
                setText(event.target.value);
              }}
              rows={3}
            />
            <input
              ref={picker}
              type="file"
              multiple
              hidden
              accept={ATTACHMENT_ACCEPT}
              aria-label={t("assistant.attach")}
              onChange={(event) => {
                attach(event.target.files);
                event.target.value = "";
              }}
            />
            <div className="flex flex-wrap gap-2">
              <Button
                type="submit"
                disabled={busy || !canSend || problem !== null || dialog.data === undefined}
              >
                {t("assistant.send")}
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => {
                  picker.current?.click();
                }}
              >
                <PaperclipIcon aria-hidden />
                {t("assistant.attach")}
              </Button>
            </div>
          </form>
        </div>
      </EditSheet>
    </>
  );
}

/** Только серверные блоки управляют карточкой и успехом, модельный текст остаётся текстом. */
function ResponseBlock({
  block,
  busy,
  stale,
  onConfirm,
  onConfirmImport,
}: {
  block: AssistantBlock;
  busy: boolean;
  stale: AssistantBlockImportDraft["draftId"] | null;
  onConfirm: (id: AssistantBlockClientDraft["clientId"]) => void;
  onConfirmImport: (id: AssistantBlockImportDraft["draftId"]) => void;
}) {
  const { t, format } = useI18n();
  switch (block.type) {
    case "import_draft":
      return (
        <ImportDraftCard
          block={block}
          busy={busy}
          stale={stale === block.draftId}
          onConfirm={onConfirmImport}
        />
      );
    case "import_completed":
      return <ImportCompleted block={block} />;
    case "client_created":
      return (
        <Link
          to="/clients/$clientId"
          params={{ clientId: block.clientId }}
          className="text-primary underline"
        >
          {t("assistant.created")}
        </Link>
      );
    case "client_draft":
      return (
        <article className="space-y-3 rounded-lg border bg-card p-4">
          <h3 className="font-semibold">{t("assistant.confirmTitle")}</h3>
          <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
            <dt>{t("assistant.name")}</dt>
            <dd>{block.payload.name}</dd>
            <dt>{t("clients.gender")}</dt>
            <dd>{t(`gender.${block.payload.gender}`)}</dd>
            <dt>{t("clients.birthday")}</dt>
            <dd>
              {block.payload.birthday === null
                ? t("assistant.notSet")
                : format.date(block.payload.birthday)}
            </dd>
            {block.payload.contacts.map((contact, index) => (
              <div className="contents" key={index}>
                <dt>{t(`contactType.${contact.type}`)}</dt>
                <dd>
                  {contact.value}
                  {contact.label !== null && ` · ${contact.label}`}
                </dd>
              </div>
            ))}
            {block.payload.contacts.length === 0 && (
              <>
                <dt>{t("assistant.contacts")}</dt>
                <dd>{t("assistant.notSet")}</dd>
              </>
            )}
            {block.payload.customFields.map((field) => (
              <div className="contents" key={field.fieldKey}>
                <dt>{block.payload.customFieldLabels[field.fieldKey] ?? field.fieldKey}</dt>
                <dd>{String(field.value)}</dd>
              </div>
            ))}
          </dl>
          {block.status === "pending" && (
            <Button
              disabled={busy}
              onClick={() => {
                onConfirm(block.clientId);
              }}
            >
              {t("assistant.confirm")}
            </Button>
          )}
          {block.status === "superseded" && (
            <p className="text-sm text-muted-foreground">{t("assistant.superseded")}</p>
          )}
          {block.status === "executed" && (
            <Link
              to="/clients/$clientId"
              params={{ clientId: block.clientId }}
              className="text-primary underline"
            >
              {t("assistant.created")}
            </Link>
          )}
        </article>
      );
  }
}
