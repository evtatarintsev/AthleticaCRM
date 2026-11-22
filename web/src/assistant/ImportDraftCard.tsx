import { Link } from "@tanstack/react-router";
import type {
  AssistantBlockImportCompleted,
  AssistantBlockImportDraft,
  AssistantImportRow,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";

/**
 * Пакетная карточка импорта, собранная сервером: счётчики, превью и проблемные строки.
 * Кнопка создания есть только у ожидающей карточки; [stale] — сервер отклонил её как устаревшую.
 */
export function ImportDraftCard({
  block,
  busy,
  stale,
  onConfirm,
}: {
  block: AssistantBlockImportDraft;
  busy: boolean;
  stale: boolean;
  onConfirm: (draftId: AssistantBlockImportDraft["draftId"]) => void;
}) {
  const { t } = useI18n();
  const { counts } = block;
  const summary = [
    [t("assistant.import.ready"), counts.ready],
    [t("assistant.import.exists"), counts.exists],
    [t("assistant.import.duplicateInFile"), counts.duplicateInFile],
    [t("assistant.import.possibleDuplicate"), counts.possibleDuplicate],
    [t("assistant.import.errors"), counts.error],
    [t("assistant.import.warnings"), counts.warnings],
  ] as const;
  const problemsTotal = counts.total - counts.ready + counts.warnings;
  return (
    <article className="min-w-0 space-y-3 rounded-lg border bg-card p-4">
      <h3 className="font-semibold">{t("assistant.import.title")}</h3>
      <dl className="grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 gap-y-1 text-sm">
        {summary
          .filter(([, value], index) => index === 0 || value > 0)
          .map(([label, value]) => (
            <div className="contents" key={label}>
              <dt>{label}</dt>
              <dd className="text-right tabular-nums">{value}</dd>
            </div>
          ))}
      </dl>
      {block.preview.length > 0 && (
        <section className="space-y-1">
          <h4 className="text-sm font-medium">{t("assistant.import.preview")}</h4>
          <ul className="max-h-48 space-y-1 overflow-y-auto text-sm">
            {block.preview.map((row) => (
              <li key={row.row}>
                <RowLine row={row} />
              </li>
            ))}
          </ul>
          {counts.ready > block.preview.length && (
            <p className="text-xs text-muted-foreground">
              {t("assistant.import.shown", { shown: block.preview.length, total: counts.ready })}
            </p>
          )}
        </section>
      )}
      {block.problems.length > 0 && (
        <section className="space-y-1">
          <h4 className="text-sm font-medium">{t("assistant.import.problems")}</h4>
          <ul
            aria-label={t("assistant.import.problems")}
            className="max-h-64 space-y-2 overflow-y-auto text-sm"
          >
            {block.problems.map((row) => (
              <li key={row.row} className="space-y-0.5">
                <RowLine row={row} />
                <RowReasons row={row} />
              </li>
            ))}
          </ul>
          {problemsTotal > block.problems.length && (
            <p className="text-xs text-muted-foreground">
              {t("assistant.import.shown", { shown: block.problems.length, total: problemsTotal })}
            </p>
          )}
        </section>
      )}
      {block.status === "pending" && (
        <>
          <p className="text-xs text-muted-foreground">{t("assistant.import.fixHint")}</p>
          {stale && <FormAlert message={t("assistant.import.stale")} />}
          {counts.ready > 0 && (
            <Button
              disabled={busy}
              onClick={() => {
                onConfirm(block.draftId);
              }}
            >
              {t("assistant.import.create", { count: counts.ready })}
            </Button>
          )}
        </>
      )}
      {block.status === "superseded" && (
        <p className="text-sm text-muted-foreground">{t("assistant.superseded")}</p>
      )}
      {block.status === "executed" && <ImportCompletedLink created={counts.ready} />}
    </article>
  );
}

/** Квитанция пакета: только число созданных по данным сервера и ссылка на список клиентов. */
export function ImportCompleted({ block }: { block: AssistantBlockImportCompleted }) {
  return <ImportCompletedLink created={block.created} />;
}

function ImportCompletedLink({ created }: { created: number }) {
  const { t } = useI18n();
  return (
    <p className="space-x-2 text-sm">
      <span className="font-medium">{t("assistant.import.created", { count: created })}</span>
      <Link to="/clients" className="text-primary underline">
        {t("assistant.import.openClients")}
      </Link>
    </p>
  );
}

/** Позиция строки в файле, имя и данные; неуверенные значения помечены «(?)». */
function RowLine({ row }: { row: AssistantImportRow }) {
  const { t, format } = useI18n();
  const position =
    row.photo !== null
      ? t("assistant.import.position.photo", { photo: row.photo, line: row.line })
      : row.sheet !== null && row.sheet !== ""
        ? t("assistant.import.position.sheet", { sheet: row.sheet, line: row.line })
        : t("assistant.import.position.row", { line: row.line });
  const uncertain = (field: string, value: string) =>
    row.uncertainFields.includes(field) ? (
      <mark className="rounded bg-amber-100 px-0.5 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100">
        {t("assistant.import.uncertain", { value })}
      </mark>
    ) : (
      value
    );
  const client = row.client;
  return (
    <p className="min-w-0">
      <span className="text-muted-foreground">{position}</span>
      {" · "}
      {row.name !== null && uncertain("name", row.name)}
      {client !== null && (
        <>
          {" · "}
          {t(`gender.${client.gender}`)}
          {client.birthday !== null && (
            <> · {uncertain("birthday", format.date(client.birthday))}</>
          )}
          {client.contacts.map((contact) => (
            <span key={`${contact.type}-${contact.value}`}> · {contact.value}</span>
          ))}
        </>
      )}
    </p>
  );
}

/** Причина исключения строки и предупреждения создаваемой строки. */
function RowReasons({ row }: { row: AssistantImportRow }) {
  const { t } = useI18n();
  const reasons: string[] = [];
  switch (row.status) {
    case "error":
      if (row.error !== null) reasons.push(t(`assistant.import.rowError.${row.error}`));
      break;
    case "exists":
    case "duplicate_in_file":
    case "possible_duplicate":
      reasons.push(t(`assistant.import.status.${row.status}`));
      break;
    case "ready":
      break;
  }
  row.warnings.forEach((warning) => {
    reasons.push(t(`assistant.import.warning.${warning}`));
  });
  return (
    <p
      className={row.status === "ready" ? "text-amber-700 dark:text-amber-300" : "text-destructive"}
    >
      {reasons.join(" · ")}
      {row.errorField !== null && row.status === "error" && ` (${row.errorField})`}
      {row.existingClientId !== null && (
        <>
          {" · "}
          <Link
            to="/clients/$clientId"
            params={{ clientId: row.existingClientId }}
            className="text-primary underline"
          >
            {t("assistant.import.openClient")}
          </Link>
        </>
      )}
    </p>
  );
}
