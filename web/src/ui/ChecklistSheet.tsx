import { useState, type ReactNode } from "react";
import { FormAlert } from "@/forms/FormAlert";
import { EditSheet, EditSheetForm } from "./EditSheet";
import { useEditSheet } from "./editSheetContext";

/** Запись списка [ChecklistSheet]. */
export interface ChecklistItem<Id extends string> {
  /** Идентификатор записи. */
  readonly id: Id;
  /** Подпись записи. */
  readonly name: string;
  /** Необязательная картинка перед подписью, например аватар сотрудника. */
  readonly avatar?: ReactNode;
}

/** Свойства панели с чекбоксами. */
interface ChecklistSheetProps<Id extends string> {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Заголовок панели. */
  readonly title: string;
  /** Все записи, из которых выбирают. */
  readonly items: readonly ChecklistItem<Id>[];
  /** Текущий набор: эти записи отмечены при открытии. */
  readonly selected: readonly Id[];
  /** Сообщение при пустом списке [items]. */
  readonly emptyText: string;
  /**
   * Сохранение нового набора: `null` — успех, панель закрывается; строка — текст ошибки,
   * панель остаётся открытой с прежними отметками.
   */
  readonly onSubmit: (ids: readonly Id[]) => Promise<string | null>;
}

/**
 * Панель справа для замены небольшого набора записей целиком: все записи [items] с
 * чекбоксами, отмечен текущий набор [selected]. «Сохранить» передаёт отмеченные в
 * `onSubmit`, «Отмена» ничего не меняет; изменённые отметки защищены подтверждением.
 */
export function ChecklistSheet<Id extends string>({
  open,
  onOpenChange,
  title,
  ...rest
}: ChecklistSheetProps<Id>) {
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={title}>
      <ChecklistContent {...rest} />
    </EditSheet>
  );
}

/** Содержимое панели; монтируется заново при каждом открытии. */
function ChecklistContent<Id extends string>({
  items,
  selected,
  emptyText,
  onSubmit,
}: Omit<ChecklistSheetProps<Id>, "open" | "onOpenChange" | "title">) {
  const { close } = useEditSheet();
  const [draft, setDraft] = useState<ReadonlySet<Id>>(new Set(selected));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const initial = new Set(selected);
  const dirty = draft.size !== initial.size || Array.from(draft).some((id) => !initial.has(id));

  const toggle = (id: Id) => {
    const next = new Set(draft);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setDraft(next);
  };

  const submit = async () => {
    setSubmitting(true);
    setError(null);
    const failure = await onSubmit(Array.from(draft));
    if (failure === null) {
      close();
      return;
    }
    setSubmitting(false);
    setError(failure);
  };

  return (
    <EditSheetForm
      dirty={dirty}
      submitting={submitting}
      onSubmit={() => {
        void submit();
      }}
    >
      {error !== null && <FormAlert message={error} />}
      {items.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <ul className="divide-y">
          {items.map((item) => (
            <li key={item.id}>
              <label className="flex cursor-pointer items-center gap-3 px-1 py-3 text-sm hover:bg-accent has-disabled:cursor-default has-disabled:opacity-50">
                <input
                  type="checkbox"
                  checked={draft.has(item.id)}
                  disabled={submitting}
                  onChange={() => {
                    toggle(item.id);
                  }}
                  className="size-4 shrink-0 accent-primary"
                />
                {item.avatar}
                <span className="min-w-0 flex-1 truncate">{item.name}</span>
              </label>
            </li>
          ))}
        </ul>
      )}
    </EditSheetForm>
  );
}
