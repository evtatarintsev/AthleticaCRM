import { useCallback, useState } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import { errorText } from "../api/errorText";
import {
  LocalDateSchema,
  type ClientAppProfileSchema,
  type ClientAppScheduleResponse,
  type ClientAppScheduleSessionSchema,
  type LocalDate,
  type LocalTime,
} from "../api/generated/contracts";
import { useI18n } from "../i18n/context";
import { Failure } from "../ui/Failure";
import { useLoaded, type Loaded } from "../ui/useLoaded";

/** Дата [date], сдвинутая на [days] дней; считается в UTC, чтобы пояс браузера не сдвигал день. */
function shiftDate(date: LocalDate, days: number): LocalDate {
  const shifted = new Date(`${date}T00:00:00Z`);
  shifted.setUTCDate(shifted.getUTCDate() + days);
  return LocalDateSchema.parse(shifted.toISOString().slice(0, 10));
}

/** Время [time] без секунд: `10:00`. */
function hhmm(time: LocalTime) {
  return time.slice(0, 5);
}

/** Формат дня с днём недели на языке [locale]; дата без пояса форматируется в UTC. */
function dayFormat(locale: string) {
  return new Intl.DateTimeFormat(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: "UTC",
  });
}

/** Группа, зал и тренеры занятия [session] одной строкой. */
function Place({ session }: { session: ClientAppScheduleSessionSchema }) {
  return (
    <span className="muted">
      {[session.groupName, session.hallName, ...session.coaches]
        .filter((it) => it !== null)
        .join(" · ")}
    </span>
  );
}

/**
 * Блок ближайшего занятия профиля [profile] на главной, загруженного через [api]: дата, время,
 * группа, зал и тренеры; без ближайшего занятия — «Ближайших занятий нет» и ссылка на расписание.
 * [onOpen] — открыть расписание.
 */
export function NextSessionBlock({
  api,
  profile,
  onOpen,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onOpen: () => void;
}) {
  const { t, locale } = useI18n();
  const { orgId, clientId } = profile;
  const { state, reload } = useLoaded<ClientAppScheduleResponse>(
    useCallback(() => api.call("client-app/schedule", { orgId, clientId }), [api, orgId, clientId]),
  );
  const open = (
    <button type="button" className="link" onClick={onOpen}>
      {t("schedule.open")}
    </button>
  );
  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      break;
  }
  const next = state.value.next;
  if (next === null) {
    return (
      <section className="card">
        <h2>{t("schedule.next")}</h2>
        <p className="muted">{t("schedule.noNext")}</p>
        {open}
      </section>
    );
  }
  return (
    <section className="card">
      <h2>{t("schedule.next")}</h2>
      <p>
        <strong>
          {dayFormat(locale).format(new Date(next.date))}, {hhmm(next.startTime)}
        </strong>
        <br />
        <Place session={next} />
      </p>
      {next.absenceNoticeAt !== null && <p className="muted">{t("schedule.notified")}</p>}
      {open}
    </section>
  );
}

/** Пометки занятия [session]: перенос с прежним временем, отмена, проведение, предупреждение. */
function Marks({ session }: { session: ClientAppScheduleSessionSchema }) {
  const { t, locale } = useI18n();
  const moved = session.movedFrom;
  return (
    <>
      {moved !== null && (
        <span className="ending">
          {moved.date === session.date
            ? t("schedule.movedFrom", { from: hhmm(moved.startTime) })
            : t("schedule.movedFrom", {
                from: `${dayFormat(locale).format(new Date(moved.date))}, ${hhmm(moved.startTime)}`,
              })}
        </span>
      )}
      {session.status === "CANCELLED" && <span className="ending">{t("schedule.cancelled")}</span>}
      {session.status === "COMPLETED" && <span className="muted">{t("schedule.completed")}</span>}
      {session.absenceNoticeAt !== null && <span className="muted">{t("schedule.notified")}</span>}
    </>
  );
}

