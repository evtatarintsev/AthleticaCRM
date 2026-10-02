import { useForm } from "@tanstack/react-form";
import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import type { UploadId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n, type I18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { sessionQuery } from "@/query/queries";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { AvatarPicker } from "./AvatarPicker";
import { ChangePasswordSheet } from "./ChangePasswordSheet";

/** Значения формы профиля. */
interface ProfileFormValues {
  readonly name: string;
  readonly avatarId: UploadId | null;
}

/**
 * Схема имени с сообщением на языке [t]; имя обрезается по краям. Проверяется на уровне поля:
 * бренд `UploadId` есть только у выхода схемы, поэтому схема всей формы с аватаром не подходит
 * TanStack Form по типу входа.
 */
function nameSchema(t: I18n["t"]) {
  return z.string().trim().min(1, t("error.required"));
}

/** Панель профиля текущего пользователя, открытая при [open]. */
export function ProfileSheet({
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
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("profile.title")}>
      <ProfileForm api={api} />
    </EditSheet>
  );
}

/**
 * Форма профиля: имя и аватар. После сохранения сессия перечитывается, новое имя с аватаром
 * сразу видны в меню аккаунта, а панель закрывается. Из формы поверх неё открывается смена пароля.
 */
function ProfileForm({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close } = useEditSheet();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const schema = useMemo(() => nameSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const [passwordOpen, setPasswordOpen] = useState(false);

  const defaultValues: ProfileFormValues = { name: me.name, avatarId: me.avatarId };
  const form = useForm({
    defaultValues,
    onSubmit: async ({ value }) => {
      const name = schema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const result = await api.call("auth/me/update", {
        name: name.data,
        avatarId: value.avatarId,
      });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      await queryClient.invalidateQueries({ queryKey: sessionQuery(api).queryKey });
      toast.success(t("profile.saved"));
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
          nested={
            <ChangePasswordSheet api={api} open={passwordOpen} onOpenChange={setPasswordOpen} />
          }
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="avatarId">
            {(field) => (
              <AvatarPicker
                api={api}
                value={field.state.value}
                name={me.name}
                disabled={submitting}
                onChange={(id) => {
                  field.handleChange(id);
                }}
              />
            )}
          </form.Field>
          <form.Field name="name" validators={{ onSubmit: schema }}>
            {(field) => (
              <TextField field={field} label={t("profile.name")} autoComplete="name" required />
            )}
          </form.Field>
          <Button
            type="button"
            variant="link"
            className="px-0"
            onClick={() => {
              setPasswordOpen(true);
            }}
          >
            {t("profile.changePassword")}
          </Button>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
