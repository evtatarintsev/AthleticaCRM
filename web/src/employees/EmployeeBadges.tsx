import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";

/** Бейдж «Владелец» рядом с именем сотрудника-владельца организации. */
export function OwnerBadge() {
  const { t } = useI18n();
  return (
    <span className="shrink-0 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
      {t("employees.owner")}
    </span>
  );
}

/** Статус активности сотрудника: активен или неактивен. */
export function StatusBadge({ active }: { active: boolean }) {
  const { t } = useI18n();
  return (
    <span
      className={cn(
        "shrink-0 rounded px-1.5 py-0.5 text-xs font-medium",
        active ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground",
      )}
    >
      {active ? t("employees.statusActive") : t("employees.statusInactive")}
    </span>
  );
}

/** Ярлык роли сотрудника с названием [name]. */
export function RoleChip({ name }: { name: string }) {
  return (
    <span className="shrink-0 rounded-full border px-2 py-0.5 text-xs text-muted-foreground">
      {name}
    </span>
  );
}
