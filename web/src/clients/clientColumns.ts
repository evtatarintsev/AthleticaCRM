import type { ContactType, CustomFieldDefinition } from "@/api/generated/contracts";
import type { PlainMessageKey } from "@/i18n/context";

/** Стандартное поле клиента, доступное как колонка таблицы (каталог `ClientField`). */
export type ClientFieldKey = "birthday" | "gender" | "groups" | "balance";

/**
 * Колонка таблицы клиентов, восстановленная из ключей настроек отображения.
 * «Имя» — отдельная всегда видимая колонка и в настройки не входит.
 */
export type ClientColumn =
  | { readonly kind: "standard"; readonly field: ClientFieldKey }
  | { readonly kind: "contact"; readonly type: ContactType }
  | { readonly kind: "custom"; readonly fieldKey: string; readonly label: string };

/** Стандартные поля в устойчивом порядке. */
export const CLIENT_FIELD_KEYS: readonly ClientFieldKey[] = [
  "birthday",
  "gender",
  "groups",
  "balance",
];

/** Типы контактов в устойчивом порядке. */
export const CONTACT_TYPES: readonly ContactType[] = [
  "PHONE",
  "EMAIL",
  "TELEGRAM",
  "VK",
  "FACEBOOK",
];

/** Префикс ключа контакт-колонки: `contact:PHONE` и т. п. */
const CONTACT_PREFIX = "contact:";

/** Ключ контакт-колонки типа [type] в настройках отображения. */
export function contactColumnKey(type: ContactType): string {
  return `${CONTACT_PREFIX}${type}`;
}

/** Ключ названия стандартного поля [field] в словаре. */
export function standardColumnLabelKey(field: ClientFieldKey): PlainMessageKey {
  switch (field) {
    case "gender":
      return "clients.column.gender";
    case "birthday":
      return "clients.column.birthday";
    case "balance":
      return "clients.column.balance";
    case "groups":
      return "clients.column.groups";
  }
}

/**
 * Разбирает ключ [key] настроек отображения в колонку. Стандартные поля распознаются
 * по каталогу `ClientField`, контакт-колонки — по префиксу, остальное ищется среди
 * определений дополнительных полей [customFields]. Неизвестный ключ — `null`.
 */
export function clientColumnOf(
  key: string,
  customFields: readonly CustomFieldDefinition[],
): ClientColumn | null {
  const field = CLIENT_FIELD_KEYS.find((candidate) => candidate === key);
  if (field !== undefined) {
    return { kind: "standard", field };
  }
  if (key.startsWith(CONTACT_PREFIX)) {
    const type = CONTACT_TYPES.find((candidate) => contactColumnKey(candidate) === key);
    return type === undefined ? null : { kind: "contact", type };
  }
  const definition = customFields.find((field) => field.fieldKey === key);
  return definition === undefined
    ? null
    : { kind: "custom", fieldKey: key, label: definition.label };
}

/**
 * Колонки таблицы из ключей настроек отображения [keys] и определений дополнительных
 * полей [customFields]. Неизвестные ключи (удалённые поля, устаревшие) отбрасываются.
 */
export function resolveClientColumns(
  keys: readonly string[],
  customFields: readonly CustomFieldDefinition[],
): readonly ClientColumn[] {
  return keys.flatMap((key) => {
    const column = clientColumnOf(key, customFields);
    return column === null ? [] : [column];
  });
}

/** Ключ колонки [column] в настройках отображения. */
export function clientColumnKey(column: ClientColumn): string {
  switch (column.kind) {
    case "standard":
      return column.field;
    case "contact":
      return contactColumnKey(column.type);
    case "custom":
      return column.fieldKey;
  }
}

/**
 * Колонки, которые не включены в [enabled]: стандартные, контактные и дополнительные
 * в устойчивом порядке. Показываются в диалоге настроек колонок.
 */
export function availableClientColumns(
  enabled: readonly ClientColumn[],
  customFields: readonly CustomFieldDefinition[],
): readonly ClientColumn[] {
  const selected = new Set(enabled.map(clientColumnKey));
  const standard = CLIENT_FIELD_KEYS.filter((field) => !selected.has(field)).map(
    (field): ClientColumn => ({ kind: "standard", field }),
  );
  const contact = CONTACT_TYPES.map((type): ClientColumn => ({ kind: "contact", type })).filter(
    (column) => !selected.has(clientColumnKey(column)),
  );
  const custom = customFields
    .filter((field) => !selected.has(field.fieldKey))
    .map((field): ClientColumn => ({
      kind: "custom",
      fieldKey: field.fieldKey,
      label: field.label,
    }));
  return [...standard, ...contact, ...custom];
}
