import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import type { EmployeeId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormAlert } from "@/forms/FormAlert";
import { PasswordField, TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";

/** Значения формы отправки доступа. */
interface SendAccessFormValues {
  readonly email: string;
  readonly password: string;
}

/** Схема формы: email обязателен и валиден, пароль не пуст. */
function sendAccessSchema(t: I18n["t"]) {
  return z.object({
    email: z
      .string()
      .trim()
      .pipe(z.email(t("error.invalidEmail"))),
    password: z.string().min(1, t("error.required")),
  });
}

/** Свойства диалога отправки доступа. */
interface SendAccessDialogProps {
  readonly api: ApiClient;
  readonly employeeId: EmployeeId;
  /** Email сотрудника из карточки — предзаполняет поле; `null`, если email не указан. */
  readonly defaultEmail: string | null;
  /** Вызывается после успешной отправки. */
  readonly onSuccess: () => void;
  /** Закрывает диалог без отправки. */
  readonly onClose: () => void;
}

/**
 * Диалог отправки доступа сотруднику: email (предзаполнен из карточки) и пароль,
 * которые сервер отправит сотруднику письмом. Отправляет `employees/send-access`.
 */
export function SendAccessDialog({
  api,
  employeeId,
  defaultEmail,
  onSuccess,
  onClose,
}: SendAccessDialogProps) {
  const { t } = useI18n();
  const schema = useMemo(() => sendAccessSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);

  const defaultValues: SendAccessFormValues = { email: defaultEmail ?? "", password: "" };
  const form = useForm({
    defaultValues,
    validators: { onSubmit: schema },
    onSubmit: async ({ value }) => {
      const parsed = schema.safeParse(value);
      if (!parsed.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("employees/send-access", {
        employeeId,
        email: parsed.data.email,
        password: parsed.data.password,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      onSuccess();
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
          <DialogTitle>{t("employees.sendAccess")}</DialogTitle>
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
          <form.Field name="email">
            {(field) => (
              <TextField
                field={field}
                label={t("employees.sendAccessEmail")}
                type="email"
                autoComplete="off"
                required
              />
            )}
          </form.Field>
          <form.Field name="password">
            {(field) => (
              <PasswordField
                field={field}
                label={t("employees.sendAccessPassword")}
                autoComplete="new-password"
                required
              />
            )}
          </form.Field>
          <DialogFooter closeLabel={t("action.cancel")}>
            <form.Subscribe selector={(state) => state.isSubmitting}>
              {(submitting) => (
                <Button type="submit" disabled={submitting} aria-busy={submitting}>
                  {t("employees.sendAccess")}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
