import { useState, type ReactNode } from "react";
import type { ApiResult } from "@/api/client";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Сохранение финансовой формы: ошибка сохраняет поля, успех закрывает панель. */
export function FinanceForm({
  children,
  dirty,
  submit,
  onSaved,
}: {
  readonly children: ReactNode;
  readonly dirty: boolean;
  readonly submit: () => Promise<ApiResult<unknown> | null>;
  readonly onSaved: () => Promise<void> | void;
}) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const save = async () => {
    setPending(true);
    const result = await submit();
    if (result === null) {
      setPending(false);
      setError(t("error.required"));
      return;
    }
    if (!result.ok) {
      setPending(false);
      setError(apiErrorMessage(t, result.error));
      return;
    }
    await onSaved();
    setPending(false);
    close();
  };
  return (
    <EditSheetForm
      dirty={dirty}
      submitting={pending}
      onSubmit={() => {
        void save();
      }}
    >
      {error !== null && <FormAlert message={error} />}
      {children}
    </EditSheetForm>
  );
}
