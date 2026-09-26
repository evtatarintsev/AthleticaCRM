import { PlusIcon, XIcon } from "lucide-react";
import type { ContactType } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { manualField } from "@/forms/field";
import { SelectField, TextField, type SelectOption } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import {
  EMPTY_CONTACT,
  removedContactAt,
  replacedContactAt,
  type ContactFormEntry,
} from "./clientContacts";

const CONTACT_TYPES: readonly ContactType[] = ["PHONE", "EMAIL", "TELEGRAM", "VK", "FACEBOOK"];

/** Контакты клиента на форме создания/редактирования: тип, значение, добавление и удаление строк. */
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
  const typeOptions: readonly SelectOption<ContactType>[] = CONTACT_TYPES.map((type) => ({
    value: type,
    label: t(`contactType.${type}`),
  }));

  return (
    <fieldset className="space-y-2">
      <legend className="mb-1 text-sm font-medium">{t("clients.contacts")}</legend>
      {entries.map((entry, index) => (
        <div key={index} className="flex items-end gap-2">
          <div className="w-36 shrink-0">
            <SelectField
              field={manualField(`contact-type-${index.toString()}`, entry.type, (type) => {
                onChange(replacedContactAt(entries, index, { ...entry, type }));
              })}
              label={t("clients.contactType")}
              options={typeOptions}
            />
          </div>
          <div className="flex-1">
            <TextField
              field={manualField(`contact-value-${index.toString()}`, entry.value, (value) => {
                onChange(replacedContactAt(entries, index, { ...entry, value }));
              })}
              label={t(`contactType.${entry.type}`)}
              autoComplete="off"
              disabled={disabled}
            />
          </div>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            disabled={disabled}
            aria-label={t("clients.removeContact")}
            onClick={() => {
              onChange(removedContactAt(entries, index));
            }}
          >
            <XIcon aria-hidden />
          </Button>
        </div>
      ))}
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={disabled}
        onClick={() => {
          onChange([...entries, EMPTY_CONTACT]);
        }}
      >
        <PlusIcon aria-hidden />
        {t("clients.addContact")}
      </Button>
    </fieldset>
  );
}
