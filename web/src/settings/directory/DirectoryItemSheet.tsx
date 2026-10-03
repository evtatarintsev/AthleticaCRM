import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import { z } from "zod";
import { FormAlert } from "@/forms/FormAlert";
import { TextAreaField, TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Наибольшая длина пояснения записи; совпадает с пределом на сервере. */
const DESCRIPTION_MAX_LENGTH = 500;

/** Значения формы записи справочника; пояснение пустое, если справочник его не поддерживает. */
export interface DirectoryItemValues {
  readonly name: string;
  readonly description: string;
}

/** Свойства панели записи справочника. */
interface DirectoryItemSheetProps {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Заголовок: создание или редактирование. */
  readonly title: string;
  /** Показывать ли поле «Пояснение». */
  readonly describable: boolean;
  /** Значения при открытии; пустое название — новая запись. */
  readonly initial: DirectoryItemValues;
  /** Сохраняет обрезанные значения; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (values: DirectoryItemValues) => Promise<string | null>;
}

/**
 * Панель создания или изменения записи справочника: поле «Название» и, у справочников
 * с пояснением, необязательное поле «Пояснение». Открывается поверх панели справочника.
 */
export function DirectoryItemSheet({
  open,
  onOpenChange,
  title,
  describable,
  initial,
  onSave,
}: DirectoryItemSheetProps) {
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={title}>
      <DirectoryItemForm describable={describable} initial={initial} onSave={onSave} />
    </EditSheet>
  );
}

/** Форма записи справочника; после успешного сохранения закрывает свою панель. */
function DirectoryItemForm({
  describable,
  initial,
  onSave,
}: Pick<DirectoryItemSheetProps, "describable" | "initial" | "onSave">) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const nameSchema = useMemo(() => z.string().trim().min(1, t("error.required")), [t]);
  const descriptionSchema = useMemo(
    () =>
      z
        .string()
        .trim()
        .max(
          DESCRIPTION_MAX_LENGTH,
          t("directory.descriptionTooLong", { max: DESCRIPTION_MAX_LENGTH }),
        ),
    [t],
  );
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm({
    defaultValues: { name: initial.name, description: initial.description },
    onSubmit: async ({ value }) => {
      const name = nameSchema.safeParse(value.name);
      const description = descriptionSchema.safeParse(value.description);
      if (!name.success || !description.success) {
        return;
      }
      setFailure(null);
      const error = await onSave({ name: name.data, description: description.data });
      if (error === null) {
        close();
      } else {
        setFailure(error);
      }
    },
  });
  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          onSubmit={() => {
            void form.handleSubmit();
          }}
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="name" validators={{ onSubmit: nameSchema }}>
            {(field) => (
              <TextField
                field={field}
                label={t("directory.name")}
                autoComplete="off"
                required
                autoFocus
              />
            )}
          </form.Field>
          {describable && (
            <form.Field name="description" validators={{ onSubmit: descriptionSchema }}>
              {(field) => (
                <TextAreaField
                  field={field}
                  label={t("directory.description")}
                  hint={t("directory.descriptionHint")}
                  rows={3}
                />
              )}
            </form.Field>
          )}
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
