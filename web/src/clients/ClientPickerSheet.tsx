import { keepPreviousData, useInfiniteQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, ClientId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { unwrap } from "@/query/apiFailure";
import { EditSheet, EditSheetBody, EditSheetSelection } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { CLIENTS_PAGE_SIZE } from "./clientsQueries";

/** Задержка между вводом в поиск и запросом списка. */
const SEARCH_DEBOUNCE_MS = 400;

/** Клиент, выбранный в [ClientPickerSheet]. */
export interface PickedClient {
  /** Идентификатор клиента. */
  readonly id: ClientId;
  /** Имя клиента. */
  readonly name: string;
}

/**
 * Режим выбора: `single` — нажатие на клиента сразу подтверждает выбор,
 * `multiple` — клиенты отмечаются и подтверждаются кнопкой.
 */
export type ClientPickerMode = "single" | "multiple";

/** Свойства панели выбора клиента. */
interface ClientPickerSheetProps {
  /** Клиент API. */
  readonly api: ApiClient;
  /** Текущий филиал — часть ключа кэша. */
  readonly branchId: BranchId;
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Заголовок панели. */
  readonly title: string;
  /** Режим выбора. */
  readonly mode: ClientPickerMode;
  /** Клиенты, которых выбрать нельзя, с причиной для каждого. */
  readonly unavailable: ReadonlyMap<ClientId, string>;
  /** Подпись кнопки подтверждения в режиме `multiple`. */
  readonly submitLabel?: string;
  /**
   * Обработка выбора: `null` — успех, панель закрывается; строка — текст ошибки,
   * панель остаётся открытой с прежним выбором.
   */
  readonly onSubmit: (clients: readonly PickedClient[]) => Promise<string | null>;
}

/**
 * Панель справа для выбора клиентов организации: поиск по имени, список порциями и
 * одиночный или множественный выбор. Что значит выбор и кого выбрать нельзя, решает
 * открывший её экран — панель только собирает выбор и передаёт его в `onSubmit`.
 */
export function ClientPickerSheet({ open, onOpenChange, title, ...rest }: ClientPickerSheetProps) {
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={title}>
      <ClientPickerContent {...rest} />
    </EditSheet>
  );
}

/** Содержимое панели выбора; монтируется заново при каждом открытии. */
function ClientPickerContent({
  api,
  branchId,
  mode,
  unavailable,
  submitLabel,
  onSubmit,
}: Omit<ClientPickerSheetProps, "open" | "onOpenChange" | "title">) {
  const { t } = useI18n();
  const { close, reportGuard } = useEditSheet();
  const [query, setQuery] = useState("");
  const [name, setName] = useState<string | null>(null);
  const [selected, setSelected] = useState<ReadonlyMap<ClientId, PickedClient>>(new Map());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const next = query.trim() === "" ? null : query.trim();
    if (next === name) {
      return;
    }
    const timer = setTimeout(() => {
      setName(next);
    }, SEARCH_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [query, name]);

  useEffect(() => {
    reportGuard({ dirty: selected.size > 0, submitting });
  }, [reportGuard, selected, submitting]);

  const pages = useInfiniteQuery({
    queryKey: ["api", branchId, "clients/list", { picker: name }] as const,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      unwrap(await api.call("clients/list", { name, limit: CLIENTS_PAGE_SIZE, offset: pageParam })),
    getNextPageParam: (last, all) => {
      const loaded = all.reduce((count, page) => count + page.clients.length, 0);
      return loaded < last.total ? loaded : undefined;
    },
    placeholderData: keepPreviousData,
  });
  const clients = pages.data?.pages.flatMap((page) => page.clients) ?? [];

  const submit = async (picked: readonly PickedClient[]) => {
    setSubmitting(true);
    setError(null);
    const failure = await onSubmit(picked);
    if (failure === null) {
      close();
      return;
    }
    setSubmitting(false);
    setError(failure);
  };

  const toggle = (client: PickedClient) => {
    const next = new Map(selected);
    if (next.has(client.id)) {
      next.delete(client.id);
    } else {
      next.set(client.id, client);
    }
    setSelected(next);
  };

  return (
    <>
      <EditSheetBody>
        <Input
          type="search"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
          }}
          placeholder={t("clientPicker.search")}
          aria-label={t("clientPicker.search")}
        />
        {error !== null && <FormAlert message={error} />}
        {pages.isError && <FormAlert message={t("clientPicker.loadError")} />}
        {pages.data !== undefined &&
          (clients.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">
              {t("clientPicker.empty")}
            </p>
          ) : (
            <ul className="divide-y">
              {clients.map((client) => {
                const reason = unavailable.get(client.id);
                const picked = { id: client.id, name: client.name };
                return (
                  <li key={client.id}>
                    {mode === "multiple" ? (
                      <label className="flex cursor-pointer items-center gap-3 px-1 py-3 text-sm hover:bg-accent has-disabled:cursor-default has-disabled:opacity-50">
                        <input
                          type="checkbox"
                          checked={selected.has(client.id)}
                          disabled={reason !== undefined || submitting}
                          onChange={() => {
                            toggle(picked);
                          }}
                          className="size-4 shrink-0 accent-primary"
                        />
                        <ClientRowText name={client.name} reason={reason} />
                      </label>
                    ) : (
                      <button
                        type="button"
                        disabled={reason !== undefined || submitting}
                        onClick={() => {
                          void submit([picked]);
                        }}
                        className="flex w-full items-center gap-3 px-1 py-3 text-left text-sm hover:bg-accent disabled:opacity-50"
                      >
                        <ClientRowText name={client.name} reason={reason} />
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          ))}
        {pages.hasNextPage && (
          <div className="flex justify-center">
            <Button
              type="button"
              variant="outline"
              disabled={pages.isFetchingNextPage}
              onClick={() => {
                void pages.fetchNextPage();
              }}
            >
              {t("clients.loadMore")}
            </Button>
          </div>
        )}
      </EditSheetBody>
      {mode === "multiple" && (
        <EditSheetSelection
          count={selected.size}
          actions={
            <Button
              type="button"
              disabled={submitting}
              aria-busy={submitting}
              onClick={() => {
                void submit(Array.from(selected.values()));
              }}
            >
              {submitLabel ?? t("action.add")}
            </Button>
          }
        />
      )}
    </>
  );
}

/** Имя клиента [name] и причина [reason], по которой его нельзя выбрать. */
function ClientRowText({ name, reason }: { name: string; reason: string | undefined }) {
  return (
    <>
      <span className="min-w-0 flex-1 truncate">{name}</span>
      {reason !== undefined && (
        <span className="shrink-0 text-xs text-muted-foreground">{reason}</span>
      )}
    </>
  );
}
