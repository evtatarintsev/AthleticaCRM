import { PlusIcon } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
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

/** Свойства вложенной панели создания записи, которую открывает [ChecklistSheet]. */
export interface ChecklistCreateProps<Id extends string> {
  /** Открыта ли панель создания. */
  readonly open: boolean;
  /** Вызывается, когда панель создания надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Строка поиска на момент открытия — заготовка названия новой записи; может быть пустой. */
  readonly name: string;
  /** Сообщает о созданной записи: она сразу отмечается в списке. */
  readonly onCreated: (id: Id) => void;
}

/** Создание новой записи прямо из списка, когда подходящей в нём нет. */
export interface ChecklistCreate<Id extends string> {
  /** Подпись кнопки создания по строке поиска [query]; пустая строка — поиск не заполнен. */
  readonly label: (query: string) => string;
  /** Вложенная панель создания записи. */
  readonly render: (props: ChecklistCreateProps<Id>) => ReactNode;
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
   * Подпись поля поиска по подписям записей; без неё поля поиска нет. Нужна длинным спискам,
   * например правам и ролям сотрудника.
   */
  readonly searchLabel?: string | undefined;
  /** Подпись кнопки подтверждения вместо «Сохранить». */
  readonly submitLabel?: string | undefined;
  /**
   * Сохранение нового набора: `null` — успех, панель закрывается; строка — текст ошибки,
   * панель остаётся открытой с прежними отметками.
   */
  readonly onSubmit: (ids: readonly Id[]) => Promise<string | null>;
  /** Кнопка создания записи под списком; без неё создавать из панели нельзя. */
  readonly create?: ChecklistCreate<Id> | undefined;
}

/**
 * Панель справа для замены небольшого набора записей целиком: все записи [items] с
 * чекбоксами, отмечен текущий набор [selected]. «Сохранить» передаёт отмеченные в
 * `onSubmit`, «Отмена» ничего не меняет; изменённые отметки защищены подтверждением.
 * Поиск [ChecklistSheetProps.searchLabel] только скрывает записи: отмеченные, но скрытые
 * поиском записи остаются в наборе. С [ChecklistSheetProps.create] под списком есть кнопка
 * создания записи: созданная запись сразу отмечается, сохраняет набор по-прежнему «Сохранить».
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
  searchLabel,
  submitLabel,
  onSubmit,
  create,
}: Omit<ChecklistSheetProps<Id>, "open" | "onOpenChange" | "title">) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [draft, setDraft] = useState<ReadonlySet<Id>>(new Set(selected));
  const [query, setQuery] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState<string | null>(null);

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

  const created = (id: Id) => {
    setDraft(new Set(draft).add(id));
    setQuery("");
  };

  const needle = query.trim().toLocaleLowerCase();
  const visible =
    needle === "" ? items : items.filter((item) => item.name.toLocaleLowerCase().includes(needle));

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
      submitLabel={submitLabel ?? t("action.save")}
      onSubmit={() => {
        void submit();
      }}
      nested={create?.render({
        open: creating !== null,
        onOpenChange: (next) => {
          setCreating(next ? query.trim() : null);
        },
        name: creating ?? "",
        onCreated: created,
      })}
    >
      {searchLabel !== undefined && items.length > 0 && (
        <Input
          type="search"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
            }
          }}
          placeholder={searchLabel}
          aria-label={searchLabel}
        />
      )}
      {error !== null && <FormAlert message={error} />}
      {items.length === 0 && (
        <p className="py-6 text-center text-sm text-muted-foreground">{emptyText}</p>
      )}
      {items.length > 0 && visible.length === 0 && (
        <p className="py-6 text-center text-sm text-muted-foreground">{t("picker.nothingFound")}</p>
      )}
      {visible.length > 0 && (
        <ul className="divide-y">
          {visible.map((item) => (
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
      {create !== undefined && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={submitting}
          onClick={() => {
            setCreating(query.trim());
          }}
        >
          <PlusIcon />
          {create.label(visible.length === 0 ? query.trim() : "")}
        </Button>
      )}
    </EditSheetForm>
  );
}
