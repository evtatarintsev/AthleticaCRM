import { Link } from "@tanstack/react-router";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, GroupId, GroupRosterClient } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { dayOfWeekOf, todayLocalDate } from "@/lib/localDate";
import { cn } from "@/lib/utils";
import {
  defaultJournalPeriod,
  journalRows,
  shiftJournalPeriod,
  type JournalCell,
  type JournalPeriod,
} from "./groupOverview";
import { useGroupAttendance } from "./groupsQueries";

/**
 * Вкладка «Журнал»: матрица посещаемости группы за период — закрытые занятия столбцами, строки —
 * текущий состав [roster] и исключённые участники, отмеченные в периоде. Период по умолчанию —
 * последние 4 недели, переключается назад и вперёд.
 */
export function GroupJournalTab({
  api,
  branchId,
  groupId,
  roster,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly groupId: GroupId;
  readonly roster: readonly GroupRosterClient[];
}) {
  const { t, format } = useI18n();
  const [period, setPeriod] = useState<JournalPeriod>(() => defaultJournalPeriod(todayLocalDate()));
  const query = useGroupAttendance(api, branchId, groupId, period.from, period.to);
  const data = query.data;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-end gap-2">
        <Button
          variant="outline"
          size="icon-sm"
          aria-label={t("groups.sessions.prevPeriod")}
          onClick={() => {
            setPeriod(shiftJournalPeriod(period, -1));
          }}
        >
          <ChevronLeftIcon aria-hidden />
        </Button>
        <span className="text-sm whitespace-nowrap tabular-nums">
          {t("groups.sessions.range", {
            from: format.date(period.from),
            to: format.date(period.to),
          })}
        </span>
        <Button
          variant="outline"
          size="icon-sm"
          aria-label={t("groups.sessions.nextPeriod")}
          onClick={() => {
            setPeriod(shiftJournalPeriod(period, 1));
          }}
        >
          <ChevronRightIcon aria-hidden />
        </Button>
      </div>

      {query.isError && <FormAlert message={t("groups.journal.loadError")} />}
      {query.isPending && <Skeleton className="h-48 w-full" />}
      {data?.sessions.length === 0 && (
        <p className="text-sm text-muted-foreground">{t("groups.journal.empty")}</p>
      )}
      {data !== undefined && data.sessions.length > 0 && (
        <div
          className={cn(
            "overflow-x-auto rounded-xl border bg-card",
            query.isPlaceholderData && "opacity-60",
          )}
        >
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                <th className="sticky left-0 bg-card px-4 py-2 text-left text-xs font-medium text-muted-foreground">
                  {t("groups.journal.client")}
                </th>
                {data.sessions.map((session) => (
                  <th
                    key={session.id}
                    className="px-1 py-2 text-center text-[11px] leading-tight font-medium text-muted-foreground"
                  >
                    {t(`day.${dayOfWeekOf(session.date)}`)}
                    <br />
                    {format.date(session.date)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {journalRows(roster, data).map((row) => (
                <tr key={row.clientId} className="border-t">
                  <th
                    scope="row"
                    className="sticky left-0 bg-card px-4 py-2 text-left font-medium whitespace-nowrap"
                  >
                    {row.name}
                    {row.former && (
                      <span className="ml-2 text-xs font-normal text-muted-foreground">
                        {t("groups.journal.former")}
                      </span>
                    )}
                  </th>
                  {row.cells.map((cell, index) => (
                    <td key={data.sessions[index]?.id ?? index} className="h-10 px-1 text-center">
                      <Cell cell={cell} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/**
 * Клетка журнала [cell]: «пришёл» и «не пришёл» ведут в журнал занятия, метки — в подсказке;
 * «не участвовал» — нейтральный прочерк без перехода.
 */
function Cell({ cell }: { readonly cell: JournalCell }) {
  const { t } = useI18n();
  if (cell.kind === "none") {
    return (
      <span title={t("groups.journal.none")} className="text-muted-foreground/60">
        —<span className="sr-only">{t("groups.journal.none")}</span>
      </span>
    );
  }
  const status = t(cell.kind === "present" ? "groups.journal.present" : "groups.journal.absent");
  const label = [status, ...cell.labels.map((l) => l.name)].join(" · ");
  return (
    <Link
      to="/sessions/$sessionId"
      params={{ sessionId: cell.sessionId }}
      title={label}
      aria-label={label}
      className="inline-flex size-6 items-center justify-center rounded-full hover:bg-accent"
    >
      <span
        aria-hidden
        className={cn(
          "inline-block size-3.5 rounded-full",
          cell.kind === "present" ? "bg-primary" : "ring-2 ring-muted-foreground/50 ring-inset",
        )}
      />
    </Link>
  );
}
