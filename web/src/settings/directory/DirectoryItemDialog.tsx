import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";

/** Свойства диалога записи справочника. */
interface DirectoryItemDialogProps {
  /** Заголовок: создание или редактирование. */
  readonly title: string;
  /** Название записи при открытии; пустое — новая запись. */
  readonly initialName: string;
  /** Сохраняет обрезанное название; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (name: string) => Promise<string | null>;
  /** Закрывает диалог. */
  readonly onClose: () => void;
}

/**
 * Диалог создания или переименования записи справочника с одним полем «Название».
 * Монтируется на время редактирования, поэтому каждый раз открывается с чистой формой.
 */
export function DirectoryItemDialog({
  title,
  initialName,
  onSave,
  onClose,
}: DirectoryItemDialogProps) {
  const { t } = useI18n();
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
        onClose();
      } else {
        setFailure(error);
      }
    },
  });
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent aria-describedby={undefined} closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
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
