import { ChevronDownIcon, PlusIcon, XIcon } from "lucide-react";
import { useId, useRef, useState } from "react";
import type { ContactType } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { useI18n } from "@/i18n/context";
import {
  contactCountOf,
  removedContactAt,
  replacedContactAt,
  withContactAdded,
  withPrimaryAt,
  type ContactFormEntry,
} from "./clientContacts";
import { contactIcon } from "./contactIcon";

/** Предел длины комментария к контакту; совпадает с серверным `MAX_CONTACT_LABEL_LENGTH`. */
const MAX_CONTACT_LABEL_LENGTH = 100;

/** Редкие типы контактов: добавляются из меню «Ещё», а не отдельной кнопкой. */
const MORE_CONTACT_TYPES: readonly ContactType[] = ["TELEGRAM", "VK", "FACEBOOK"];

/** Тип поля ввода под значение контакта [type]: телефон и почта получают свою клавиатуру. */
function inputTypeOf(type: ContactType): "tel" | "email" | "text" {
  switch (type) {
    case "PHONE":
      return "tel";
    case "EMAIL":
      return "email";
    case "TELEGRAM":
    case "VK":
    case "FACEBOOK":
      return "text";
  }
}

/**
 * Контакты клиента на форме создания/редактирования: строка — иконка типа, значение,
 * комментарий, выбор основного (если строк типа несколько) и удаление. Тип строки задаётся
 * кнопкой добавления и не меняется.
 */
export function ClientContactsFields({
  entries,
  onChange,
  disabled,
}: {
  readonly entries: readonly ContactFormEntry[];
  readonly onChange: (entries: readonly ContactFormEntry[]) => void;
  readonly disabled: boolean;
}) {
  const { t } = useI18n();
  const radioGroup = useId();
  const [focusIndex, setFocusIndex] = useState<number | null>(null);
  const pickedFromMenu = useRef(false);
  const addedInput = useRef<HTMLInputElement>(null);

  const add = (type: ContactType) => {
    setFocusIndex(entries.length);
    onChange(withContactAdded(entries, type));
  };

  return (
    <fieldset className="space-y-2">
      <legend className="mb-1 text-sm font-medium">{t("clients.contacts")}</legend>
      {entries.map((entry, index) => (
        <div key={index} className="flex flex-wrap items-center gap-2">
          <span className="inline-flex size-9 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground [&_svg]:size-4">
            {contactIcon(entry.type)}
          </span>
          <Input
            className="min-w-40 flex-1"
            type={inputTypeOf(entry.type)}
            aria-label={t(`contactType.${entry.type}`)}
            placeholder={t(`contactType.${entry.type}`)}
            autoComplete="off"
            autoFocus={index === focusIndex}
            ref={index === focusIndex ? addedInput : undefined}
            value={entry.value}
            disabled={disabled}
            onChange={(event) => {
              onChange(replacedContactAt(entries, index, { ...entry, value: event.target.value }));
            }}
          />
          <Input
            className="w-full sm:w-40"
            aria-label={t("clients.contactLabel")}
            placeholder={t("clients.contactLabelPlaceholder")}
            autoComplete="off"
            maxLength={MAX_CONTACT_LABEL_LENGTH}
            value={entry.label}
            disabled={disabled}
            onChange={(event) => {
              onChange(replacedContactAt(entries, index, { ...entry, label: event.target.value }));
            }}
          />
          {contactCountOf(entries, entry.type) > 1 && (
            <label className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <input
                type="radio"
                name={`${radioGroup}-${entry.type}`}
                checked={entry.isPrimary}
                disabled={disabled}
                onChange={() => {
                  onChange(withPrimaryAt(entries, index));
                }}
                className="size-4 accent-primary"
              />
              {t("clients.contactPrimary")}
            </label>
          )}
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={disabled}
            aria-label={t("clients.removeContact")}
            onClick={() => {
              setFocusIndex(null);
              onChange(removedContactAt(entries, index));
            }}
          >
            <XIcon aria-hidden />
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled}
          onClick={() => {
            add("PHONE");
          }}
        >
          <PlusIcon aria-hidden />
          {t("clients.addPhone")}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled}
          onClick={() => {
            add("EMAIL");
          }}
        >
          <PlusIcon aria-hidden />
          {t("clients.addEmail")}
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button type="button" variant="outline" size="sm" disabled={disabled}>
              <PlusIcon aria-hidden />
              {t("clients.addMoreContacts")}
              <ChevronDownIcon aria-hidden />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            align="start"
            onCloseAutoFocus={(event) => {
              if (pickedFromMenu.current) {
                pickedFromMenu.current = false;
                event.preventDefault();
                requestAnimationFrame(() => {
                  addedInput.current?.focus();
                });
              }
            }}
          >
            {MORE_CONTACT_TYPES.map((type) => (
              <DropdownMenuItem
                key={type}
                onSelect={() => {
                  pickedFromMenu.current = true;
                  add(type);
                }}
              >
                {contactIcon(type)}
                {t(`contactType.${type}`)}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </fieldset>
  );
}
