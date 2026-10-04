import { useState } from "react";
import { UserPermissionSchema, type UserPermission } from "@/api/generated/contracts";
import { Input } from "@/components/ui/input";
import { useI18n } from "@/i18n/context";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import type { PermissionOverride, PermissionOverrides } from "./employeeForm";

/** Свойства панели прав сотрудника. */
interface PermissionsSheetProps {
  /** Открыта ли панель. */
  readonly open: boolean;
  /** Вызывается, когда панель надо открыть или закрыть. */
  readonly onOpenChange: (open: boolean) => void;
  /** Текущие решения по правам из формы; с них начинается черновик. */
  readonly value: PermissionOverrides;
  /** Права, которые дают роли, выбранные в форме; такие права помечаются. */
  readonly rolePermissions: ReadonlySet<UserPermission>;
  /** Применение черновика кнопкой «Готово». */
  readonly onApply: (value: PermissionOverrides) => void;
}

/** Состояние права в панели: «по роли» или явное решение. */
type PermissionState = "inherit" | PermissionOverride;

/** Порядок состояний в переключателе. */
const STATES: readonly PermissionState[] = ["inherit", "grant", "revoke"];

/**
 * Панель справа со всеми правами системы: у каждого права переключатель «По роли» /
 * «Выдано» / «Отозвано», пометка права из выбранных ролей [PermissionsSheetProps.rolePermissions]
 * и поиск по названию и описанию. «Готово» отдаёт решения в `onApply`, закрытие без него
 * ничего не меняет; изменённые решения защищены подтверждением.
 */
export function PermissionsSheet({ open, onOpenChange, ...rest }: PermissionsSheetProps) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("employees.sectionPermissions")}>
      <PermissionsContent {...rest} />
    </EditSheet>
  );
}

/** Содержимое панели; монтируется заново при каждом открытии. */
function PermissionsContent({
  value,
  rolePermissions,
  onApply,
}: Omit<PermissionsSheetProps, "open" | "onOpenChange">) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [draft, setDraft] = useState<PermissionOverrides>(value);
  const [query, setQuery] = useState("");

  const dirty = UserPermissionSchema.options.some((p) => draft[p] !== value[p]);

  const needle = query.trim().toLocaleLowerCase();
  const visible = UserPermissionSchema.options.filter(
    (permission) =>
      needle === "" ||
      t(`permission.${permission}.name`).toLocaleLowerCase().includes(needle) ||
      t(`permission.${permission}.description`).toLocaleLowerCase().includes(needle),
  );

  const change = (permission: UserPermission, state: PermissionState) => {
    const next: Partial<Record<UserPermission, PermissionOverride>> = {};
    UserPermissionSchema.options.forEach((p) => {
      const value = p === permission ? state : (draft[p] ?? "inherit");
      if (value !== "inherit") {
        next[p] = value;
      }
    });
    setDraft(next);
  };

  const stateLabel = (state: PermissionState): string => {
    switch (state) {
      case "inherit":
        return t("employees.permissionInherit");
      case "grant":
        return t("employees.permissionGrant");
      case "revoke":
        return t("employees.permissionRevoke");
    }
  };

  return (
    <EditSheetForm
      dirty={dirty}
      submitting={false}
      submitLabel={t("action.done")}
      onSubmit={() => {
        onApply(draft);
        close();
      }}
    >
      <Input
        type="search"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value);
        }}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            event.preventDefault();
          }
        }}
        placeholder={t("employees.searchPermissions")}
        aria-label={t("employees.searchPermissions")}
      />
      {visible.length === 0 ? (
        <p className="py-6 text-center text-sm text-muted-foreground">{t("picker.nothingFound")}</p>
      ) : (
        <ul className="divide-y">
          {visible.map((permission) => {
            const name = t(`permission.${permission}.name`);
            const current: PermissionState = draft[permission] ?? "inherit";
            return (
              <li key={permission} className="space-y-2 py-3">
                <div className="space-y-0.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-medium">{name}</span>
                    {rolePermissions.has(permission) && (
                      <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-secondary-foreground">
                        {t("employees.permissionFromRole")}
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {t(`permission.${permission}.description`)}
                  </p>
                </div>
                <div
                  role="radiogroup"
                  aria-label={name}
                  className="inline-flex rounded-md border p-0.5"
                >
                  {STATES.map((state) => (
                    <label
                      key={state}
                      className="cursor-pointer rounded px-3 py-1 text-xs font-medium text-muted-foreground has-checked:bg-primary has-checked:text-primary-foreground has-focus-visible:ring-[3px] has-focus-visible:ring-ring/50"
                    >
                      <input
                        type="radio"
                        name={`permission-${permission}`}
                        value={state}
                        checked={current === state}
                        onChange={() => {
                          change(permission, state);
                        }}
                        className="sr-only"
                      />
                      {stateLabel(state)}
                    </label>
                  ))}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </EditSheetForm>
  );
}
