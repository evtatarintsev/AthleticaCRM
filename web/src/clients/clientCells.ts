import type { RowSelectionState } from "@tanstack/react-table";
import type {
  ClientId,
  ClientListItem,
  ContactType,
  CustomFieldValue,
} from "@/api/generated/contracts";
import type { ClientFieldKey } from "./clientColumns";

/** Значение дополнительного поля клиента [client] с ключом [fieldKey], либо `undefined`. */
export function clientFieldValue(
  client: ClientListItem,
  fieldKey: string,
): CustomFieldValue | undefined {
  return client.customFields.find((field) => field.fieldKey === fieldKey);
}

/** Значения контактов клиента [client] типа [type] в порядке ответа. */
export function contactValues(client: ClientListItem, type: ContactType): readonly string[] {
  return client.contacts.filter((contact) => contact.type === type).map((contact) => contact.value);
}

/**
 * Короткое текстовое представление значения дополнительного поля [value]:
 * булево — «✓»/«—», число и дата — как записаны, остальное — значение.
 */
export function customFieldDisplay(value: CustomFieldValue): string {
  switch (value.type) {
    case "bool":
      return value.value ? "✓" : "—";
    case "number":
      return String(value.value);
    case "text":
    case "select":
    case "date":
      return value.value;
  }
}

/** Идентификаторы выбранных клиентов из состояния таблицы [rowSelection]. */
export function selectedClientIds(
  clients: readonly ClientListItem[],
  rowSelection: RowSelectionState,
): readonly ClientId[] {
  return clients.filter((client) => rowSelection[client.id] === true).map((client) => client.id);
}

/** Колонка сортировки для стандартного поля [field], либо `null` — поле не сортируется. */
export function sortableField(field: ClientFieldKey): "balance" | "birthday" | null {
  switch (field) {
    case "balance":
    case "birthday":
      return field;
    case "gender":
    case "groups":
      return null;
  }
}
