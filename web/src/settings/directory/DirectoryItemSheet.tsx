import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import { z } from "zod";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Свойства панели записи справочника. */
interface DirectoryItemSheetProps {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Заголовок: создание или редактирование. */
  readonly title: string;
  /** Название записи при открытии; пустое — новая запись. */
  readonly initialName: string;
  /** Сохраняет обрезанное название; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (name: string) => Promise<string | null>;
}

/**
 * Панель создания или переименования записи справочника с одним полем «Название».
 * Открывается поверх панели справочника.
 */
export function DirectoryItemSheet({
  open,
  onOpenChange,
  title,
  initialName,
  onSave,
}: DirectoryItemSheetProps) {
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={title}>
      <DirectoryItemForm initialName={initialName} onSave={onSave} />
    </EditSheet>
  );
}

/** Форма записи справочника; после успешного сохранения закрывает свою панель. */
function DirectoryItemForm({
  initialName,
  onSave,
}: Pick<DirectoryItemSheetProps, "initialName" | "onSave">) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const schema = useMemo(() => z.string().trim().min(1, t("error.required")), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm({
    defaultValues: { name: initialName },
    onSubmit: async ({ value }) => {
      const name = schema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const error = await onSave(name.data);
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
          <form.Field name="name" validators={{ onSubmit: schema }}>
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
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
