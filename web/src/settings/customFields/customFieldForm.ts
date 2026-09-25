import { z } from "zod";
import { CustomFieldKeySchema, type CustomFieldDefinition } from "@/api/generated/contracts";
import type { I18n } from "@/i18n/context";

/** Тип дополнительного поля — дискриминатор определения. */
export type CustomFieldType = CustomFieldDefinition["fieldType"];

/** Типы в порядке показа в выпадающем списке. */
export const CUSTOM_FIELD_TYPES: readonly CustomFieldType[] = [
  "text",
  "number",
  "date",
  "select",
  "boolean",
  "phone",
  "email",
  "url",
];

/** Значения формы определения поля: числа и опции вводятся текстом. */
export interface CustomFieldFormValues {
  readonly fieldKey: string;
  readonly label: string;
  readonly fieldType: CustomFieldType;
  readonly isRequired: boolean;
  readonly isSearchable: boolean;
  readonly isSortable: boolean;
  readonly options: string;
  readonly minValue: string;
  readonly maxValue: string;
  readonly minLength: string;
  readonly maxLength: string;
}

/** Пустая форма нового поля: строка без ограничений. */
export const EMPTY_CUSTOM_FIELD: CustomFieldFormValues = {
  fieldKey: "",
  label: "",
  fieldType: "text",
  isRequired: false,
  isSearchable: false,
  isSortable: false,
  options: "",
  minValue: "",
  maxValue: "",
  minLength: "",
  maxLength: "",
};

/** Значения формы для редактирования определения [definition]. */
export function formValuesOf(definition: CustomFieldDefinition): CustomFieldFormValues {
  const base: CustomFieldFormValues = {
    ...EMPTY_CUSTOM_FIELD,
    fieldKey: definition.fieldKey,
    label: definition.label,
    fieldType: definition.fieldType,
    isRequired: definition.isRequired,
    isSearchable: definition.isSearchable,
    isSortable: definition.isSortable,
  };
  switch (definition.fieldType) {
    case "select":
      return { ...base, options: definition.options.join("\n") };
    case "number":
      return {
        ...base,
        minValue: definition.minValue?.toString() ?? "",
        maxValue: definition.maxValue?.toString() ?? "",
      };
    case "text":
      return {
        ...base,
        minLength: definition.minLength?.toString() ?? "",
        maxLength: definition.maxLength?.toString() ?? "",
      };
    case "date":
    case "boolean":
    case "phone":
    case "email":
    case "url":
      return base;
  }
}

/** Опции выбора из текста [text]: по строкам и через запятую, без пустых. */
export function parseOptions(text: string): readonly string[] {
  return text
    .split(/[\n,]/)
    .map((option) => option.trim())
    .filter((option) => option !== "");
}

/** Необязательное целое из текста: пусто — `null`, не целое — `undefined`. */
function optionalInt(text: string, max: number): number | null | undefined {
  const trimmed = text.trim();
  if (trimmed === "") {
    return null;
  }
  if (!/^-?\d+$/.test(trimmed)) {
    return undefined;
  }
  const value = Number(trimmed);
  return Math.abs(value) <= max ? value : undefined;
}

/** Пределы `Long` и `Int` в Kotlin, которые помещаются в безопасное целое JS. */
const LIMITS = { long: Number.MAX_SAFE_INTEGER, int: 2_147_483_647 } as const;

/**
 * Схема формы определения с сообщениями на языке [t]. [takenKeys] — ключи других полей:
 * новый ключ не должен с ними совпадать.
 */
export function customFieldSchema(t: I18n["t"], takenKeys: ReadonlySet<string>) {
  const intField = (max: number) =>
    z.string().refine((v) => optionalInt(v, max) !== undefined, t("customFields.invalidNumber"));
  return z
    .object({
      fieldKey: z
        .string()
        .trim()
        .min(1, t("error.required"))
        .refine((v) => CustomFieldKeySchema.safeParse(v).success, t("customFields.keyFormat"))
        .refine((v) => !takenKeys.has(v), t("customFields.keyTaken")),
      label: z.string().trim().min(1, t("error.required")),
      fieldType: z.enum(CUSTOM_FIELD_TYPES),
      isRequired: z.boolean(),
      isSearchable: z.boolean(),
      isSortable: z.boolean(),
      options: z.string(),
      minValue: intField(LIMITS.long),
      maxValue: intField(LIMITS.long),
      minLength: intField(LIMITS.int),
      maxLength: intField(LIMITS.int),
    })
    .refine((v) => v.fieldType !== "select" || parseOptions(v.options).length > 0, {
      path: ["options"],
      message: t("customFields.optionsRequired"),
    })
    .refine((v) => v.fieldType !== "number" || isRange(v.minValue, v.maxValue, LIMITS.long), {
      path: ["maxValue"],
      message: t("customFields.minLessThanMax"),
    })
    .refine((v) => v.fieldType !== "text" || isRange(v.minLength, v.maxLength, LIMITS.int), {
      path: ["maxLength"],
      message: t("customFields.minLessThanMax"),
    });
}

/** Истина, если минимум [min] меньше максимума [max] или один из них не задан. */
function isRange(min: string, max: string, limit: number): boolean {
  const from = optionalInt(min, limit);
  const to = optionalInt(max, limit);
  return from === undefined || to === undefined || from === null || to === null || from < to;
}

/** Определение поля из проверенной формы [values]; некорректная форма — `null`. */
export function definitionOf(values: CustomFieldFormValues): CustomFieldDefinition | null {
  const fieldKey = CustomFieldKeySchema.safeParse(values.fieldKey.trim());
  if (!fieldKey.success) {
    return null;
  }
  const base = {
    fieldKey: fieldKey.data,
    label: values.label.trim(),
    isRequired: values.isRequired,
    isSearchable: values.isSearchable,
    isSortable: values.isSortable,
  };
  switch (values.fieldType) {
    case "select":
      return { ...base, fieldType: "select", options: parseOptions(values.options) };
    case "number": {
      const minValue = optionalInt(values.minValue, LIMITS.long);
      const maxValue = optionalInt(values.maxValue, LIMITS.long);
      return minValue === undefined || maxValue === undefined
        ? null
        : { ...base, fieldType: "number", minValue, maxValue };
    }
    case "text": {
      const minLength = optionalInt(values.minLength, LIMITS.int);
      const maxLength = optionalInt(values.maxLength, LIMITS.int);
      return minLength === undefined || maxLength === undefined
        ? null
        : { ...base, fieldType: "text", minLength, maxLength };
    }
    case "date":
      return { ...base, fieldType: "date" };
    case "boolean":
      return { ...base, fieldType: "boolean" };
    case "phone":
      return { ...base, fieldType: "phone" };
    case "email":
      return { ...base, fieldType: "email" };
    case "url":
      return { ...base, fieldType: "url" };
  }
}
