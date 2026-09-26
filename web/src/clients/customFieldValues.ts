import type { CustomFieldKey, CustomFieldValue } from "@/api/generated/contracts";

/** Значение поля [fieldKey] в [values], либо `undefined` если оно ещё не задано. */
export function customFieldValueOf(
  values: readonly CustomFieldValue[],
  fieldKey: CustomFieldKey,
): CustomFieldValue | undefined {
  return values.find((value) => value.fieldKey === fieldKey);
}

/** Копия [values] с обновлённым или добавленным значением [value]. */
export function withCustomFieldValue(
  values: readonly CustomFieldValue[],
  value: CustomFieldValue,
): readonly CustomFieldValue[] {
  const exists = values.some((v) => v.fieldKey === value.fieldKey);
  return exists
    ? values.map((v) => (v.fieldKey === value.fieldKey ? value : v))
    : [...values, value];
}

/** Копия [values] без значения поля [fieldKey]. */
export function withoutCustomFieldValue(
  values: readonly CustomFieldValue[],
  fieldKey: CustomFieldKey,
): readonly CustomFieldValue[] {
  return values.filter((value) => value.fieldKey !== fieldKey);
}
