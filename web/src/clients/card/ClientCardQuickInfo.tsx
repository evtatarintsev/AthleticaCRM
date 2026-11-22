import { attendanceSummary, currentMemberships } from "./clientOverview";
import { CheckIcon, ChevronRightIcon, TicketIcon, WalletIcon } from "lucide-react";
import type { ReactNode } from "react";
import type {
  ClientAttendanceItemSchema,
  MembershipId,
  MembershipSchema,
  Money,
} from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { FreezeNote, MembershipTerms } from "../membership/MembershipParts";
import { isCurrentMembership, membershipView } from "../membership/membershipView";

/** Три плитки карточки; undefined означает загрузку. */
export function ClientCardQuickInfo({
  balance,
  memberships,
  attendance,
  onBalanceClick,
  onMembershipOpen,
  onMemberships,
  onSessions,
}: {
  readonly balance: Money;
  readonly memberships: readonly MembershipSchema[] | undefined;
  readonly attendance: readonly ClientAttendanceItemSchema[] | undefined;
  readonly onBalanceClick: (() => void) | undefined;
  readonly onMembershipOpen: (id: MembershipId) => void;
  readonly onMemberships: () => void;
  readonly onSessions: () => void;
}) {
  const { t, format } = useI18n();
  const summary = attendanceSummary(attendance);
  const current = currentMemberships(memberships);
  const main = current[0];
  return (
    <div className="grid grid-cols-2 gap-2.5 sm:gap-3 min-[1000px]:grid-cols-3">
      <div className="relative min-w-0 max-[1000px]:col-span-2 min-[1000px]:col-span-1">
        {memberships === undefined ? (
          <TileSkeleton />
        ) : main === undefined ? (
          <div className={TILE_CLASS}>
            <span className="pr-20">
              <TileLabel icon={<TicketIcon />}>{t("clientCard.quick.subscription")}</TileLabel>
            </span>
            <span className="text-muted-foreground">{t("clientCard.quick.noSubscription")}</span>
          </div>
        ) : (
          <MembershipOverviewTile
            membership={main}
            onOpen={() => {
              onMembershipOpen(main.id);
            }}
          />
        )}
        {memberships !== undefined && memberships.length > 0 && (
          <button
            type="button"
            className="absolute top-3 right-3 z-10 h-[22px] rounded-full bg-primary/10 px-2 text-xs font-semibold text-primary hover:bg-primary/15 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
            onClick={onMemberships}
            aria-label={t("clientOverview.moreMemberships", { count: memberships.length })}
            aria-haspopup="dialog"
          >
            {t("clientOverview.allMemberships", { count: memberships.length })}
          </button>
        )}
      </div>

      {onBalanceClick === undefined ? (
        <div className={TILE_CLASS}>
          <BalanceOverviewContent balance={balance} go={false} />
        </div>
      ) : (
        <button
          type="button"
          className={cn(TILE_CLASS, INTERACTIVE)}
          aria-haspopup="dialog"
          aria-label={t("clientCard.balanceOpen", { amount: format.money(balance) })}
          onClick={onBalanceClick}
        >
          <BalanceOverviewContent balance={balance} go />
        </button>
      )}

      <button
        type="button"
        className={cn(TILE_CLASS, INTERACTIVE)}
        aria-label={t("clientOverview.openSessions")}
        aria-haspopup="dialog"
        onClick={onSessions}
      >
        <TileLabel icon={<CheckIcon />} go>
          {t("clientOverview.attendance")}
        </TileLabel>
        {attendance === undefined ? (
          <Skeleton className="h-6 w-24" />
        ) : summary.recent.length === 0 ? (
          <span className="text-muted-foreground">{t("clientOverview.noAttendance")}</span>
        ) : (
          <TileValue>
            {t("clientOverview.ratio", {
              count: summary.present,
              total: summary.recent.length,
            })}
          </TileValue>
        )}
        {summary.lastVisit !== null && (
          <span className="text-sm text-foreground/80">
            {t("clientOverview.lastVisit", { date: format.date(summary.lastVisit.date) })}
          </span>
        )}
        {summary.recent.length > 0 && (
          <span className="mt-auto flex gap-1" aria-hidden>
            {summary.recent.toReversed().map((item) => (
              <span
                key={item.sessionId}
                className={cn(
                  "h-2 flex-1 rounded-[3px]",
                  item.presence === "PRESENT"
                    ? "bg-primary"
                    : "shadow-[inset_0_0_0_1.5px_var(--border)]",
                )}
              />
            ))}
          </span>
        )}
      </button>
    </div>
  );
}

/** Общее оформление плиток карточки. */
const TILE_CLASS =
  "flex h-full min-h-33 w-full min-w-0 flex-col gap-1.5 rounded-xl border bg-card p-4 text-left max-sm:min-h-30 max-sm:p-3.5";

