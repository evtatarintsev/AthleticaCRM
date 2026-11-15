import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { SparklesIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  AssistantBlock,
  AssistantBlockClientDraft,
  EmployeeId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorOf } from "@/query/apiFailure";
import { EditSheet } from "@/ui/EditSheet";
import {
  assistantCodeMessage,
  assistantDialogQuery,
  assistantErrorMessage,
  assistantStatusQuery,
} from "./assistantQueries";

/** Состояние живёт над размонтируемым EditSheet и переживает закрытие и переходы страниц. */
export function AssistantHost({ api, employeeId }: { api: ApiClient; employeeId: EmployeeId }) {
  const { t } = useI18n();
  const cache = useQueryClient();
  const status = useQuery(assistantStatusQuery(api, employeeId));
  const allowed = status.data?.available === true && status.data.enabled && status.data.permitted;
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const inFlight = useRef(false);
  const active = useRef(true);
  const dialogQuery = assistantDialogQuery(api, employeeId);
  const dialog = useQuery({
    ...dialogQuery,
    enabled: allowed && open,
    refetchInterval: (query) =>
      query.state.data?.messages.some((m) => m.status === "processing") === true ? 1000 : false,
  });
  if (!allowed && open) setOpen(false);
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
  const busy = sending || processing;

  /** Квитанция обновляет все списки клиентов, в том числе в других филиалах. */
  async function refreshClients() {
    await cache.invalidateQueries({
      predicate: (query) => query.queryKey.includes("clients/list"),
    });
  }

  /** Прямой запрос без retry: при потере ответа перечитывается только диалог. */
  async function send() {
    const current = dialog.data;
    if (current === undefined || inFlight.current || processing || text.trim() === "") return;
    inFlight.current = true;
    setSending(true);
    setFailure(null);
    try {
      const result = await api.call("assistant/messages/send", {
        dialogId: current.id,
        text: text.trim(),
      });
      if (!active.current) return;
      if (result.ok) {
        cache.setQueryData(dialogQuery.queryKey, result.value);
        setText("");
        if (result.value.messages.some((m) => m.blocks.some((b) => b.type === "client_created")))
          await refreshClients();
      } else {
        setFailure(assistantErrorMessage(t, result.error));
        await dialog.refetch();
      }
      await status.refetch();
    } finally {
      inFlight.current = false;
      setSending(false);
    }
  }

  /** Подтверждение блокируется синхронно, включая два клика в одном кадре. */
  async function confirm(clientId: AssistantBlockClientDraft["clientId"]) {
    if (inFlight.current || processing) return;
    inFlight.current = true;
    setSending(true);
    setFailure(null);
    try {
      const result = await api.call("assistant/drafts/confirm", { clientId });
      if (!active.current) return;
      if (result.ok) await refreshClients();
      else setFailure(assistantErrorMessage(t, result.error));
      await dialog.refetch();
      await status.refetch();
    } finally {
      inFlight.current = false;
      setSending(false);
    }
  }

  /** Новый диалог начинается только по явному действию сотрудника. */
  async function create() {
    if (inFlight.current || processing) return;
    inFlight.current = true;
    setSending(true);
    setFailure(null);
    try {
      const result = await api.call("assistant/dialogs/create");
      if (!active.current) return;
      if (result.ok) {
        cache.setQueryData(dialogQuery.queryKey, result.value);
        setText("");
      } else setFailure(assistantErrorMessage(t, result.error));
      await status.refetch();
    } finally {
      inFlight.current = false;
      setSending(false);
    }
  }

  return (
    <>
      {allowed && (
        <Button
          variant="ghost"
          size="icon"
          aria-label={t("assistant.title")}
          onClick={() => {
            setOpen(true);
          }}
        >
          <SparklesIcon aria-hidden />
        </Button>
      )}
      <EditSheet
        open={open && allowed}
        onOpenChange={setOpen}
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
                <p className="whitespace-pre-wrap">{message.text}</p>
                {message.blocks.map((block, index) => (
                  <ResponseBlock
                    key={index}
                    block={block}
                    busy={busy}
                    onConfirm={(id) => {
                      void confirm(id);
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
                {t("assistant.waiting")}
              </p>
            )}
          </div>
          <form
            className="space-y-3 border-t p-4"
            onSubmit={(event) => {
              event.preventDefault();
              void send();
            }}
          >
            {failure !== null && <FormAlert message={failure} />}
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
            <Button
              type="submit"
              disabled={busy || text.trim() === "" || dialog.data === undefined}
            >
              {t("assistant.send")}
            </Button>
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
  onConfirm,
}: {
  block: AssistantBlock;
  busy: boolean;
  onConfirm: (id: AssistantBlockClientDraft["clientId"]) => void;
}) {
  const { t, format } = useI18n();
  switch (block.type) {
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
                <dd>{contact.value}</dd>
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
