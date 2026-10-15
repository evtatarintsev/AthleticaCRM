import { Link } from "@tanstack/react-router";
import {
  BanIcon,
  CalendarClockIcon,
  CheckCircle2Icon,
  ChevronRightIcon,
  CircleAlertIcon,
  PencilIcon,
  StickyNoteIcon,
} from "lucide-react";
import { useState, type ReactNode } from "react";
import type { ApiClient } from "@/api/client";
import type {
  EmployeeId,
  EmployeeListItem,
  ScheduleGroupSchema,
  SessionStatus,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { EmployeeCardSheet } from "@/employees/EmployeeCardSheet";
import { useI18n } from "@/i18n/context";
import { isPlainClick } from "@/lib/plainClick";
import { cn } from "@/lib/utils";
import { Avatar } from "@/ui/Avatar";
import type { CompleteBlock } from "../attendance";
import type { SessionCard } from "../sessionDetailMock";
import { sessionStatusLabelKey } from "../sessionStatus";

/**
 * Левая колонка карточки занятия в стиле карточки клиента: заголовок «Занятие в группе …»
 * со ссылкой на группу и статусом, плитки даты, зала, тренеров и заполненности журнала,
 * действия и, если провести занятие нельзя, — объяснение причины рядом с кнопкой.
 * Плитки даты и зала кликабельны и открывают панели переноса и смены зала;
 * тренеры — ссылки на их карточки, состав меняется кнопкой с карандашом.
 *
 * Обычный клик по тренеру открывает его карточку в боковой панели поверх занятия, чтобы
 * не уводить со страницы; клик с модификатором (новая вкладка) и средняя кнопка работают
 * как обычная ссылка на раздел «Сотрудники». Открытая карточка — локальное состояние,
 * а не search-параметр: как и остальные панели занятия, она — краткий взгляд, который
 * не нужно сохранять в истории и делиться им по ссылке.
 */
export function SessionInfoPanel({
  api,
  group,
  card,
  employees,
  editable,
  block,
  marked,
  total,
  onReschedule,
  onChangeHall,
  onChangeCoaches,
  onComplete,
  onCancel,
  onNote,
}: {
  readonly api: ApiClient;
  readonly group: ScheduleGroupSchema;
  readonly card: SessionCard;
  /** Сотрудники филиала: из них берутся аватары тренеров; `undefined` — ещё загружаются. */
  readonly employees: readonly EmployeeListItem[] | undefined;
  /** Можно ли менять занятие (оно запланировано). */
  readonly editable: boolean;
  /** Почему занятие нельзя провести; `null` — можно. */
  readonly block: CompleteBlock | null;
  /** Число отмеченных участников. */
  readonly marked: number;
  /** Число участников в составе. */
  readonly total: number;
  readonly onReschedule: () => void;
  readonly onChangeHall: () => void;
  readonly onChangeCoaches: () => void;
  readonly onComplete: () => void;
  readonly onCancel: () => void;
  readonly onNote: () => void;
}) {
  const { t, format } = useI18n();
  const [openCoach, setOpenCoach] = useState<EmployeeId | null>(null);
  const [coachEditOpen, setCoachEditOpen] = useState(false);
  const avatarOf = new Map((employees ?? []).map((employee) => [employee.id, employee.avatarId]));
  const blockId = "session-complete-block";
  const showBlock = editable && (block === "unmarked" || block === "notStarted");

  return (
    <div className="space-y-4">
      <div className="space-y-4 rounded-lg border bg-card p-4">
        <div className="flex items-start gap-3">
          <span
            aria-hidden
            className="grid size-12 shrink-0 place-items-center rounded-full bg-primary/10 text-primary [&_svg]:size-6"
          >
            <CalendarClockIcon />
          </span>
          <div className="min-w-0 flex-1 space-y-1.5">
            <h1 className="text-xl font-semibold tracking-tight">
              <span className="text-muted-foreground">{t("sessionDetail.titlePrefix")} </span>
              <Link
                to="/groups/$groupId"
                params={{ groupId: group.id }}
                className="hover:underline"
              >
                {group.name}
              </Link>
            </h1>
            <SessionStatusBadge status={card.status} />
          </div>
        </div>

        <dl className="grid gap-2">
          <Tile
            label={t("sessionDetail.tile.dateTime")}
            onClick={editable ? onReschedule : undefined}
            actionLabel={t("sessionDetail.rescheduleOpen", {
              date: format.date(card.date),
              time: format.time(card.startTime),
            })}
          >
            <span className="block font-medium">
              {format.date(card.date)}, {format.time(card.startTime)}
            </span>
            <Sub>
              {t("sessionDetail.timeRange", {
                start: format.time(card.startTime),
                end: format.time(card.endTime),
              })}
            </Sub>
          </Tile>
          <Tile
            label={t("sessionDetail.tile.hall")}
            onClick={editable ? onChangeHall : undefined}
            actionLabel={t("sessionDetail.hallOpen", { name: card.hall.name })}
          >
            <span className="block font-medium">{card.hall.name}</span>
          </Tile>
          <div className="min-w-0 rounded-md border bg-background/40 px-3 py-2">
            <dt className="flex items-center justify-between gap-1 text-xs text-muted-foreground">
              {t("sessionDetail.coachesTitle")}
              {editable && (
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="-my-1 -mr-2 size-7"
                  aria-label={t("sessionDetail.changeCoachesAction")}
                  title={t("sessionDetail.changeCoachesAction")}
                  onClick={onChangeCoaches}
                >
                  <PencilIcon aria-hidden />
                </Button>
              )}
            </dt>
            <dd className="mt-1 text-sm">
              {card.coaches.length === 0 ? (
                <span className="text-muted-foreground">{t("sessionDetail.noCoaches")}</span>
              ) : (
                <ul className="space-y-2.5 py-1">
                  {card.coaches.map((coach) => (
                    <li key={coach.id}>
                      <Link
                        to="/employees"
                        search={{ employee: coach.id }}
                        aria-haspopup="dialog"
                        onClick={(event) => {
                          if (isPlainClick(event)) {
                            event.preventDefault();
                            setOpenCoach(coach.id);
                          }
                        }}
                        className="inline-flex items-center gap-2.5 font-medium text-primary hover:underline"
                      >
                        <Avatar
                          api={api}
                          uploadId={avatarOf.get(coach.id) ?? null}
                          name={coach.name}
                          className="size-7 text-[11px]"
                        />
                        {coach.name}
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </dd>
          </div>
          <Tile label={t("sessionDetail.tile.attendance")}>
            <span className="block font-medium tabular-nums">
              {t("sessionDetail.attendanceProgress", { marked, total })}
            </span>
            <span
              aria-hidden
              className="mt-1.5 block h-1.5 overflow-hidden rounded-full bg-secondary"
            >
              <span
                className={cn(
                  "block h-full rounded-full",
                  marked === total ? "bg-green-600" : "bg-primary",
                )}
                style={{ width: total === 0 ? "0%" : `${String((marked / total) * 100)}%` }}
              />
            </span>
          </Tile>
        </dl>

        {editable && (
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={block !== null}
              aria-describedby={showBlock ? blockId : undefined}
              onClick={onComplete}
            >
              <CheckCircle2Icon aria-hidden />
              {t("sessionDetail.completeAction")}
            </Button>
            <Button variant="outline" size="sm" onClick={onCancel}>
              <BanIcon aria-hidden />
              {t("sessionDetail.cancelAction")}
            </Button>
          </div>
        )}

        {showBlock && (
          <div
            id={blockId}
            className="space-y-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
          >
            <p className="flex items-center gap-1.5 font-medium">
              <CircleAlertIcon aria-hidden className="size-4 shrink-0" />
              {t("sessionDetail.completeBlocked.title")}
            </p>
            <p>
              {block === "unmarked"
                ? t("sessionDetail.completeBlocked.unmarked", { count: total - marked })
                : t("sessionDetail.completeBlocked.notStarted", {
                    date: format.date(card.date),
                    time: format.time(card.startTime),
                  })}
            </p>
          </div>
        )}
      </div>

      <div className="rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b py-2 pr-2 pl-4">
          <h2 className="font-medium">{t("sessionDetail.noteTitle")}</h2>
          {card.note !== "" && (
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("sessionDetail.editNoteAria")}
              title={t("sessionDetail.editNoteAria")}
              onClick={onNote}
            >
              <PencilIcon aria-hidden />
            </Button>
          )}
        </div>
        {card.note === "" ? (
          <button
            type="button"
            onClick={onNote}
            className="flex w-full items-center gap-2 px-4 py-3 text-left text-sm text-muted-foreground hover:text-foreground"
          >
            <StickyNoteIcon aria-hidden className="size-4" />
            {t("sessionDetail.noteAction")}
          </button>
        ) : (
          <p className="px-4 py-3 text-sm whitespace-pre-wrap">{card.note}</p>
        )}
      </div>

      <EmployeeCardSheet
        api={api}
        employeeId={openCoach}
        editOpen={coachEditOpen}
        onEditOpenChange={setCoachEditOpen}
        onClose={() => {
          setOpenCoach(null);
          setCoachEditOpen(false);
        }}
      />
    </div>
  );
}

/** Цветной бейдж статуса занятия. */
function SessionStatusBadge({ status }: { readonly status: SessionStatus }) {
  const { t } = useI18n();
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        status === "SCHEDULED" && "bg-sky-100 text-sky-900 dark:bg-sky-950/60 dark:text-sky-200",
        status === "COMPLETED" &&
          "bg-green-100 text-green-900 dark:bg-green-950/60 dark:text-green-200",
        status === "CANCELLED" && "bg-red-100 text-red-900 dark:bg-red-950/60 dark:text-red-200",
      )}
    >
      {t(sessionStatusLabelKey(status))}
    </span>
  );
}

/**
 * Плитка информации о занятии, как в шапке карточки клиента. С [onClick] плитка кликабельна
 * целиком: значение — кнопка с доступным именем [actionLabel], справа от подписи — стрелка.
 */
function Tile({
  label,
  onClick,
  actionLabel,
  children,
}: {
  readonly label: string;
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
      <dd className="mt-0.5 min-w-0 text-sm">
        {clickable ? (
          <button
            type="button"
            aria-label={actionLabel}
            aria-haspopup="dialog"
            onClick={onClick}
            className="w-full cursor-pointer text-left outline-none after:absolute after:inset-0 after:rounded-md"
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
