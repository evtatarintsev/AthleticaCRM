import {
  LocalDateSchema,
  type CustomFieldDefinition,
  type CustomFieldValue,
} from "@/api/generated/contracts";
import { manualField } from "@/forms/field";
import { CheckboxField, SelectField, TextField, type SelectOption } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import {
  customFieldValueOf,
  withCustomFieldValue,
  withoutCustomFieldValue,
} from "./customFieldValues";

/** Значение выбора «вариант не выбран» в списке типа `select`. */
const NO_SELECTION = "";

/**
 * Дополнительные поля клиента на форме создания/редактирования: по одному инпуту
 * на каждое определение [definitions], значения — в [values].
 */
export function ClientCustomFieldsFields({
  definitions,
  values,
  onChange,
  disabled,
}: {
  readonly definitions: readonly CustomFieldDefinition[];
  readonly values: readonly CustomFieldValue[];
  readonly onChange: (values: readonly CustomFieldValue[]) => void;
  readonly disabled: boolean;
}) {
  const { t } = useI18n();
  if (definitions.length === 0) {
    return null;
  }
  return (
    <div className="space-y-4 border-t pt-4">
      <h2 className="text-sm font-medium text-muted-foreground">{t("clients.additionalFields")}</h2>
      {definitions.map((def) => (
        <CustomFieldInput
          key={def.fieldKey}
          def={def}
          values={values}
          onChange={onChange}
          disabled={disabled}
        />
      ))}
    </div>
  );
}

function CustomFieldInput({
  def,
  values,
  onChange,
  disabled,
}: {
  readonly def: CustomFieldDefinition;
  readonly values: readonly CustomFieldValue[];
  readonly onChange: (values: readonly CustomFieldValue[]) => void;
  readonly disabled: boolean;
}) {
  const { t } = useI18n();
  const current = customFieldValueOf(values, def.fieldKey);

  switch (def.fieldType) {
    case "text":
    case "phone":
    case "email":
    case "url": {
      const value = current?.type === "text" ? current.value : "";
      return (
        <TextField
          field={manualField(def.fieldKey, value, (next) => {
            onChange(
              withCustomFieldValue(values, { type: "text", fieldKey: def.fieldKey, value: next }),
            );
          })}
          label={def.label}
          type={def.fieldType === "email" ? "email" : def.fieldType === "phone" ? "tel" : "text"}
          autoComplete="off"
          disabled={disabled}
        />
      );
    }

    case "number": {
      const value = current?.type === "number" ? current.value : null;
      return (
        <TextField
          field={manualField(def.fieldKey, value === null ? "" : value.toString(), (next) => {
            if (next.trim() === "") {
              onChange(withoutCustomFieldValue(values, def.fieldKey));
              return;
            }
            const parsed = Number(next);
            if (!Number.isNaN(parsed)) {
              onChange(
                withCustomFieldValue(values, {
                  type: "number",
                  fieldKey: def.fieldKey,
                  value: parsed,
                }),
              );
            }
          })}
          label={def.label}
          inputMode="decimal"
          autoComplete="off"
          disabled={disabled}
        />
      );
    }

    case "boolean": {
      const value = current?.type === "bool" && current.value;
      return (
        <CheckboxField
          field={manualField(def.fieldKey, value, (next) => {
            onChange(
              withCustomFieldValue(values, { type: "bool", fieldKey: def.fieldKey, value: next }),
            );
          })}
          label={def.label}
        />
      );
    }

    case "date": {
      const value = current?.type === "date" ? current.value : "";
      return (
        <TextField
          field={manualField(def.fieldKey, value, (next) => {
            if (next === "") {
              onChange(withoutCustomFieldValue(values, def.fieldKey));
              return;
            }
            onChange(
              withCustomFieldValue(values, {
                type: "date",
                fieldKey: def.fieldKey,
                value: LocalDateSchema.parse(next),
              }),
            );
          })}
          label={def.label}
          type="date"
          disabled={disabled}
        />
      );
    }

    case "select": {
      const value = current?.type === "select" ? current.value : NO_SELECTION;
      const options: readonly SelectOption<string>[] = [
        { value: NO_SELECTION, label: t("clients.selectOptionNone") },
        ...def.options.map((option) => ({ value: option, label: option })),
      ];
      return (
        <SelectField
          field={manualField(def.fieldKey, value, (next) => {
            onChange(
              next === NO_SELECTION
                ? withoutCustomFieldValue(values, def.fieldKey)
                : withCustomFieldValue(values, {
                    type: "select",
                    fieldKey: def.fieldKey,
                    value: next,
                  }),
            );
          })}
          label={def.label}
          options={options}
        />
      );
    }
  }
}
