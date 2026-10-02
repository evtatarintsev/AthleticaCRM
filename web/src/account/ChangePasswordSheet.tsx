import { useForm } from "@tanstack/react-form";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import { FormAlert } from "@/forms/FormAlert";
import { PasswordField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { sessionQuery } from "@/query/queries";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Значения формы смены пароля. */
interface ChangePasswordFormValues {
  readonly oldPassword: string;
  readonly newPassword: string;
  readonly confirmPassword: string;
}

/** Схема формы смены пароля с сообщениями на языке [t]: поля заполнены, новый пароль повторён. */
function changePasswordSchema(t: I18n["t"]) {
  return z
    .object({
      oldPassword: z.string().min(1, t("error.required")),
      newPassword: z.string().min(1, t("error.required")),
      confirmPassword: z.string().min(1, t("error.required")),
    })
    .refine((value) => value.newPassword === value.confirmPassword, {
      path: ["confirmPassword"],
      message: t("error.passwordsDontMatch"),
    });
}

/** Панель смены пароля текущего пользователя, открытая при [open]. */
export function ChangePasswordSheet({
  api,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("password.title")}>
      <ChangePasswordForm api={api} />
    </EditSheet>
  );
}

/**
 * Форма смены пароля. Поля размечены `current-password` / `new-password`, а скрытое поле
 * логина подсказывает менеджеру паролей, для какой учётной записи сохранить пароль.
 * После смены пароля панель закрывается.
 */
function ChangePasswordForm({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const schema = useMemo(() => changePasswordSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: ChangePasswordFormValues = {
    oldPassword: "",
    newPassword: "",
    confirmPassword: "",
  };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("auth/me/change-password", {
        oldPassword: parsed.data.oldPassword,
        newPassword: parsed.data.newPassword,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      toast.success(t("password.changed"));
      close();
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
          <input
            type="text"
            name="username"
            autoComplete="username"
            value={me.username}
            readOnly
            hidden
          />
          <form.Field name="oldPassword">
            {(field) => (
              <PasswordField
                field={field}
                label={t("password.current")}
                autoComplete="current-password"
                required
              />
            )}
          </form.Field>
          <form.Field name="newPassword">
            {(field) => (
              <PasswordField
                field={field}
                label={t("password.new")}
                autoComplete="new-password"
                required
              />
            )}
          </form.Field>
          <form.Field name="confirmPassword">
            {(field) => (
              <PasswordField
                field={field}
                label={t("password.confirm")}
                autoComplete="new-password"
                required
              />
            )}
          </form.Field>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