/** Признаки нажимаемой плитки: наведение и фокус. */
const INTERACTIVE =
  "cursor-pointer transition-colors hover:border-foreground/20 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none";

/** Подпись плитки [children] с иконкой [icon]; [go] — стрелка перехода. */
function TileLabel({
  icon,
  go = false,
  children,
}: {
  readonly icon: ReactNode;
  readonly go?: boolean;
  readonly children: ReactNode;
}) {
  return (
    <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground [&_svg]:size-4 [&_svg]:shrink-0">
      <span aria-hidden className="contents">
        {icon}
      </span>
      {children}
      {go && <ChevronRightIcon aria-hidden className="ml-auto" />}
    </span>
  );
}

/** Крупное значение плитки [children]. */
function TileValue({ children }: { readonly children: ReactNode }) {
  return (
    <span className="text-xl leading-tight font-bold tracking-tight tabular-nums sm:text-[22px]">
      {children}
    </span>
  );
}

/** Заглушка плитки на время загрузки. */
function TileSkeleton() {
  return <Skeleton className="h-full min-h-33 rounded-xl max-sm:min-h-30" />;
}

/** Содержимое плитки «Баланс» [balance]; [go] — стрелка перехода. */
function BalanceOverviewContent({
  balance,
  go,
}: {
  readonly balance: Money;
  readonly go: boolean;
}) {
  const { t, format } = useI18n();
  return (
    <>
      <TileLabel icon={<WalletIcon />} go={go}>
        {t("clientCard.balance")}
      </TileLabel>
      <span
        className={cn(
          balance.minorUnits < 0 && "text-destructive",
          balance.minorUnits > 0 && "text-emerald-700 dark:text-emerald-400",
        )}
      >
        <TileValue>{format.money(balance)}</TileValue>
      </span>
    </>
  );
}

/**
 * Плитка основного абонемента [membership]: кольцо остатка, название, срок и выделение
 * «Заканчивается». Нажатие — [onOpen]; справа сверху отдельная кнопка «Все · N».
 */
function MembershipOverviewTile({
  membership,
  onOpen,
}: {
  readonly membership: MembershipSchema;
  readonly onOpen: () => void;
}) {
  const { t } = useI18n();
  const view = membershipView(membership);
  const ending = view.phase === "ending";
  return (
    <button
      type="button"
      className={cn(TILE_CLASS, INTERACTIVE)}
      aria-haspopup="dialog"
      aria-label={t("clientCard.membership.open", { name: membership.name })}
      onClick={onOpen}
    >
      <span className="pr-20">
        <TileLabel icon={<TicketIcon />} go>
          {t("clientCard.quick.subscription")}
        </TileLabel>
      </span>
      <span className={cn("flex items-center gap-3", view.phase === "upcoming" && "opacity-60")}>
        <RemainingRing membership={membership} ending={ending} />
        <span className="min-w-0">
          <span className="line-clamp-2 font-semibold" title={membership.name}>
            {membership.name}
          </span>
          <span className="block text-sm text-foreground/80">
            <MembershipTerms membership={membership} view={view} />
          </span>
        </span>
      </span>
      <span className="block text-xs text-sky-700 dark:text-sky-400">
        <FreezeNote membership={membership} />
      </span>
      {ending && (
        <span className="mt-auto inline-flex h-[22px] items-center self-start rounded-full bg-amber-100 px-2 text-xs font-semibold text-amber-900 dark:bg-amber-950 dark:text-amber-200">
          {t("clientCard.subscriptionEnding")}
        </span>
      )}
    </button>
  );
}

/** Кольцо остатка занятий [membership] с числом в центре; безлимит — «∞». */
function RemainingRing({
  membership,
  ending,
}: {
  readonly membership: MembershipSchema;
  readonly ending: boolean;
}) {
  const total = membership.sessionsTotal;
  const left = membership.sessionsRemaining ?? 0;
  const share = total === null ? 1 : total > 0 ? left / total : 0;
  return (
    <span aria-hidden className="relative inline-flex size-11 shrink-0">
      <svg viewBox="0 0 44 44" className="size-11 -rotate-90">
        <circle cx="22" cy="22" r="18" fill="none" strokeWidth="4" className="stroke-border" />
        <circle
          cx="22"
          cy="22"
          r="18"
          fill="none"
          strokeWidth="4"
          strokeLinecap="round"
          pathLength="100"
          strokeDasharray={`${String(100 * share)} 100`}
          className={ending ? "stroke-amber-600" : "stroke-primary"}
        />
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-[13px] font-bold">
        {total === null ? "∞" : left}
      </span>
    </span>
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
  muted = false,
  onClick,
  actionLabel,
  children,
}: {
  readonly label: string;
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
            aria-haspopup="dialog"
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
