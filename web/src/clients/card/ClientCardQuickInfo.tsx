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
import { MembershipTerms } from "../membership/MembershipParts";
import { isCurrentMembership, membershipView } from "../membership/membershipView";

/**
 * Быстрая информация в шапке карточки: баланс, плитки действующих абонементов, последнее посещение
 * и ближайшее занятие из данных сервера; статус, ответственный и скидка — заглушки до появления
 * этих полей в анкете. `undefined` во входных данных — значение ещё загружается.
 * Если задан [onBalanceClick], плитка баланса кликабельна и открывает историю баланса;
 * плитка абонемента открывает его панель через [onMembershipOpen].
 */
export function ClientCardQuickInfo({
  balance,
  memberships,
  lastVisit,
  nextSession,
  onBalanceClick,
  onMembershipOpen,
}: {
  readonly balance: Money;
  readonly memberships: readonly MembershipSchema[] | undefined;
  /** Последнее посещение; `null` — посещений не было. */
  readonly lastVisit: ClientAttendanceItemSchema | null | undefined;
  /** Ближайшее занятие; `null` — занятий нет. */
  readonly nextSession: ScheduleSessionSchema | null | undefined;
  /** Открывает историю баланса; `undefined` — плитка баланса некликабельна. */
  readonly onBalanceClick: (() => void) | undefined;
  /** Открывает панель абонемента по его идентификатору. */
  readonly onMembershipOpen: (id: MembershipId) => void;
}) {
  const { t, format } = useI18n();

  return (
    <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-6">
      <BalanceTile balance={balance} onClick={onBalanceClick} />
      <MembershipTiles memberships={memberships} onOpen={onMembershipOpen} />
      <LastVisitTile lastVisit={lastVisit} />
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
      <span className="block truncate font-medium" title={membership.name}>
        {membership.name}
      </span>
      <span className="block text-xs text-muted-foreground">
        <MembershipTerms membership={membership} view={view} />
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
 * Плитка быстрой информации; [stub] — поле ещё не поддержано, рамка пунктиром;
 * [muted] — значение приглушено (например, абонемент ещё не начался).
 * С [onClick] плитка кликабельна целиком: значение — кнопка с доступным именем [actionLabel],
 * зона нажатия растянута на всю плитку, справа от подписи — стрелка перехода.
 */
function Tile({
  label,
  stub = false,
  muted = false,
  onClick,
  actionLabel,
  children,
}: {
  readonly label: string;
  readonly stub?: boolean;
  readonly muted?: boolean;
  readonly onClick?: (() => void) | undefined;
  readonly actionLabel?: string;
  readonly children: ReactNode;
}) {
  const { t } = useI18n();
  const clickable = onClick !== undefined;
  return (
    <div
      className={cn(
        "relative min-w-0 rounded-md border bg-background/40 px-3 py-2",
        stub && "border-dashed",
        clickable &&
          "group transition-colors hover:border-foreground/20 hover:bg-accent has-[:active]:bg-accent/70 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring",
      )}
      title={stub ? t("clientCard.quick.stubHint") : undefined}
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
