import type {
  ClientContactInput,
  ClientContactSchema,
  ContactType,
} from "@/api/generated/contracts";

/** Строка формы контактов: тип и значение. Новая строка — пустой телефон. */
export interface ContactFormEntry {
  readonly type: ContactType;
  readonly value: string;
}

/** Пустая строка контакта. */
export const EMPTY_CONTACT: ContactFormEntry = { type: "PHONE", value: "" };

/** Строки формы из контактов клиента; пустой список клиента даёт одну пустую строку ввода. */
export function contactEntriesOf(
  contacts: readonly ClientContactSchema[],
): readonly ContactFormEntry[] {
  if (contacts.length === 0) {
    return [EMPTY_CONTACT];
  }
  return contacts.map((contact) => ({ type: contact.type, value: contact.value }));
}

/** Копия [entries] с заменённой строкой по индексу [index]. */
export function replacedContactAt(
  entries: readonly ContactFormEntry[],
  index: number,
  entry: ContactFormEntry,
): readonly ContactFormEntry[] {
  return entries.map((current, i) => (i === index ? entry : current));
}

/** Копия [entries] без строки по индексу [index]. */
export function removedContactAt(
  entries: readonly ContactFormEntry[],
  index: number,
): readonly ContactFormEntry[] {
  return entries.filter((_, i) => i !== index);
}

/** Контакты для отправки на сервер: непустые значения с обрезанными пробелами. */
export function contactInputsOf(
  entries: readonly ContactFormEntry[],
): readonly ClientContactInput[] {
  return entries
    .filter((entry) => entry.value.trim() !== "")
    .map((entry) => ({ type: entry.type, value: entry.value.trim() }));
}
