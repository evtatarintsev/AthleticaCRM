import { CheckIcon } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { FormAlert } from "@/forms/FormAlert";
import type { ChecklistItem } from "./ChecklistSheet";
import { EditSheet, EditSheetBody } from "./EditSheet";
import { useEditSheet } from "./editSheetContext";

/** Свойства панели одиночного выбора. */
interface ChoiceSheetProps<Id extends string> {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Заголовок панели. */
  readonly title: string;
  /** Записи, из которых выбирают. */
  readonly items: readonly ChecklistItem<Id>[];
  /**
   * Текущий выбор: отмечается галочкой; `null` — отмечен пункт [noneLabel],
   * `undefined` — не отмечено ничего (например, при выборе для нескольких записей сразу).
   */
  readonly current?: Id | null;
  /** Подпись первого пункта «ничего не выбрано»; без неё пункта нет. */
  readonly noneLabel?: string;
  /** Сообщение при пустом списке [items]. */
  readonly emptyText: string;
  /**
   * Обработка выбора записи или `null` для пункта [noneLabel]: `null` в ответе — успех,
   * панель закрывается; строка — текст ошибки, панель остаётся открытой.
   */
  readonly onChoose: (id: Id | null) => Promise<string | null>;
}

/**
 * Панель справа для выбора одной записи из небольшого списка: нажатие на запись сразу
 * подтверждает выбор, без отметок и кнопки «Сохранить». Черновика нет, поэтому
 * закрытие не требует подтверждения; пока выбор обрабатывается, панель не закрывается.
 */
export function ChoiceSheet<Id extends string>({
  open,
  onOpenChange,
  title,
  ...rest
}: ChoiceSheetProps<Id>) {
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={title}>
      <ChoiceContent {...rest} />
    </EditSheet>
  );
}

/** Содержимое панели; монтируется заново при каждом открытии. */
function ChoiceContent<Id extends string>({
  items,
  current,
  noneLabel,
  emptyText,
  onChoose,
}: Omit<ChoiceSheetProps<Id>, "open" | "onOpenChange" | "title">) {
  const { close, reportGuard } = useEditSheet();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    reportGuard({ dirty: false, submitting });
  }, [reportGuard, submitting]);

  const choose = async (id: Id | null) => {
    setSubmitting(true);
    setError(null);
    const failure = await onChoose(id);
    if (failure === null) {
      setSubmitting(false);
      close();
      return;
    }
    setSubmitting(false);
    setError(failure);
  };

  return (
    <EditSheetBody>
      {error !== null && <FormAlert message={error} />}
      <ul className="divide-y">
        {noneLabel !== undefined && (
          <li>
            <ChoiceRow
              label={noneLabel}
              chosen={current === null}
              disabled={submitting}
              onClick={() => {
                void choose(null);
              }}
            />
          </li>
        )}
        {items.map((item) => (
          <li key={item.id}>
            <ChoiceRow
              label={item.name}
              avatar={item.avatar}
              chosen={current === item.id}
              disabled={submitting}
              onClick={() => {
                void choose(item.id);
              }}
            />
          </li>
        ))}
      </ul>
      {items.length === 0 && (
        <p className="py-6 text-center text-sm text-muted-foreground">{emptyText}</p>
      )}
    </EditSheetBody>
  );
}

/** Строка выбора: картинка [avatar], подпись [label] и галочка, если она выбрана сейчас. */
function ChoiceRow({
  label,
  avatar,
  chosen,
  disabled,
  onClick,
}: {
  readonly label: string;
  readonly avatar?: ReactNode;
  readonly chosen: boolean;
  readonly disabled: boolean;
  readonly onClick: () => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      aria-current={chosen ? "true" : undefined}
      onClick={onClick}
      className="flex w-full items-center gap-3 px-1 py-3 text-left text-sm hover:bg-accent disabled:opacity-50"
    >
      {avatar}
      <span className="min-w-0 flex-1 truncate">{label}</span>
      {chosen && <CheckIcon aria-hidden className="size-4 shrink-0 text-primary" />}
    </button>
  );
}
