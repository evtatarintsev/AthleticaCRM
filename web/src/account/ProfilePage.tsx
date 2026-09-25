import { useForm } from "@tanstack/react-form";
import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
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
import { PageHeader } from "@/ui/PageHeader";
import { AvatarPicker } from "./AvatarPicker";

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

/**
 * Профиль текущего пользователя: имя и аватар. После сохранения сессия перечитывается,
 * и новое имя с аватаром сразу видны в меню аккаунта.
 */
export function ProfilePage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const schema = useMemo(() => nameSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);

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
    },
  });

  return (
    <section className="max-w-md">
      <PageHeader title={t("profile.title")} />
      <form
        method="post"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void form.handleSubmit();
        }}
        className="space-y-6"
      >
        {failure !== null && <FormAlert message={failure} />}
        <form.Subscribe selector={(state) => state.isSubmitting}>
          {(submitting) => (
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
          )}
        </form.Subscribe>
        <form.Field name="name" validators={{ onSubmit: schema }}>
          {(field) => (
            <TextField field={field} label={t("profile.name")} autoComplete="name" required />
          )}
        </form.Field>
        <div className="flex flex-wrap items-center gap-3">
          <form.Subscribe selector={(state) => state.isSubmitting}>
            {(submitting) => (
              <Button type="submit" disabled={submitting} aria-busy={submitting}>
                {t("action.save")}
              </Button>
            )}
          </form.Subscribe>
          <Button variant="link" asChild>
            <Link to="/settings/change-password">{t("profile.changePassword")}</Link>
          </Button>
        </div>
      </form>
    </section>
  );
}
