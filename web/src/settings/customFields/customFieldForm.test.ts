import { describe, expect, it } from "vitest";
import {
  CustomFieldDefinitionSchema,
  SaveCustomFieldsRequestSchema,
  type CustomFieldDefinition,
} from "@/api/generated/contracts";
import { createTranslator } from "@/i18n/messages";
import { ru } from "@/i18n/ru";
import {
  CUSTOM_FIELD_TYPES,
  customFieldSchema,
  definitionOf,
  EMPTY_CUSTOM_FIELD,
  formValuesOf,
  parseOptions,
} from "./customFieldForm";

const t = createTranslator<typeof ru>("ru", ru);
const schema = customFieldSchema(t, new Set(["taken"]));

describe("определение дополнительного поля", () => {
  it("каждый тип собирается в определение, которое проходит схему контракта", () => {
    CUSTOM_FIELD_TYPES.forEach((fieldType) => {
      const definition = definitionOf({
        ...EMPTY_CUSTOM_FIELD,
        fieldKey: `f_${fieldType}`,
        label: `Поле ${fieldType}`,
        fieldType,
        options: "a, b",
      });
      expect(definition?.fieldType).toBe(fieldType);
      expect(CustomFieldDefinitionSchema.safeParse(definition).success).toBe(true);
      expect(
        SaveCustomFieldsRequestSchema.safeParse({ entityType: "CLIENT", fields: [definition] })
          .success,
      ).toBe(true);
    });
  });

  it("форма редактирования обратима для каждого типа", () => {
    const definitions: readonly CustomFieldDefinition[] = [
      CustomFieldDefinitionSchema.parse({
        fieldType: "select",
        fieldKey: "level",
        label: "Уровень",
        isRequired: true,
        isSearchable: true,
        isSortable: false,
        options: ["Новичок", "Профи"],
      }),
      CustomFieldDefinitionSchema.parse({
        fieldType: "number",
        fieldKey: "weight",
        label: "Вес",
        isRequired: false,
        isSearchable: false,
        isSortable: true,
        minValue: 20,
        maxValue: null,
      }),
      CustomFieldDefinitionSchema.parse({
        fieldType: "text",
        fieldKey: "belt",
        label: "Пояс",
        isRequired: false,
        isSearchable: false,
        isSortable: false,
        minLength: null,
        maxLength: 30,
      }),
      CustomFieldDefinitionSchema.parse({
        fieldType: "date",
        fieldKey: "medical",
        label: "Справка до",
        isRequired: true,
        isSearchable: false,
        isSortable: true,
      }),
    ];
    definitions.forEach((definition) => {
      expect(definitionOf(formValuesOf(definition))).toEqual(definition);
    });
  });

  it("опции разбираются по строкам и запятым", () => {
    expect(parseOptions(" Утро,\nВечер\n\n , День ")).toEqual(["Утро", "Вечер", "День"]);
  });

  it("проверяет ключ, название, опции и пределы", () => {
    const issues = (values: Partial<typeof EMPTY_CUSTOM_FIELD>) => {
      const result = schema.safeParse({
        ...EMPTY_CUSTOM_FIELD,
        fieldKey: "ok",
        label: "Ок",
        ...values,
      });
      return result.success
        ? []
        : result.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`);
    };
    expect(issues({})).toEqual([]);
    expect(issues({ fieldKey: "Bad-Key" })).toEqual([`fieldKey: ${ru["customFields.keyFormat"]}`]);
    expect(issues({ fieldKey: "taken" })).toEqual([`fieldKey: ${ru["customFields.keyTaken"]}`]);
    expect(issues({ label: "  " })).toEqual([`label: ${ru["error.required"]}`]);
    expect(issues({ fieldType: "select", options: " , " })).toEqual([
      `options: ${ru["customFields.optionsRequired"]}`,
    ]);
    expect(issues({ fieldType: "number", minValue: "10", maxValue: "5" })).toEqual([
      `maxValue: ${ru["customFields.minLessThanMax"]}`,
    ]);
    expect(issues({ fieldType: "text", minLength: "1.5" })).toEqual([
      `minLength: ${ru["customFields.invalidNumber"]}`,
    ]);
  });
});
