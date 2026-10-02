import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon, XIcon } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import type { ApiClient, ApiResult } from "@/api/client";
import {
  LocalDateSchema,
  LocalTimeSchema,
  type AttendanceLabelId,
  type AttendanceLabelSchema,
  type BranchId,
  type ClientId,
  type EmployeeId,
  type JournalParticipantSchema,
  type LocalDate,
  type LocalTime,
  type ParticipationKind,
  type ScheduleCoachSchema,
  type ScheduleHallSchema,
  type SessionId,
  type SessionJournalResponse,
  type SessionStatus,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { FormAlert } from "@/forms/FormAlert";
import { manualField } from "@/forms/field";
import { TextAreaField } from "@/forms/fields";
import type { PlainMessageKey } from "@/i18n/context";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { MultiSelectPicker } from "@/ui/MultiSelectPicker";
import { PageHeader } from "@/ui/PageHeader";
import { completeBlock, isApplicable, unmarkedCount, type MarkDraft } from "./attendance";
import { ParticipantAttendance } from "./ParticipantAttendance";
import {
  cancelSession,
  changeCoaches,
  changeHall,
  rescheduleSession,
  sessionCard,
  setNote,
  type SessionCard,
  type SessionCardOverrides,
} from "./sessionDetailMock";
import { isEditableStatus, sessionStatusLabelKey } from "./sessionStatus";

/** Какой из диалогов действий сейчас открыт. */
type ActionDialog =
  "reschedule" | "hall" | "coaches" | "note" | "markRemainingAbsent" | "addParticipant" | null;

/**
 * Карточка занятия: данные занятия и журнал посещаемости с сервера — состав, отметки,
 * проведение занятия, массовая отметка отсутствующих, разовые участники.
 * Отмена, перенос, смена зала и тренеров и заметка пока меняют только локальное
 * состояние страницы (см. `sessionDetailMock.ts`).
 */
export function SessionDetailPage({
  api,
  sessionId,
}: {
  readonly api: ApiClient;
  readonly sessionId: SessionId;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const journalQuery = apiQuery(api, branchId, "sessions/journal", { sessionId });
  const journal = useQuery(journalQuery);

  if (journal.isPending) {
    return (
      <section className="max-w-2xl space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-24 w-full" />
      </section>
    );
  }
  if (journal.data === undefined) {
    return (
      <section className="max-w-2xl space-y-4">
        <FormAlert message={t("sessionDetail.loadError")} />
      </section>
    );
  }
  return (
    <SessionJournalCard
      api={api}
      branchId={branchId}
      journal={journal.data}
      journalKey={journalQuery.queryKey}
    />
  );
}

/** Карточка загруженного занятия [journal]; ответы команд журнала кладутся в кэш по [journalKey]. */
function SessionJournalCard({
  api,
  branchId,
  journal,
  journalKey,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly journal: SessionJournalResponse;
  readonly journalKey: ReturnType<typeof apiQuery<"sessions/journal">>["queryKey"];
}) {
  const { t, format } = useI18n();
  const queryClient = useQueryClient();
  const halls = useQuery({
    ...apiQuery(api, branchId, "halls/list"),
    select: (r) => r.halls,
  });
  const employees = useQuery({
    ...apiQuery(api, branchId, "employees/list"),
    select: (r) => r.employees,
  });
  const labels = useQuery({
    ...apiQuery(api, branchId, "attendance-labels/list", { includeArchived: false }),
    select: (r) => r.labels,
  });

  const [overrides, setOverrides] = useState<SessionCardOverrides>({});
  const [dialog, setDialog] = useState<ActionDialog>(null);
  const [cancelling, setCancelling] = useState(false);

  const card = sessionCard(journal, overrides);
  const editable = isEditableStatus(card.status);
  const journalOpen = card.status !== "CANCELLED";
  const block = card.status === journal.status ? completeBlock(journal) : "notScheduled";
  const unmarked = unmarkedCount(journal);
  const sessionId = journal.sessionId;

  /** Кладёт ответ команды журнала в кэш; ошибку показывает и возвращает `false`. */
  const apply = (result: ApiResult<SessionJournalResponse>): boolean => {
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return false;
    }
    queryClient.setQueryData(journalKey, result.value);
    return true;
  };

  const saveMark = async (clientId: ClientId, mark: MarkDraft) =>
    apply(
      await api.call("sessions/journal/mark", {
        sessionId,
        clientId,
        presence: mark.presence,
        labelIds: mark.labelIds,
      }),
    );

  const complete = async () => {
    if (apply(await api.call("sessions/complete", { sessionId }))) {
      toast.success(t("sessionDetail.completed"));
    }
  };

  return (
    <section className="max-w-2xl space-y-6 pb-16">
      <Button variant="ghost" size="sm" asChild className="-ml-2">
        <Link to="/schedule">
          <ArrowLeftIcon aria-hidden />
          {t("action.back")}
        </Link>
      </Button>

      <PageHeader
        title={journal.group.name}
        actions={<SessionStatusBadge status={card.status} />}
      />

      <div className="space-y-1 text-sm text-muted-foreground">
        <p>
          {format.date(card.date)} · {format.time(card.startTime)}–{format.time(card.endTime)}
        </p>
        <p>{t("home.hallLabel", { name: card.hall.name })}</p>
      </div>

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("sessionDetail.coachesTitle")}
        </h2>
        {card.coaches.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("sessionDetail.noCoaches")}</p>
        ) : (
          <p className="text-sm">{card.coaches.map((coach) => coach.name).join(", ")}</p>
        )}
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-medium text-muted-foreground">
          {t("sessionDetail.actionsTitle")}
        </h2>
        <div className="flex flex-wrap gap-2">
          {editable && (
            <Button
              size="sm"
              disabled={block !== null}
              onClick={() => {
                void complete();
              }}
            >
              {t("sessionDetail.completeAction")}
            </Button>
          )}
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
        {editable && block === "unmarked" && (
          <p className="text-sm text-muted-foreground">
            {t("sessionDetail.completeBlocked.unmarked", { count: unmarked })}
          </p>
        )}
        {editable && block === "notStarted" && (
          <p className="text-sm text-muted-foreground">
            {t("sessionDetail.completeBlocked.notStarted")}
          </p>
        )}
        {card.note !== "" && <p className="text-sm whitespace-pre-wrap">{card.note}</p>}
      </section>

      <section className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-medium text-muted-foreground">
            {t("sessionDetail.participantsTitle")}
          </h2>
          <div className="flex flex-wrap gap-2">
            {editable && unmarked > 0 && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setDialog("markRemainingAbsent");
                }}
              >
                {t("sessionDetail.markRemainingAbsentAction")}
              </Button>
            )}
            {editable && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setDialog("addParticipant");
                }}
              >
                {t("sessionDetail.addParticipantAction")}
              </Button>
            )}
          </div>
        </div>
        {journal.participants.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("sessionDetail.noParticipants")}</p>
        ) : (
          <ul className="divide-y rounded-md border">
            {journal.participants.map((participant) => (
              <li
                key={participant.clientId}
                aria-label={participant.name}
                className="flex flex-wrap items-start justify-between gap-2 px-4 py-3"
              >
                <div className="flex items-center gap-2">
                  <span className="text-sm">{participant.name}</span>
                  <ParticipationBadge kind={participant.kind} />
                  {editable && participant.kind === "ONE_TIME" && (
                    <button
                      type="button"
                      aria-label={t("sessionDetail.removeParticipantAria", {
                        name: participant.name,
                      })}
                      onClick={() => {
                        void api
                          .call("sessions/journal/remove-participant", {
                            sessionId,
                            clientId: participant.clientId,
                          })
                          .then(apply);
                      }}
                      className="rounded-full text-muted-foreground hover:text-destructive"
                    >
                      <XIcon aria-hidden className="size-3.5" />
                    </button>
                  )}
                </div>
                <ParticipantAttendance
                  participant={participant}
                  catalog={labels.data ?? []}
                  editable={journalOpen}
                  onSave={(mark) => saveMark(participant.clientId, mark)}
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
          setOverrides((current) => cancelSession(current));
          return Promise.resolve();
        }}
      />

      {dialog === "markRemainingAbsent" && (
        <MarkRemainingAbsentDialog
          labels={(labels.data ?? []).filter((label) => isApplicable(label, "ABSENT"))}
          onConfirm={async (labelId) => {
            if (
              apply(
                await api.call("sessions/journal/mark-remaining-absent", { sessionId, labelId }),
              )
            ) {
              setDialog(null);
            }
          }}
          onClose={() => {
            setDialog(null);
          }}
        />
      )}

      {dialog === "addParticipant" && (
        <AddParticipantSheet
          api={api}
          branchId={branchId}
          participants={journal.participants}
          onAdd={async (clientId) => {
            if (
              apply(await api.call("sessions/journal/add-participant", { sessionId, clientId }))
            ) {
              setDialog(null);
            }
          }}
          onClose={() => {
            setDialog(null);
          }}
        />
      )}

      {dialog === "reschedule" && (
        <RescheduleDialog
          card={card}
          onSave={(date, startTime, endTime) => {
            setOverrides((current) => rescheduleSession(current, date, startTime, endTime));
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
          value={card.hall}
          onSave={(hall) => {
            setOverrides((current) => changeHall(current, hall));
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
          selected={card.coaches.map((coach) => coach.id)}
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
            setOverrides((current) => changeCoaches(current, coaches));
          }}
        />
      )}

      {dialog === "note" && (
        <NoteDialog
          initialNote={card.note}
          onSave={(note) => {
            setOverrides((current) => setNote(current, note));
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
function SessionStatusBadge({ status }: { readonly status: SessionStatus }) {
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

/** Ключ словаря подписи вида участия [kind]. */
function participationLabelKey(kind: ParticipationKind): PlainMessageKey {
  switch (kind) {
    case "REGULAR":
      return "sessionDetail.participant.REGULAR";
    case "ONE_TIME":
      return "sessionDetail.participant.ONE_TIME";
  }
}

/** Бейдж вида участия: постоянный участник группы или разовый. */
function ParticipationBadge({ kind }: { readonly kind: ParticipationKind }) {
  const { t } = useI18n();
  return (
    <span className="rounded-full border px-1.5 py-0.5 text-xs text-muted-foreground">
      {t(participationLabelKey(kind))}
    </span>
  );
}

/** Диалог массовой отметки неотмеченных участников как «не пришёл» с меткой из [labels]. */
function MarkRemainingAbsentDialog({
  labels,
  onConfirm,
  onClose,
}: {
  readonly labels: readonly AttendanceLabelSchema[];
  readonly onConfirm: (labelId: AttendanceLabelId) => Promise<void>;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const [labelId, setLabelId] = useState<AttendanceLabelId | null>(null);
  const [pending, setPending] = useState(false);

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
          <DialogTitle>{t("sessionDetail.markRemainingAbsentTitle")}</DialogTitle>
          <DialogDescription>{t("sessionDetail.markRemainingAbsentText")}</DialogDescription>
        </DialogHeader>
        <label className="block space-y-1.5">
          <span className="text-sm font-medium">{t("sessionDetail.field.label")}</span>
          <NativeSelect
            value={labelId ?? ""}
            onChange={(event) => {
              setLabelId(labels.find((label) => label.id === event.target.value)?.id ?? null);
            }}
          >
            <option value="">{t("sessionDetail.selectLabel")}</option>
            {labels.map((label) => (
              <option key={label.id} value={label.id}>
                {label.name}
              </option>
            ))}
          </NativeSelect>
        </label>
        <DialogFooter closeLabel={t("action.cancel")}>
          <Button
            disabled={labelId === null || pending}
            onClick={() => {
              if (labelId !== null) {
                setPending(true);
                void onConfirm(labelId).then(() => {
                  setPending(false);
                });
              }
            }}
          >
            {t("sessionDetail.markRemainingAbsentAction")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Шторка выбора клиента организации для разового участия. Клиенты, уже числящиеся
 * в составе [participants], показаны, но выбрать их нельзя.
 */
function AddParticipantSheet({
  api,
  branchId,
  participants,
  onAdd,
  onClose,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly participants: readonly JournalParticipantSchema[];
  readonly onAdd: (clientId: ClientId) => Promise<void>;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const [query, setQuery] = useState("");
  const [addingId, setAddingId] = useState<ClientId | null>(null);
  const name = query.trim() === "" ? null : query.trim();
  const clients = useQuery({
    ...apiQuery(api, branchId, "clients/list", { name, limit: 20 }),
    select: (r) => r.clients,
  });
  const inRoster = useMemo(
    () => new Set(participants.map((participant) => participant.clientId)),
    [participants],
  );

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <SheetContent
        side="bottom"
        className="max-h-[85vh] overflow-y-auto"
        closeLabel={t("action.close")}
      >
        <SheetHeader>
          <SheetTitle>{t("sessionDetail.addParticipantAction")}</SheetTitle>
          <SheetDescription className="sr-only">
            {t("sessionDetail.addParticipantAction")}
          </SheetDescription>
        </SheetHeader>
        <div className="space-y-3 px-4 pb-4">
          <Input
            type="search"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
            }}
            placeholder={t("sessionDetail.addParticipantSearch")}
            aria-label={t("sessionDetail.addParticipantSearch")}
          />
          {clients.data !== undefined &&
            (clients.data.length === 0 ? (
              <p className="py-6 text-center text-sm text-muted-foreground">
                {t("sessionDetail.addParticipantEmpty")}
              </p>
            ) : (
              <ul className="max-h-96 divide-y overflow-y-auto">
                {clients.data.map((client) => {
                  const present = inRoster.has(client.id);
                  return (
                    <li key={client.id}>
                      <button
                        type="button"
                        disabled={present || addingId !== null}
                        onClick={() => {
                          setAddingId(client.id);
                          void onAdd(client.id).then(() => {
                            setAddingId(null);
                          });
                        }}
                        className="flex w-full items-center justify-between px-1 py-3 text-left text-sm hover:bg-accent disabled:opacity-50"
                      >
                        <span>{client.name}</span>
                        {present && (
                          <span className="text-xs text-muted-foreground">
                            {t("sessionDetail.alreadyParticipant")}
                          </span>
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}

/** Диалог переноса занятия: новая дата и время. */
function RescheduleDialog({
  card,
  onSave,
  onClose,
}: {
  readonly card: SessionCard;
  readonly onSave: (date: LocalDate, startTime: LocalTime, endTime: LocalTime) => void;
  readonly onClose: () => void;
}) {
  const { t } = useI18n();
  const [date, setDate] = useState(card.date);
  const [startTime, setStartTime] = useState(card.startTime);
  const [endTime, setEndTime] = useState(card.endTime);
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