/**
 * Расписание профиля [profile] по неделям через [api]: занятия по дням с пометками и «Не придём» /
 * отмена предупреждения у запланированных и ещё не начавшихся. [onBack] — назад на главную.
 */
export function Schedule({
  api,
  profile,
  onBack,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onBack: () => void;
}) {
  const { t, locale } = useI18n();
  const { orgId, clientId } = profile;
  const [weekOf, setWeekOf] = useState<LocalDate | null>(null);
  const [busyOn, setBusyOn] = useState<Loaded<ClientAppScheduleResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { state, reload } = useLoaded<ClientAppScheduleResponse>(
    useCallback(
      () => api.call("client-app/schedule", { orgId, clientId, weekOf }),
      [api, orgId, clientId, weekOf],
    ),
  );

  const busy = busyOn !== null && busyOn === state;

  /**
   * «Не придём» или отмена предупреждения действием [path] по занятию [session]. Кнопки заблокированы,
   * пока не придёт обновлённое расписание: занятость привязана к состоянию загрузки на момент нажатия.
   */
  async function notice(
    path: "client-app/absence-notices/create" | "client-app/absence-notices/cancel",
    session: ClientAppScheduleSessionSchema,
  ) {
    setBusyOn(state);
    setError(null);
    const result = await api.call(path, { orgId, clientId, sessionId: session.sessionId });
    if (result.ok) {
      reload();
    } else {
      setBusyOn(null);
      setError(errorText(result.error, t));
    }
  }

  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      break;
  }
  const week = state.value;
  const days = dayFormat(locale);
  const range = new Intl.DateTimeFormat(locale, { day: "numeric", month: "long", timeZone: "UTC" });
  const dates = [...new Set(week.sessions.map((it) => it.date))];
  return (
    <section className="card">
      <h1>{t("schedule.title")}</h1>
      <div className="week">
        <button
          type="button"
          className="secondary"
          aria-label={t("schedule.previous")}
          onClick={() => {
            setWeekOf((it) => shiftDate(it ?? week.weekStart, -7));
          }}
        >
          ←
        </button>
        <span>
          {range.format(new Date(week.weekStart))} –{" "}
          {range.format(new Date(shiftDate(week.weekStart, 6)))}
        </span>
        <button
          type="button"
          className="secondary"
          aria-label={t("schedule.following")}
          onClick={() => {
            setWeekOf((it) => shiftDate(it ?? week.weekStart, 7));
          }}
        >
          →
        </button>
      </div>
      {dates.length === 0 && <p className="muted">{t("schedule.empty")}</p>}
      {dates.map((date) => (
        <div key={date} className="group">
          <h2>{days.format(new Date(date))}</h2>
          <ul className="sessions">
            {week.sessions
              .filter((it) => it.date === date)
              .map((it) => {
                const open = it.status === "SCHEDULED" && !it.hasStarted;
                return (
                  <li key={it.sessionId} className="session" aria-label={hhmm(it.startTime)}>
                    <div>
                      <strong>
                        {hhmm(it.startTime)}–{hhmm(it.endTime)}
                      </strong>
                      <Place session={it} />
                      <Marks session={it} />
                    </div>
                    {open &&
                      (it.absenceNoticeAt === null ? (
                        <button
                          type="button"
                          className="secondary"
                          disabled={busy}
                          onClick={() => void notice("client-app/absence-notices/create", it)}
                        >
                          {t("schedule.notify")}
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="link"
                          disabled={busy}
                          onClick={() => void notice("client-app/absence-notices/cancel", it)}
                        >
                          {t("schedule.cancelNotice")}
                        </button>
                      ))}
                  </li>
                );
              })}
          </ul>
        </div>
      ))}
      {error !== null && <p role="alert">{error}</p>}
      <button type="button" className="link" onClick={onBack}>
        {t("schedule.back")}
      </button>
    </section>
  );
}
