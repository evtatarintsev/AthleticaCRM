import { useForm } from "@tanstack/react-form";
import { useMemo, useState } from "react";
import { z } from "zod";
import {
  UserPermissionSchema,
  type RoleItem,
  type UserPermission,
} from "@/api/generated/contracts";
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

/** Название и права роли из формы. */
export interface RoleTerms {
  readonly name: string;
  readonly permissions: readonly UserPermission[];
}

/** Свойства диалога роли. */
interface RoleDialogProps {
  /** Редактируемая роль; `null` — новая. */
  readonly initial: RoleItem | null;
  /** Сохраняет роль; возвращает текст ошибки или `null` при успехе. */
  readonly onSave: (terms: RoleTerms) => Promise<string | null>;
  /** Закрывает диалог. */
  readonly onClose: () => void;
}

/** Диалог создания или изменения роли: название и набор прав с пояснениями. */
export function RoleDialog({ initial, onSave, onClose }: RoleDialogProps) {
  const { t } = useI18n();
  const nameSchema = useMemo(() => z.string().trim().min(1, t("error.required")), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const [permissions, setPermissions] = useState<ReadonlySet<UserPermission>>(
    new Set(initial?.permissions ?? []),
  );
  const form = useForm({
    defaultValues: { name: initial?.name ?? "" },
    onSubmit: async ({ value }) => {
      const name = nameSchema.safeParse(value.name);
      if (!name.success) {
        return;
      }
      setFailure(null);
      const error = await onSave({
        name: name.data,
        permissions: UserPermissionSchema.options.filter((p) => permissions.has(p)),
      });
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
      <DialogContent
        aria-describedby={undefined}
        closeLabel={t("action.close")}
        className="max-h-[calc(100dvh-2rem)] overflow-y-auto"
      >
        <DialogHeader>
          <DialogTitle>{initial === null ? t("roles.create") : t("roles.edit")}</DialogTitle>
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
          <form.Field name="name" validators={{ onSubmit: nameSchema }}>
            {(field) => (
              <TextField field={field} label={t("roles.name")} autoComplete="off" required />
            )}
          </form.Field>
          <fieldset className="space-y-1">
            <legend className="mb-1 text-sm font-medium">{t("roles.permissions")}</legend>
            {UserPermissionSchema.options.map((permission) => (
              <label
                key={permission}
                className="flex cursor-pointer items-start gap-3 rounded-md p-2 hover:bg-accent/50"
              >
                <input
                  type="checkbox"
                  checked={permissions.has(permission)}
                  onChange={(event) => {
                    const next = new Set(permissions);
                    if (event.target.checked) {
                      next.add(permission);
                    } else {
                      next.delete(permission);
                    }
                    setPermissions(next);
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
