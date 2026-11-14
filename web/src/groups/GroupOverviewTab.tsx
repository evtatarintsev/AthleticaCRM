import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import type { ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type {
  BranchId,
  GroupDetailResponse,
  GroupId,
  GroupRosterClient,
  GroupSessionSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { addDays, dayOfWeekOf, todayLocalDate } from "@/lib/localDate";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { Avatar } from "@/ui/Avatar";
import {
  ATTENDANCE_LOOKBACK_DAYS,
  ATTENTION_ORDER,
  attendanceSummary,
  attentionCounts,
  mergedScheduleRows,
  scheduleRowsByDay,
  type RosterFilter,
  type ScheduleRow,
} from "./groupOverview";
import { useGroupAttendance } from "./groupsQueries";
import { defaultSessionsWindow } from "./groupSessionsWindow";

/**
 * Вкладка «Обзор» карточки группы [detail]: плитки «Следующее занятие», «Требуют внимания»,
 * «Клиенты», «Посещаемость» и карточки «Расписание» и «Тренеры». Состав [roster] — `undefined`,
 * пока не загружен. Плитки ведут на вкладки через [onOpenClients] и [onOpenJournal], правка
 * расписания и тренеров — через [onEditSchedule] и [onEditEmployees].
 */
export function GroupOverviewTab({
  api,
  branchId,
  groupId,
  detail,
  roster,
  onOpenClients,
  onOpenJournal,
  onEditSchedule,
  onEditEmployees,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly groupId: GroupId;
  readonly detail: GroupDetailResponse;
  readonly roster: readonly GroupRosterClient[] | undefined;
  readonly onOpenClients: (filter: RosterFilter) => void;
  readonly onOpenJournal: () => void;
  readonly onEditSchedule: () => void;
  readonly onEditEmployees: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 max-[560px]:grid-cols-1 min-[1000px]:grid-cols-4">
        <NextSessionTile
          api={api}
          branchId={branchId}
          groupId={groupId}
          onEditSchedule={onEditSchedule}
        />
        <AttentionTile roster={roster} onOpen={onOpenClients} />
        <ClientsTile roster={roster} onOpen={onOpenClients} />
        <AttendanceTile api={api} branchId={branchId} groupId={groupId} onOpen={onOpenJournal} />
      </div>
      <div className="grid gap-4 md:grid-cols-2 md:items-start">
        <OverviewCard
          title={t("groups.detail.scheduleTitle")}
          onEdit={onEditSchedule}
          editLabel={t("groups.detail.editSection")}
        >
          <ScheduleCardBody detail={detail} />
        </OverviewCard>
        <OverviewCard
          title={t("groups.detail.employeesTitle")}
          onEdit={onEditEmployees}
          editLabel={t("groups.detail.editSection")}
        >
          {detail.employees.length === 0 ? (
            <p className="px-4 py-3 text-sm text-muted-foreground">
              {t("groups.detail.employeesEmpty")}
            </p>
          ) : (
            <ul className="divide-y">
              {detail.employees.map((employee) => (
                <li key={employee.id} className="flex items-center gap-3 px-4 py-2.5 text-sm">
                  <Avatar
                    api={api}
                    uploadId={employee.avatarId}
                    name={employee.name}
                    className="size-7"
                  />
                  {employee.name}
                </li>
              ))}
            </ul>
          )}
        </OverviewCard>
      </div>
    </div>
  );
}

/** Оформление плитки сводки; [tone] — цветовая схема. */
function tileClass(tone: "default" | "brand" | "warn") {
  return cn(
    "flex min-h-[7.5rem] flex-col gap-1.5 rounded-xl border p-4 text-left",
    tone === "brand" && "border-transparent bg-primary text-primary-foreground",
    tone === "warn" &&
      "border-transparent bg-amber-50 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200",
    tone === "default" && "bg-card hover:border-foreground/20",
  );
}

/** Заголовок плитки [children]. */
function TileTitle({ children }: { readonly children: ReactNode }) {
  return <h2 className="text-xs font-medium opacity-80">{children}</h2>;
}

/** Крупное значение плитки [children]. */
function TileValue({ children }: { readonly children: ReactNode }) {
  return <p className="text-2xl font-bold tracking-tight tabular-nums">{children}</p>;
}

/**
 * Плитка «Следующее занятие»: ближайшее занятие из ответа блока «Занятия» (тот же запрос и окно
 * по умолчанию) с переходом в его журнал; без занятия — «Изменить расписание» через [onEditSchedule].
 */
function NextSessionTile({
  api,
  branchId,
  groupId,
  onEditSchedule,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly groupId: GroupId;
  readonly onEditSchedule: () => void;
}) {
  const { t } = useI18n();
  const window = defaultSessionsWindow(todayLocalDate());
  const query = useQuery(
    apiQuery(api, branchId, "groups/sessions", { groupId, from: window.from, to: window.to }),
  );
  if (query.data === undefined) {
    return <Skeleton className="min-h-[7.5rem] rounded-xl" />;
  }
  const next = query.data.next;
  if (next === null) {
    return (
      <section className={tileClass("default")}>
        <TileTitle>{t("groups.overview.nextTitle")}</TileTitle>
        <p className="text-sm text-muted-foreground">{t("groups.overview.noNext")}</p>
        <Button variant="outline" size="sm" className="mt-auto self-start" onClick={onEditSchedule}>
          {t("groups.detail.editSchedule")}
        </Button>
      </section>
    );
  }
  return (
    <section className={tileClass("brand")}>
      <TileTitle>{t("groups.overview.nextTitle")}</TileTitle>
      <NextSessionText session={next} />
      <Button variant="secondary" size="sm" className="mt-auto self-start" asChild>
        <Link to="/sessions/$sessionId" params={{ sessionId: next.id }}>
          {t("groups.overview.markAttendance")}
        </Link>
      </Button>
    </section>
  );
}

/** Дата, время, зал и тренеры занятия [session]. */
function NextSessionText({ session }: { readonly session: GroupSessionSchema }) {
  const { t, format } = useI18n();
  return (
    <>
      <p className="text-lg font-bold tracking-tight">
        {t(`day.${dayOfWeekOf(session.date)}`)} {format.date(session.date)}
      </p>
      <p className="text-sm opacity-85">
        {t("groups.overview.hall", {
          time: `${format.time(session.startTime)}–${format.time(session.endTime)}`,
          hall: session.hall.name,
        })}
      </p>
      {session.coaches.length > 0 && (
        <p className="truncate text-sm opacity-85">
          {session.coaches.map((coach) => coach.name).join(", ")}
        </p>
      )}
    </>
  );
}

/** Плитка «Требуют внимания»: число и разбивка по причинам; открывает состав с фильтром. */
function AttentionTile({
  roster,
  onOpen,
}: {
  readonly roster: readonly GroupRosterClient[] | undefined;
  readonly onOpen: (filter: RosterFilter) => void;
}) {
  const { t } = useI18n();
  if (roster === undefined) {
    return <Skeleton className="min-h-[7.5rem] rounded-xl" />;
  }
  const counts = attentionCounts(roster);
  return (
    <button
      type="button"
      className={tileClass(counts.total > 0 ? "warn" : "default")}
      onClick={() => {
        onOpen("attention");
      }}
    >
      <TileTitle>{t("groups.overview.attentionTitle")}</TileTitle>
      {counts.total === 0 ? (
        <p className="text-sm text-muted-foreground">{t("groups.overview.allOk")}</p>
      ) : (
        <>
          <TileValue>{counts.total}</TileValue>
          <ul className="text-sm">
            {ATTENTION_ORDER.filter((reason) => counts.byReason[reason] > 0).map((reason) => (
              <li key={reason}>
                {t("groups.overview.reasonCount", {
                  reason: t(`groups.attention.${reason}`),
                  count: counts.byReason[reason],
                })}
              </li>
            ))}
          </ul>
        </>
      )}
    </button>
  );
}

/** Плитка «Клиенты»: число участников и полоса «в порядке / заканчивается / проблемы». */
function ClientsTile({
  roster,
  onOpen,
}: {
  readonly roster: readonly GroupRosterClient[] | undefined;
  readonly onOpen: (filter: RosterFilter) => void;
}) {
  const { t } = useI18n();
  if (roster === undefined) {
    return <Skeleton className="min-h-[7.5rem] rounded-xl" />;
  }
  const counts = attentionCounts(roster);
  const ok = roster.length - counts.total;
  const ending = counts.byReason.MEMBERSHIP_ENDING;
  const problem = counts.byReason.DEBT + counts.byReason.NO_MEMBERSHIP;
  const legend = t("groups.overview.distribution", { ok, ending, problem });
  return (
    <button
      type="button"
      className={tileClass("default")}
      onClick={() => {
        onOpen("all");
      }}
    >
      <TileTitle>{t("groups.overview.clientsTitle")}</TileTitle>
      <TileValue>{roster.length}</TileValue>
      {roster.length > 0 && (
        <div className="mt-auto flex h-1.5 gap-0.5 overflow-hidden rounded-full" title={legend}>
          <span className="bg-emerald-500" style={{ flexGrow: ok }} />
          <span className="bg-amber-500" style={{ flexGrow: ending }} />
          <span className="bg-red-500" style={{ flexGrow: problem }} />
        </div>
      )}
      <span className="sr-only">{legend}</span>
    </button>
  );
}

/**
 * Плитка «Посещаемость»: доля «пришёл» по последним закрытым занятиям за
 * [ATTENDANCE_LOOKBACK_DAYS] дней и явка по каждому из них; открывает «Журнал».
 */
function AttendanceTile({
  api,
  branchId,
  groupId,
  onOpen,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly groupId: GroupId;
  readonly onOpen: () => void;
}) {
  const { t, format } = useI18n();
  const today = todayLocalDate();
  const query = useGroupAttendance(
    api,
    branchId,
    groupId,
    addDays(today, -ATTENDANCE_LOOKBACK_DAYS),
    today,
  );
  if (query.data === undefined) {
    return <Skeleton className="min-h-[7.5rem] rounded-xl" />;
  }
  const summary = attendanceSummary(query.data);
  return (
    <button type="button" className={tileClass("default")} onClick={onOpen}>
      <TileTitle>{t("groups.overview.attendanceTitle")}</TileTitle>
      {summary === null ? (
        <p className="text-sm text-muted-foreground">{t("groups.overview.noClosed")}</p>
      ) : (
        <>
          <TileValue>{summary.percent} %</TileValue>
          <p className="text-sm text-muted-foreground">
            {t("groups.overview.attendanceSub", { count: summary.sessions.length })}
          </p>
          <ul className="mt-auto flex h-7 items-end gap-0.5">
            {summary.sessions.map((turnout) => {
              const label = t("groups.overview.turnout", {
                date: format.date(turnout.session.date),
                present: turnout.present,
                total: turnout.total,
              });
              const height = turnout.total === 0 ? 0 : (turnout.present * 100) / turnout.total;
              return (
                <li
                  key={turnout.session.id}
                  title={label}
                  className="flex-1 rounded-sm bg-primary"
                  style={{ height: `${String(Math.max(height, 8))}%` }}
                >
                  <span className="sr-only">{label}</span>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </button>
  );
}

/** Карточка «Обзора» с заголовком [title] и действием [editLabel] в нём. */
function OverviewCard({
  title,
  editLabel,
  onEdit,
  children,
}: {
  readonly title: string;
  readonly editLabel: string;
  readonly onEdit: () => void;
  readonly children: ReactNode;
}) {
  return (
    <section className="rounded-xl border bg-card">
      <div className="flex items-center justify-between border-b px-4 py-2.5">
        <h2 className="text-sm font-semibold">{title}</h2>
        <Button variant="ghost" size="sm" onClick={onEdit}>
          {editLabel}
        </Button>
      </div>
      {children}
    </section>
  );
}

/**
 * Тело карточки «Расписание»: на широком экране — слот на строку по дням, на телефоне — слоты
 * с одинаковым временем и залом в одной строке; пометка планового изменения.
 */
function ScheduleCardBody({ detail }: { readonly detail: GroupDetailResponse }) {
  const { t, format } = useI18n();
  if (detail.schedule.length === 0) {
    return <p className="px-4 py-3 text-sm text-muted-foreground">{t("groups.noSchedule")}</p>;
  }
  return (
    <>
      <ScheduleRows rows={scheduleRowsByDay(detail.schedule)} className="max-sm:hidden" />
      <ScheduleRows rows={mergedScheduleRows(detail.schedule)} className="sm:hidden" />
      {detail.scheduleChangeAt !== null && (
        <p className="border-t px-4 py-2.5 text-xs text-muted-foreground">
          {t("groups.overview.scheduleChange", { date: format.date(detail.scheduleChangeAt) })}
        </p>
      )}
    </>
  );
}

/** Строки расписания [rows]: дни, время и зал. */
function ScheduleRows({
  rows,
  className,
}: {
  readonly rows: readonly ScheduleRow[];
  readonly className: string;
}) {
  const { t, format } = useI18n();
  return (
    <ul className={cn("divide-y text-sm", className)}>
      {rows.map((row) => {
        const days = row.days.map((day) => t(`day.${day}`)).join(", ");
        const time = `${format.time(row.startAt)}–${format.time(row.endAt)}`;
        return (
          <li
            key={`${days}|${row.startAt}|${row.hallName ?? ""}`}
            className="flex gap-3 px-4 py-2.5"
          >
            <span className="font-medium">{days}</span>
            <span className="tabular-nums">{time}</span>
            {row.hallName !== null && (
              <span className="ml-auto text-muted-foreground">{row.hallName}</span>
            )}
          </li>
        );
      })}
    </ul>
  );
}
