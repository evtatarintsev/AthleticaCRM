import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import { z } from "zod";
import {
  UserPermissionSchema,
  type RoleItem,
  type UserPermission,
} from "@/api/generated/contracts";
import { FormAlert } from "@/forms/FormAlert";
import { TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";

/** Название и права роли из формы. */
export interface RoleTerms {
  readonly name: string;
  readonly permissions: readonly UserPermission[];
}

/** Свойства панели роли. */
interface RoleSheetProps {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Редактируемая роль; `null` — новая. */
  readonly initial: RoleItem | null;
  /** Сохраняет роль; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (terms: RoleTerms) => Promise<string | null>;
}

/**
 * Панель создания или изменения роли: название и набор прав с пояснениями. Открывается поверх
 * списка ролей в настройках или поверх панели сотрудника; что делать с сохранённой ролью,
 * решает владелец панели через [RoleSheetProps.onSave].
 */
export function RoleSheet({ open, onOpenChange, initial, onSave }: RoleSheetProps) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={open}
      onOpenChange={onOpenChange}
      title={initial === null ? t("roles.create") : t("roles.edit")}
    >
      <RoleForm initial={initial} onSave={onSave} />
    </EditSheet>
  );
}

/**
 * Форма роли. Права — поле формы наравне с названием, поэтому их изменение тоже защищено
 * подтверждением при закрытии. После сохранения закрывает свою панель.
 */
function RoleForm({ initial, onSave }: Pick<RoleSheetProps, "initial" | "onSave">) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const nameSchema = useMemo(() => z.string().trim().min(1, t("error.required")), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const defaultPermissions: readonly UserPermission[] = initial?.permissions ?? [];
  const form = useForm({
    defaultValues: { name: initial?.name ?? "", permissions: defaultPermissions },
    onSubmit: async ({ value }) => {
      const name = nameSchema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const error = await onSave({
        name: name.data,
        permissions: UserPermissionSchema.options.filter((p) => value.permissions.includes(p)),
      });
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
              <TextField field={field} label={t("roles.name")} autoComplete="off" required />
            )}
          </form.Field>
          <form.Field name="permissions">
            {(field) => (
              <fieldset className="space-y-1">
                <legend className="mb-1 text-sm font-medium">{t("roles.permissions")}</legend>
                {UserPermissionSchema.options.map((permission) => (
                  <label
                    key={permission}
                    className="flex cursor-pointer items-start gap-3 rounded-md p-2 hover:bg-accent/50"
                  >
                    <input
                      type="checkbox"
                      checked={field.state.value.includes(permission)}
                      onChange={(event) => {
                        field.handleChange(
                          event.target.checked
                            ? [...field.state.value, permission]
                            : field.state.value.filter((p) => p !== permission),
                        );
                      }}
                      className="mt-0.5 size-4 shrink-0 accent-primary"
                    />
                    <span className="space-y-0.5">
                      <span className="block text-sm font-medium">
                        {t(`permission.${permission}.name`)}
                      </span>
                      <span className="block text-xs text-muted-foreground">
                        {t(`permission.${permission}.description`)}
                      </span>
                    </span>
                  </label>
                ))}
              </fieldset>
            )}
          </form.Field>
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}
