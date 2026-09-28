import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  LocalDateSchema,
  LocalTimeSchema,
  type EmployeeId,
  type LocalDate,
  type LocalTime,
  type ScheduleCoachSchema,
  type ScheduleHallSchema,
  type SessionId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { manualField } from "@/forms/field";
import { TextAreaField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { MultiSelectPicker } from "@/ui/MultiSelectPicker";
import { PageHeader } from "@/ui/PageHeader";
import { AttendanceToggle } from "./AttendanceToggle";
import {
  cancelSession,
  changeCoaches,
  changeHall,
  mockSessionDetail,
  rescheduleSession,
  setAttendance,
  setNote,
  type MockSessionDetail,
} from "./sessionDetailMock";
import { isEditableStatus, sessionStatusLabelKey } from "./sessionStatus";

/** Какой из диалогов действий сейчас открыт. */
type ActionDialog = "reschedule" | "hall" | "coaches" | "note" | null;

/**
 * Карточка занятия: данные, действия над занятием и посещаемость участников.
 * Данные — мок, детерминированно порождённый по [sessionId] (см. `sessionDetailMock.ts`);
 * действия меняют только локальный `state` страницы, ничего не отправляют на сервер.
 */
export function SessionDetailPage({
  api,
  sessionId,
}: {
  readonly api: ApiClient;
  readonly sessionId: SessionId;
}) {
  const { t, format } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const halls = useQuery({
    ...apiQuery(api, branchId, "halls/list"),
    select: (r) => r.halls,
  });
  const employees = useQuery({
    ...apiQuery(api, branchId, "employees/list"),
    select: (r) => r.employees,
  });

  const [detail, setDetail] = useState<MockSessionDetail>(() => mockSessionDetail(sessionId));
  const [dialog, setDialog] = useState<ActionDialog>(null);
  const [cancelling, setCancelling] = useState(false);

  const editable = isEditableStatus(detail.status);

  return (
    <section className="max-w-2xl space-y-6 pb-16">
      <Button variant="ghost" size="sm" asChild className="-ml-2">
        <Link to="/schedule">
          <ArrowLeftIcon aria-hidden />
          {t("action.back")}
        </Link>
      </Button>

      <PageHeader
        title={detail.group.name}
        actions={<SessionStatusBadge status={detail.status} />}
      />

      <div className="space-y-1 text-sm text-muted-foreground">
        <p>
          {format.date(detail.date)} · {format.time(detail.startTime)}–{format.time(detail.endTime)}
        </p>
        <p>{t("home.hallLabel", { name: detail.hall.name })}</p>
      </div>

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("sessionDetail.coachesTitle")}
        </h2>
        {detail.coaches.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("sessionDetail.noCoaches")}</p>
        ) : (
          <p className="text-sm">{detail.coaches.map((coach) => coach.name).join(", ")}</p>
        )}
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("sessionDetail.actionsTitle")}
        </h2>
        <div className="flex flex-wrap gap-2">
          {editable && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setCancelling(true);
              }}
            >
              {t("sessionDetail.cancelAction")}
            </Button>
          )}
          {editable && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setDialog("reschedule");
              }}
            >
              {t("sessionDetail.rescheduleAction")}
            </Button>
          )}
          {editable && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setDialog("hall");
              }}
            >
              {t("sessionDetail.changeHallAction")}
            </Button>
          )}
          {editable && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setDialog("coaches");
              }}
            >
              {t("sessionDetail.changeCoachesAction")}
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setDialog("note");
            }}
          >
            {t("sessionDetail.noteAction")}
          </Button>
        </div>
        {detail.note !== "" && <p className="text-sm whitespace-pre-wrap">{detail.note}</p>}
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("sessionDetail.participantsTitle")}
        </h2>
        {detail.participants.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("sessionDetail.noParticipants")}</p>
        ) : (
          <ul className="divide-y rounded-md border">
            {detail.participants.map((participant) => (
              <li
                key={participant.id}
                className="flex flex-wrap items-center justify-between gap-2 px-4 py-3"
              >
                <span className="text-sm">{participant.name}</span>
                <AttendanceToggle
                  value={participant.attendance}
                  onChange={(attendance) => {
                    setDetail((current) => setAttendance(current, participant.id, attendance));
                  }}
                />
              </li>
            ))}
          </ul>
        )}
      </section>

      <ConfirmDialog
        open={cancelling}
        onOpenChange={setCancelling}
        title={t("sessionDetail.cancelConfirmTitle")}
        description={t("sessionDetail.cancelConfirmText")}
        confirmLabel={t("sessionDetail.cancelAction")}
        onConfirm={() => {
          setDetail((current) => cancelSession(current));
          return Promise.resolve();
        }}
      />

      {dialog === "reschedule" && (
        <RescheduleDialog
          detail={detail}
          onSave={(date, startTime, endTime) => {
            setDetail((current) => rescheduleSession(current, date, startTime, endTime));
            setDialog(null);
          }}
          onClose={() => {
            setDialog(null);
          }}
        />
      )}

      {dialog === "hall" && (
        <ChangeHallDialog
          halls={halls.data ?? []}
          value={detail.hall}
          onSave={(hall) => {
            setDetail((current) => changeHall(current, hall));
            setDialog(null);
          }}
          onClose={() => {
            setDialog(null);
          }}
        />
      )}

      {dialog === "coaches" && (
        <MultiSelectPicker
          open
          title={t("sessionDetail.changeCoachesDialogTitle")}
          items={employees.data ?? []}
          selected={detail.coaches.map((coach) => coach.id)}
          emptyText={t("groups.picker.empty")}
          onOpenChange={(open) => {
            if (!open) {
              setDialog(null);
            }
          }}
          onApply={(selected: readonly EmployeeId[]) => {
            const byId = new Map((employees.data ?? []).map((employee) => [employee.id, employee]));
            const coaches: ScheduleCoachSchema[] = selected.flatMap((id) => {
              const employee = byId.get(id);
              return employee === undefined ? [] : [{ id: employee.id, name: employee.name }];
            });
            setDetail((current) => changeCoaches(current, coaches));
          }}
        />
      )}

      {dialog === "note" && (
        <NoteDialog
          initialNote={detail.note}
          onSave={(note) => {
            setDetail((current) => setNote(current, note));
            setDialog(null);
          }}
          onClose={() => {
            setDialog(null);
          }}
        />
      )}
    </section>
  );
}

