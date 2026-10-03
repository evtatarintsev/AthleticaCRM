import { PlusIcon } from "lucide-react";
import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type InputHTMLAttributes,
  type KeyboardEvent,
  type MouseEvent,
} from "react";
import { Input } from "@/components/ui/input";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { fieldError } from "./field";
import { FieldFrame, type FieldProps, type ManagedInputProps } from "./fields";
import { filterSuggestions, type SuggestionGroup } from "./nameSuggestions";

/** Вариант списка: подставляемое значение и идентификатор элемента для `aria-activedescendant`. */
interface ComboboxOption {
  readonly id: string;
  readonly value: string;
}

/** Свойства поля названия с подсказками. */
interface NameComboboxFieldProps
  extends FieldProps<string>, Omit<InputHTMLAttributes<HTMLInputElement>, ManagedInputProps> {
  /** Группы подсказок в порядке показа. */
  readonly groups: readonly SuggestionGroup[];
  /** Подсказки, которые уже есть в справочнике: ключ подсказки → название записи. */
  readonly existing: ReadonlyMap<string, string>;
}

/**
 * Текстовое поле названия со списком подсказок под ним (ARIA combobox). Список фильтруется
 * по вводу; выбор подставляет название подсказки, а последний вариант «Добавить … как есть»
 * оставляет введённый текст. Значение поля — всегда строка, поэтому проверки формы не меняются.
 * Список встроен в поток формы, а не всплывает: так он не спорит с ловушкой фокуса панели.
 */
export function NameComboboxField({
  field,
  label,
  hint,
  groups,
  existing,
  ...input
}: NameComboboxFieldProps) {
  const { t } = useI18n();
  const listboxId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);

  const value = field.state.value;
  const typed = value.trim();
  const matched = useMemo(() => filterSuggestions(groups, value), [groups, value]);
  const showAsIs =
    typed !== "" && !matched.some((g) => g.items.some((i) => i.suggestion.label === typed));
  const options = useMemo<readonly ComboboxOption[]>(
    () => [
      ...matched.flatMap((g) =>
        g.items.map((i) => ({ id: `${listboxId}-${i.suggestion.key}`, value: i.suggestion.label })),
      ),
      ...(showAsIs ? [{ id: `${listboxId}-as-is`, value: typed }] : []),
    ],
    [matched, showAsIs, typed, listboxId],
  );
  const expanded = open && options.length > 0;
  const activeOption = expanded ? options[active] : undefined;

  useEffect(() => {
    const list = listRef.current;
    const option = list?.querySelector<HTMLElement>(`[data-index="${String(active)}"]`);
    if (list === null || option === null || option === undefined) {
      return;
    }
    if (option.offsetTop < list.scrollTop) {
      list.scrollTop = option.offsetTop;
    } else if (option.offsetTop + option.offsetHeight > list.scrollTop + list.clientHeight) {
      list.scrollTop = option.offsetTop + option.offsetHeight - list.clientHeight;
    }
  }, [active]);

  const choose = (option: ComboboxOption) => {
    field.handleChange(option.value);
    setOpen(false);
    setActive(-1);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    switch (event.key) {
      case "ArrowDown":
      case "ArrowUp": {
        event.preventDefault();
        if (options.length === 0) {
          return;
        }
        const step = event.key === "ArrowDown" ? 1 : -1;
        setOpen(true);
        setActive((current) =>
          !expanded || current < 0
            ? step > 0
              ? 0
              : options.length - 1
            : (current + step + options.length) % options.length,
        );
        return;
      }
      case "Enter":
        if (activeOption !== undefined) {
          event.preventDefault();
          choose(activeOption);
        }
        return;
      case "Escape":
        if (expanded) {
          event.preventDefault();
          setOpen(false);
          setActive(-1);
        }
        return;
      case "Tab":
        setOpen(false);
        setActive(-1);
        return;
      default:
        return;
    }
  };

  /** Свойства элемента списка для варианта [option]. */
  const optionProps = (option: ComboboxOption) => {
    const own = options.findIndex((o) => o.id === option.id);
    return {
      id: option.id,
      role: "option",
      "aria-selected": own === active,
      "data-index": own,
      onMouseDown: (event: MouseEvent) => {
        event.preventDefault();
      },
      onClick: () => {
        choose(option);
      },
      onMouseMove: () => {
        setActive(own);
      },
      className: cn(
        "flex cursor-pointer items-baseline gap-2 rounded-sm px-2 py-1.5",
        own === active && "bg-accent text-accent-foreground",
      ),
    };
  };

  return (
    <FieldFrame
      label={label}
      hint={hint}
      error={fieldError(field)}
      control={(aria) => (
        <div>
          <Input
            {...input}
            {...aria}
            type="text"
            role="combobox"
            autoComplete="off"
            aria-autocomplete="list"
            aria-expanded={expanded}
            aria-controls={expanded ? listboxId : undefined}
            aria-activedescendant={activeOption?.id}
            name={field.name}
            value={value}
            onChange={(event) => {
              field.handleChange(event.target.value);
              setOpen(true);
              setActive(-1);
            }}
            onFocus={() => {
              setOpen(true);
            }}
            onBlur={() => {
              setOpen(false);
              setActive(-1);
              field.handleBlur();
            }}
            onKeyDown={onKeyDown}
          />
          {expanded && (
            <div
              ref={listRef}
              id={listboxId}
              role="listbox"
              aria-label={t("directory.suggestions")}
              className="mt-1 max-h-72 overflow-y-auto rounded-md border bg-popover p-1 text-sm text-popover-foreground"
            >
              {matched.map(({ group, items }) => (
                <div
                  key={group.key}
                  role="group"
                  aria-labelledby={`${listboxId}-group-${group.key}`}
                >
                  <div
                    id={`${listboxId}-group-${group.key}`}
                    className="px-2 pt-2 pb-1 text-xs font-medium text-muted-foreground"
                  >
                    {group.label}
                  </div>
                  {items.map(({ suggestion, hint: matchedBy }) => {
                    const already = existing.get(suggestion.key);
                    return (
                      <div
                        key={suggestion.key}
                        {...optionProps({
                          id: `${listboxId}-${suggestion.key}`,
                          value: suggestion.label,
                        })}
                      >
                        <span
                          className={cn("min-w-0 truncate", already !== undefined && "opacity-60")}
                        >
                          {suggestion.label}
                        </span>
                        {matchedBy !== null && (
                          <span className="min-w-0 truncate text-xs text-muted-foreground">
                            {matchedBy}
                          </span>
                        )}
                        {already !== undefined && (
                          <span className="ml-auto shrink-0 text-xs text-muted-foreground">
                            {t("directory.alreadyAdded", { name: already })}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              ))}
              {showAsIs && (
                <div {...optionProps({ id: `${listboxId}-as-is`, value: typed })}>
                  <PlusIcon aria-hidden className="size-4 shrink-0 self-center" />
                  <span className="min-w-0 truncate">
                    {t("directory.addAsIs", { name: typed })}
                  </span>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    />
  );
}
