import { Link } from "@tanstack/react-router";
import { attendanceSummary, currentMemberships } from "./clientOverview";
import { ChevronRightIcon } from "lucide-react";
import type { ReactNode } from "react";
import type {
  ClientAttendanceItemSchema,
  MembershipId,
  MembershipSchema,
  Money,
  ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { FreezeNote, MembershipTerms } from "../membership/MembershipParts";
import { isCurrentMembership, membershipView } from "../membership/membershipView";

/** Четыре плитки обзора; undefined означает загрузку. */
export function ClientCardQuickInfo({
  balance,
  memberships,
  attendance,
  nextSession,
  onBalanceClick,
  onMembershipOpen,
  onMemberships,
  onSessions,
}: {
  readonly balance: Money;
  readonly memberships: readonly MembershipSchema[] | undefined;
  readonly attendance: readonly ClientAttendanceItemSchema[] | undefined;
  readonly nextSession: ScheduleSessionSchema | null | undefined;
  readonly onBalanceClick: (() => void) | undefined;
  readonly onMembershipOpen: (id: MembershipId) => void;
  readonly onMemberships: () => void;
  readonly onSessions: () => void;
}) {
  const { t, format } = useI18n();
  const summary = attendanceSummary(attendance);
  const current = currentMemberships(memberships);
  return (
    <dl className="grid grid-cols-2 gap-2 min-[1000px]:grid-cols-4">
      <div className="relative col-span-2 sm:col-span-1">
        <MembershipTiles
          memberships={memberships === undefined ? undefined : current.slice(0, 1)}
          onOpen={onMembershipOpen}
        />
        {current.length > 1 && (
          <button
            type="button"
            className="relative z-10 mt-1 rounded px-2 text-sm text-primary hover:underline focus-visible:ring-2 focus-visible:ring-ring"
            onClick={onMemberships}
            aria-label={t("clientOverview.moreMemberships", { count: current.length - 1 })}
          >
            +{current.length - 1} →
          </button>
        )}
      </div>
      <BalanceTile balance={balance} onClick={onBalanceClick} />
      <div className="col-span-2 row-start-2 sm:col-span-1 sm:row-auto">
        <Tile label={t("clientCard.quick.nextSession")}>
          {nextSession === undefined ? (
            <Skeleton className="h-5 w-24" />
          ) : nextSession === null ? (
            <Muted>{t("clientCard.quick.none")}</Muted>
          ) : (
            <Link
              className="block rounded hover:underline focus-visible:ring-2 focus-visible:ring-ring"
              to="/sessions/$sessionId"
              params={{ sessionId: nextSession.id }}
              aria-label={t("clientOverview.openSession", { name: nextSession.group.name })}
            >
              <span className="font-medium">
                {format.date(nextSession.date)}, {format.time(nextSession.startTime)} →
              </span>
              <Sub>{nextSession.group.name}</Sub>
              <Sub>
                {[nextSession.hall.name, ...nextSession.coaches.map((c) => c.name)].join(" · ")}
              </Sub>
            </Link>
          )}
        </Tile>
      </div>
      <Tile
        label={t("clientOverview.attendance")}
        onClick={onSessions}
        actionLabel={t("clientOverview.openSessions")}
        dialog={false}
      >
        {attendance === undefined ? (
          <Skeleton className="h-5 w-24" />
        ) : (
          <>
            <span className="font-medium">
              {summary.recent.length === 0
                ? t("clientOverview.noAttendance")
                : t("clientOverview.ratio", {
                    count: summary.present,
                    total: summary.recent.length,
                  })}
            </span>
            <span className="my-1 flex gap-1" aria-hidden>
              {summary.recent.toReversed().map((item) => (
                <span
                  key={item.sessionId}
                  className={cn(
                    "h-1 flex-1 rounded",
                    item.presence === "PRESENT" ? "bg-emerald-500" : "bg-muted-foreground",
                  )}
                />
              ))}
            </span>
            {summary.lastVisit !== null && (
              <Sub>
                {t("clientOverview.lastVisit", { date: format.date(summary.lastVisit.date) })}
              </Sub>
            )}
          </>
        )}
      </Tile>
    </dl>
  );
}

/**
 * Плитка баланса [balance]; отрицательный выделен. С [onClick] плитка кликабельна
 * и открывает историю баланса. Ставится внутрь `<dl>`.
 */
export function BalanceTile({
  balance,
  onClick,
}: {
  readonly balance: Money;
  readonly onClick: (() => void) | undefined;
}) {
  const { t, format } = useI18n();
  return (
    <Tile
      label={t("clientCard.balance")}
      onClick={onClick}
      actionLabel={t("clientCard.balanceOpen", { amount: format.money(balance) })}
    >
      <span
        className={cn(
          "text-lg font-semibold tabular-nums",
          balance.minorUnits < 0 && "text-destructive",
        )}
      >
        {format.money(balance)}
      </span>
    </Tile>
  );
}

/**
 * Плитки абонементов из [memberships]: по плитке на каждый действующий абонемент (состояние
 * с сервера), по дате начала; неактивированные — последними. Пока абонементы загружаются (`undefined`) — одна плитка с заглушкой,
 * если неистёкших нет — одна некликабельная «Нет действующего». С [onOpen] плитка абонемента
 * кликабельна и открывает его панель. Ставится внутрь `<dl>`.
 */
export function MembershipTiles({
  memberships,
  onOpen,
}: {
  readonly memberships: readonly MembershipSchema[] | undefined;
  readonly onOpen: ((id: MembershipId) => void) | undefined;
}) {
  const { t } = useI18n();
  if (memberships === undefined) {
    return (
      <Tile label={t("clientCard.quick.subscription")}>
        <Skeleton className="h-5 w-24" />
      </Tile>
    );
  }
  const current = memberships
    .map((membership) => ({ membership, view: membershipView(membership) }))
    .filter(({ view }) => isCurrentMembership(view))
    .toSorted((a, b) => (a.view.startDate ?? "~").localeCompare(b.view.startDate ?? "~"));
  if (current.length === 0) {
    return (
      <Tile label={t("clientCard.quick.subscription")}>
        <Muted>{t("clientCard.quick.noSubscription")}</Muted>
      </Tile>
    );
  }
  return current.map(({ membership, view }) => (
    <Tile
      key={membership.id}
      label={t("clientCard.quick.subscription")}
      muted={view.phase === "upcoming" || view.phase === "inactive"}
      onClick={
        onOpen === undefined
          ? undefined
          : () => {
              onOpen(membership.id);
            }
      }
      actionLabel={t("clientCard.membership.open", { name: membership.name })}
    >
      {membership.sessionsTotal !== null && membership.sessionsTotal > 0 && (
        <svg aria-hidden viewBox="0 0 36 36" className="float-right ml-2 size-9 -rotate-90">
          <circle
            cx="18"
            cy="18"
            r="15"
            fill="none"
            stroke="currentColor"
            strokeWidth="3"
            className="text-secondary"
          />
          <circle
            cx="18"
            cy="18"
            r="15"
            fill="none"
            stroke="currentColor"
            strokeWidth="3"
            pathLength="100"
            strokeDasharray={`${String((100 * (membership.sessionsRemaining ?? 0)) / membership.sessionsTotal)} 100`}
            className="text-primary"
          />
        </svg>
      )}
      <span className="block truncate font-medium" title={membership.name}>
        {membership.name}
      </span>
      <span className="block text-xs text-muted-foreground">
        <MembershipTerms membership={membership} view={view} />
      </span>
      {view.phase === "ending" && (
        <span className="text-xs text-amber-700">{t("clientCard.subscriptionEnding")}</span>
      )}
      <span className="block text-xs text-sky-700 dark:text-sky-400">
        <FreezeNote membership={membership} />
      </span>
    </Tile>
  ));
}

/**
 * Плитка последнего посещения [lastVisit]: `null` — посещений не было,
 * `undefined` — ещё загружается. Ставится внутрь `<dl>`.
 */
export function LastVisitTile({
  lastVisit,
}: {
  readonly lastVisit: ClientAttendanceItemSchema | null | undefined;
}) {
  const { t, format } = useI18n();
  return (
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
  );
}

/**
 * Плитка быстрой информации;
 * [muted] — значение приглушено (например, абонемент ещё не начался).
 * С [onClick] плитка кликабельна целиком: значение — кнопка с доступным именем [actionLabel],
 * зона нажатия растянута на всю плитку, справа от подписи — стрелка перехода.
 */
function Tile({
  label,
  dialog = true,
  muted = false,
  onClick,
  actionLabel,
  children,
}: {
  readonly label: string;
  readonly dialog?: boolean;
  readonly muted?: boolean;
  readonly onClick?: (() => void) | undefined;
  readonly actionLabel?: string;
  readonly children: ReactNode;
}) {
  const clickable = onClick !== undefined;
  return (
    <div
      className={cn(
        "relative min-w-0 rounded-md border bg-background/40 px-3 py-2",
        clickable &&
          "group transition-colors hover:border-foreground/20 hover:bg-accent has-[:active]:bg-accent/70 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring",
      )}
    >
      <dt className="flex items-center justify-between gap-1 text-xs text-muted-foreground">
        {label}
        {clickable && (
          <ChevronRightIcon
            aria-hidden
            className="size-4 shrink-0 transition-colors group-hover:text-foreground"
          />
        )}
      </dt>
      <dd className={cn("mt-0.5 min-w-0 text-sm", muted && "text-muted-foreground")}>
        {clickable ? (
          <button
            type="button"
            aria-label={actionLabel}
            aria-haspopup={dialog ? "dialog" : undefined}
            onClick={onClick}
            className="block w-full min-w-0 cursor-pointer text-left outline-none after:absolute after:inset-0 after:rounded-md"
          >
            {children}
          </button>
        ) : (
          children
        )}
      </dd>
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
