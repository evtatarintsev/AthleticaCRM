import type {
  ClientContactInputInput,
  ClientContactSchema,
  ContactType,
} from "@/api/generated/contracts";

/**
 * Строка формы контактов: тип (не меняется после добавления), значение, комментарий
 * (пустая строка — без комментария) и отметка «основной» внутри своего типа.
 */
export interface ContactFormEntry {
  readonly type: ContactType;
  readonly value: string;
  readonly label: string;
  readonly isPrimary: boolean;
}

/** Копия [entries] с новой пустой строкой типа [type]; первая строка типа становится основной. */
export function withContactAdded(
  entries: readonly ContactFormEntry[],
  type: ContactType,
): readonly ContactFormEntry[] {
  const isPrimary = !entries.some((entry) => entry.type === type);
  return [...entries, { type, value: "", label: "", isPrimary }];
}

/** Строки формы из контактов клиента; пустой список клиента даёт одну пустую строку телефона. */
export function contactEntriesOf(
  contacts: readonly ClientContactSchema[],
): readonly ContactFormEntry[] {
  if (contacts.length === 0) {
    return withContactAdded([], "PHONE");
  }
  return contacts.map((contact) => ({
    type: contact.type,
    value: contact.value,
    label: contact.label ?? "",
    isPrimary: contact.isPrimary,
  }));
}

/** Копия [entries] с заменённой строкой по индексу [index]. */
export function replacedContactAt(
  entries: readonly ContactFormEntry[],
  index: number,
  entry: ContactFormEntry,
): readonly ContactFormEntry[] {
  return entries.map((current, i) => (i === index ? entry : current));
}

/** Копия [entries], где основной в своём типе — строка [index]; строки других типов не меняются. */
export function withPrimaryAt(
  entries: readonly ContactFormEntry[],
  index: number,
): readonly ContactFormEntry[] {
  const target = entries[index];
  if (target === undefined) {
    return entries;
  }
  return entries.map((entry, i) =>
    entry.type === target.type ? { ...entry, isPrimary: i === index } : entry,
  );
}

/**
 * Копия [entries] без строки по индексу [index]. Если удалена основная строка, основной
 * становится первая оставшаяся строка того же типа.
 */
export function removedContactAt(
  entries: readonly ContactFormEntry[],
  index: number,
): readonly ContactFormEntry[] {
  const removed = entries[index];
  const rest = entries.filter((_, i) => i !== index);
  if (removed?.isPrimary !== true) {
    return rest;
  }
  const heir = rest.findIndex((entry) => entry.type === removed.type);
  return heir === -1 ? rest : withPrimaryAt(rest, heir);
}

/** Число строк типа [type] в [entries]: флажок «основной» нужен, только когда их больше одной. */
export function contactCountOf(entries: readonly ContactFormEntry[], type: ContactType): number {
  return entries.filter((entry) => entry.type === type).length;
}

/**
 * Контакты для отправки на сервер: непустые значения и комментарии с обрезанными пробелами,
 * пустой комментарий — `null`.
 */
export function contactInputsOf(
  entries: readonly ContactFormEntry[],
): readonly ClientContactInputInput[] {
  return entries
    .filter((entry) => entry.value.trim() !== "")
    .map((entry) => {
      const label = entry.label.trim();
      return {
        type: entry.type,
        value: entry.value.trim(),
        label: label === "" ? null : label,
        isPrimary: entry.isPrimary,
      };
    });
}
