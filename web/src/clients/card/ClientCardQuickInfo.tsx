import type { ReactNode } from "react";
import type {
  ClientAttendanceItemSchema,
  MembershipSchema,
  Money,
  ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";

/**
 * Быстрая информация в шапке карточки: баланс, действующий абонемент, последнее посещение
 * и ближайшее занятие из данных сервера; статус, ответственный и скидка — заглушки до появления
 * этих полей в анкете. `undefined` во входных данных — значение ещё загружается.
 */
export function ClientCardQuickInfo({
  balance,
  memberships,
  lastVisit,
  nextSession,
}: {
  readonly balance: Money;
  readonly memberships: readonly MembershipSchema[] | undefined;
  /** Последнее посещение; `null` — посещений не было. */
  readonly lastVisit: ClientAttendanceItemSchema | null | undefined;
  /** Ближайшее занятие; `null` — занятий нет. */
  readonly nextSession: ScheduleSessionSchema | null | undefined;
}) {
  const { t, format } = useI18n();
  const active = memberships?.find((membership) => membership.status === "ACTIVE");

  return (
    <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-6">
      <Tile label={t("clientCard.balance")}>
        <span
          className={cn(
            "text-lg font-semibold tabular-nums",
            balance.minorUnits < 0 && "text-destructive",
          )}
        >
          {format.money(balance)}
        </span>
      </Tile>
      <Tile label={t("clientCard.quick.subscription")}>
        {memberships === undefined ? (
          <Skeleton className="h-5 w-24" />
        ) : active === undefined ? (
          <Muted>{t("clientCard.quick.noSubscription")}</Muted>
        ) : (
          <>
            <span className="block truncate font-medium" title={active.name}>
              {active.name}
            </span>
            <Sub>
              {active.sessionsTotal === null
                ? t("clientCard.quick.until", { date: format.date(active.endDate) })
                : t("clients.detail.subscriptionSessions", {
                    remaining: active.sessionsRemaining ?? 0,
                    total: active.sessionsTotal,
                  })}
            </Sub>
          </>
        )}
      </Tile>
      <Tile label={t("clientCard.quick.lastVisit")}>
        {lastVisit === undefined ? (
          <Skeleton className="h-5 w-24" />
        ) : lastVisit === null ? (
          <Muted>{t("clientCard.quick.never")}</Muted>
        ) : (
          <>
            <span className="block font-medium">{format.date(lastVisit.date)}</span>
            <Sub>{lastVisit.group.name}</Sub>
          </>
        )}
      </Tile>
      <Tile label={t("clientCard.quick.nextSession")}>
        {nextSession === undefined ? (
          <Skeleton className="h-5 w-24" />
        ) : nextSession === null ? (
          <Muted>{t("clientCard.quick.none")}</Muted>
        ) : (
          <>
            <span className="block font-medium">
              {format.date(nextSession.date)}, {format.time(nextSession.startTime)}
            </span>
            <Sub>{nextSession.group.name}</Sub>
          </>
        )}
      </Tile>
      <Tile label={t("clientCard.quick.status")} stub>
        <Muted>{t("clientCard.quick.stub")}</Muted>
      </Tile>
      <Tile label={t("clientCard.quick.responsible")} stub>
        <Muted>{t("clientCard.quick.stub")}</Muted>
      </Tile>
    </dl>
  );
}

/** Плитка быстрой информации; [stub] — поле ещё не поддержано, рамка пунктиром. */
function Tile({
  label,
  stub = false,
  children,
}: {
  readonly label: string;
  readonly stub?: boolean;
  readonly children: ReactNode;
}) {
  const { t } = useI18n();
  return (
    <div
      className={cn(
        "min-w-0 rounded-md border bg-background/40 px-3 py-2",
        stub && "border-dashed",
      )}
      title={stub ? t("clientCard.quick.stubHint") : undefined}
    >
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 min-w-0 text-sm">{children}</dd>
    </div>
  );
}

/** Вторая строка значения плитки. */
function Sub({ children }: { readonly children: ReactNode }) {
  return <span className="block truncate text-xs text-muted-foreground">{children}</span>;
}

/** Приглушённое значение: «нет», «не было», заглушка. */
function Muted({ children }: { readonly children: ReactNode }) {
  return <span className="text-muted-foreground">{children}</span>;
}
