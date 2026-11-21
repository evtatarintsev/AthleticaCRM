import type { EmployeeAccessStatus, Instant } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";

/** Бейдж «Владелец» на месте ролей сотрудника-владельца организации. */
export function OwnerBadge() {
  const { t } = useI18n();
  return (
    <span className="shrink-0 rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
      {t("employees.owner")}
    </span>
  );
}

/** Статус доступа сотрудника [status]: без доступа, не входил, активен или заблокирован. */
export function StatusBadge({ status }: { status: EmployeeAccessStatus }) {
  const { t } = useI18n();
  const [label, tone] = statusView(status);
  return (
    <span className={cn("shrink-0 rounded px-1.5 py-0.5 text-xs font-medium", tone)}>
      {t(label)}
    </span>
  );
}

/** Ключ подписи и цвет бейджа для статуса [status]. */
function statusView(
  status: EmployeeAccessStatus,
): readonly [
  (
    | "employees.statusNoAccess"
    | "employees.statusInvited"
    | "employees.statusActive"
    | "employees.statusBlocked"
  ),
  string,
] {
  switch (status) {
    case "NO_ACCESS":
      return ["employees.statusNoAccess", "bg-muted text-muted-foreground"];
    case "INVITED":
      return ["employees.statusInvited", "bg-muted text-foreground"];
    case "ACTIVE":
      return ["employees.statusActive", "bg-primary/10 text-primary"];
    case "BLOCKED":
      return ["employees.statusBlocked", "bg-destructive/10 text-destructive"];
  }
}

/**
 * Последняя активность сотрудника: отметка «В сети» при [online], иначе относительное время
 * [lastSeenAt] с префиксом [withPrefix] («Был(а) в сети …»); без активности — ничего.
 */
export function LastSeen({
  online,
  lastSeenAt,
  withPrefix,
}: {
  online: boolean;
  lastSeenAt: Instant | null;
  withPrefix: boolean;
}) {
  const { t, format } = useI18n();
  if (online) {
    return (
      <span className="inline-flex shrink-0 items-center gap-1.5 text-xs font-medium text-primary">
        <span aria-hidden className="size-2 rounded-full bg-primary" />
        {t("employees.online")}
      </span>
    );
  }
  if (lastSeenAt === null) {
    return null;
  }
  const ago = format.ago(lastSeenAt, new Date());
  return (
    <span className="text-xs text-muted-foreground">
      {withPrefix ? t("employees.lastSeen", { ago }) : ago}
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