/** Цветной бейдж статуса занятия. */
function SessionStatusBadge({ status }: { readonly status: MockSessionDetail["status"] }) {
  const { t } = useI18n();
  const color = status === "COMPLETED" ? "#388E3C" : status === "CANCELLED" ? "#D32F2F" : "#1976D2";
  return (
    <span
      className="inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium text-white"
      style={{ backgroundColor: color }}
    >
      {t(sessionStatusLabelKey(status))}
    </span>
  );
}

/** Диалог переноса занятия: новая дата и время. */
function RescheduleDialog({
  detail,
  onSave,
  onClose,
}: {
  readonly detail: MockSessionDetail;
  readonly onSave: (date: LocalDate, startTime: LocalTime, endTime: LocalTime) => void;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const [date, setDate] = useState(detail.date);
  const [startTime, setStartTime] = useState(detail.startTime);
  const [endTime, setEndTime] = useState(detail.endTime);
  const canSave = startTime < endTime;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{t("sessionDetail.rescheduleDialogTitle")}</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <label className="block space-y-1.5">
            <span className="text-sm font-medium">{t("sessionDetail.field.date")}</span>
            <input
              type="date"
              value={date}
              onChange={(event) => {
                setDate(localDateValue(event.target.value, date));
              }}
              className="w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
            />
          </label>
          <div className="grid grid-cols-2 gap-2">
            <label className="block space-y-1.5">
              <span className="text-sm font-medium">{t("sessionDetail.field.startTime")}</span>
              <input
                type="time"
                value={startTime}
                onChange={(event) => {
                  setStartTime(localTimeValue(event.target.value, startTime));
                }}
                className="w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
              />
            </label>
            <label className="block space-y-1.5">
              <span className="text-sm font-medium">{t("sessionDetail.field.endTime")}</span>
              <input
                type="time"
                value={endTime}
                onChange={(event) => {
                  setEndTime(localTimeValue(event.target.value, endTime));
                }}
                className="w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
              />
            </label>
          </div>
        </div>
        <DialogFooter closeLabel={t("action.cancel")}>
          <Button
            disabled={!canSave}
            onClick={() => {
              onSave(date, startTime, endTime);
            }}
          >
            {t("action.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Диалог смены зала занятия. */
function ChangeHallDialog({
  halls,
  value,
  onSave,
  onClose,
}: {
  readonly halls: readonly ScheduleHallSchema[];
  readonly value: ScheduleHallSchema;
  readonly onSave: (hall: ScheduleHallSchema) => void;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const options = halls.length === 0 ? [value] : halls;
  const initial = options.find((hall) => hall.id === value.id) ?? options[0] ?? value;
  const [hallId, setHallId] = useState(initial.id);
  const selected = options.find((hall) => hall.id === hallId) ?? initial;

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{t("sessionDetail.changeHallDialogTitle")}</DialogTitle>
        </DialogHeader>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium">{t("sessionDetail.field.hall")}</span>
          <NativeSelect
            value={hallId}
            onChange={(event) => {
              const hall = options.find((h) => h.id === event.target.value);
              if (hall !== undefined) {
                setHallId(hall.id);
              }
            }}
          >
            {options.map((hall) => (
              <option key={hall.id} value={hall.id}>
                {hall.name}
              </option>
            ))}
          </NativeSelect>
        </label>
        <DialogFooter closeLabel={t("action.cancel")}>
          <Button
            onClick={() => {
              onSave(selected);
            }}
          >
            {t("action.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Диалог заметки к занятию. */
function NoteDialog({
  initialNote,
  onSave,
  onClose,
}: {
  readonly initialNote: string;
  readonly onSave: (note: string) => void;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const [note, setNoteValue] = useState(initialNote);

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{t("sessionDetail.noteAction")}</DialogTitle>
        </DialogHeader>
        <TextAreaField
          field={manualField("note", note, setNoteValue)}
          label={t("sessionDetail.noteFieldLabel")}
          rows={4}
        />
        <DialogFooter closeLabel={t("action.cancel")}>
          <Button
            onClick={() => {
              onSave(note);
            }}
          >
            {t("action.save")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Значение `<input type="date">` [raw] как `LocalDate`; некорректное значение не меняет [fallback]. */
function localDateValue(raw: string, fallback: LocalDate): LocalDate {
  const parsed = LocalDateSchema.safeParse(raw);
  return parsed.success ? parsed.data : fallback;
}

/** Значение `<input type="time">` [raw] как `LocalTime`; некорректное значение не меняет [fallback]. */
function localTimeValue(raw: string, fallback: LocalTime): LocalTime {
  const parsed = LocalTimeSchema.safeParse(raw);
  return parsed.success ? parsed.data : fallback;
}
