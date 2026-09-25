import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import type { CustomFieldDefinition } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, SelectField, TextAreaField, TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import {
  CUSTOM_FIELD_TYPES,
  customFieldSchema,
  definitionOf,
  EMPTY_CUSTOM_FIELD,
  formValuesOf,
} from "./customFieldForm";

/** Свойства диалога определения дополнительного поля. */
interface CustomFieldDialogProps {
  /** Редактируемое определение; `null` — новое. Ключ существующего поля не меняется. */
  readonly initial: CustomFieldDefinition | null;
  /** Ключи остальных полей: новый ключ не должен с ними совпадать. */
  readonly takenKeys: ReadonlySet<string>;
  /** Сохраняет определение; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (definition: CustomFieldDefinition) => Promise<string | null>;
  /** Закрывает диалог. */
  readonly onClose: () => void;
}

/**
 * Диалог создания или изменения дополнительного атрибута клиента: ключ, название, тип,
 * признаки и параметры типа — опции выбора, пределы числа, длина строки.
 */
export function CustomFieldDialog({ initial, takenKeys, onSave, onClose }: CustomFieldDialogProps) {
  const { t } = useI18n();
  const schema = useMemo(() => customFieldSchema(t, takenKeys), [t, takenKeys]);
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm({
    defaultValues: initial === null ? EMPTY_CUSTOM_FIELD : formValuesOf(initial),
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const definition = definitionOf(value);
      if (definition === null) {
        return;
      }
      setFailure(null);
      const error = await onSave(definition);
      if (error === null) {
        onClose();
      } else {
        setFailure(error);
      }
    },
  });
  const typeOptions = CUSTOM_FIELD_TYPES.map((type) => ({
    value: type,
    label: t(`customFields.type.${type}`),
  }));

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent
        aria-describedby={undefined}
        closeLabel={t("action.close")}
        className="max-h-[calc(100dvh-2rem)] overflow-y-auto"
      >
        <DialogHeader>
          <DialogTitle>
            {initial === null ? t("customFields.create") : t("customFields.edit")}
          </DialogTitle>
        </DialogHeader>
        <form
          method="post"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void form.handleSubmit();
          }}
          className="space-y-4"
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="fieldKey">
            {(field) => (
              <TextField
                field={field}
                label={t("customFields.key")}
                hint={t("customFields.keyHint")}
                autoComplete="off"
                autoCapitalize="none"
                spellCheck={false}
                readOnly={initial !== null}
                required
              />
            )}
          </form.Field>
          <form.Field name="label">
            {(field) => (
              <TextField
                field={field}
                label={t("customFields.label")}
                autoComplete="off"
                required
              />
            )}
          </form.Field>
          <form.Field name="fieldType">
            {(field) => (
              <SelectField field={field} label={t("customFields.type")} options={typeOptions} />
            )}
          </form.Field>
          <div className="space-y-2">
            <form.Field name="isRequired">
              {(field) => <CheckboxField field={field} label={t("customFields.required")} />}
            </form.Field>
            <form.Field name="isSearchable">
              {(field) => <CheckboxField field={field} label={t("customFields.searchable")} />}
            </form.Field>
            <form.Field name="isSortable">
              {(field) => <CheckboxField field={field} label={t("customFields.sortable")} />}
            </form.Field>
          </div>
          <form.Subscribe selector={(state) => state.values.fieldType}>
            {(fieldType) => (
              <>
                {fieldType === "select" && (
                  <form.Field name="options">
                    {(field) => (
                      <TextAreaField
                        field={field}
                        label={t("customFields.options")}
                        hint={t("customFields.optionsHint")}
                      />
                    )}
                  </form.Field>
                )}
                {fieldType === "number" && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <form.Field name="minValue">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("customFields.minValue")}
                          inputMode="numeric"
                          autoComplete="off"
                        />
                      )}
                    </form.Field>
                    <form.Field name="maxValue">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("customFields.maxValue")}
                          inputMode="numeric"
                          autoComplete="off"
                        />
                      )}
                    </form.Field>
                  </div>
                )}
                {fieldType === "text" && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <form.Field name="minLength">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("customFields.minLength")}
                          inputMode="numeric"
                          autoComplete="off"
                        />
                      )}
                    </form.Field>
                    <form.Field name="maxLength">
                      {(field) => (
                        <TextField
                          field={field}
                          label={t("customFields.maxLength")}
                          inputMode="numeric"
                          autoComplete="off"
                        />
                      )}
                    </form.Field>
                  </div>
                )}
              </>
            )}
          </form.Subscribe>
          <DialogFooter closeLabel={t("action.cancel")}>
            <form.Subscribe selector={(state) => state.isSubmitting}>
              {(submitting) => (
                <Button type="submit" disabled={submitting} aria-busy={submitting}>
                  {t("action.save")}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
