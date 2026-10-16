import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient, ApiResult } from "@/api/client";
import type {
  AttendanceLabelId,
  AttendanceLabelSchema,
  BranchId,
  ClientId,
  EmployeeId,
  HallId,
  ScheduleCoachSchema,
  SessionId,
  SessionJournalResponse,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { ClientPickerSheet } from "@/clients/ClientPickerSheet";
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
import { FormAlert } from "@/forms/FormAlert";
import { manualField } from "@/forms/field";
import { TextAreaField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { Avatar } from "@/ui/Avatar";
import { ChecklistSheet } from "@/ui/ChecklistSheet";
import { ChoiceSheet } from "@/ui/ChoiceSheet";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { completeBlock, isApplicable, unmarkedCount, type MarkDraft } from "./attendance";
import { SessionInfoPanel } from "./card/SessionInfoPanel";
import { SessionParticipantsSection } from "./card/SessionParticipantsSection";
import { SessionRescheduleSheet } from "./card/SessionRescheduleSheet";
import {
  cancelSession,
  changeCoaches,
  changeHall,
  rescheduleSession,
  sessionCard,
  setNote,
  type SessionCardOverrides,
} from "./sessionDetailMock";
import { isEditableStatus } from "./sessionStatus";

/** Какая из панелей и диалогов действий сейчас открыта. */
type ActionDialog =
  "reschedule" | "hall" | "coaches" | "note" | "markRemainingAbsent" | "addParticipant" | null;

/** Через сколько миллисекунд после команды журнала превью списаний запрашивается повторно. */
const DEBIT_REFRESH_DELAY_MS = 2000;

/**
 * Карточка занятия: слева — что за занятие (группа, дата, зал, тренеры, заполненность
 * журнала) и действия, справа — состав и журнал посещаемости. Данные занятия и журнал
 * приходят с сервера — состав, отметки, проведение занятия, массовая отметка отсутствующих,
 * разовые участники. Отмена, перенос, смена зала и тренеров и заметка пока меняют только
 * локальное состояние страницы (см. `sessionDetailMock.ts`).
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
      <section className="max-w-6xl space-y-4">
        <Skeleton className="h-8 w-24" />
        <div className="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)]">
          <Skeleton className="h-96 w-full" />
          <Skeleton className="h-96 w-full" />
        </div>
      </section>
    );
  }
  if (journal.data === undefined) {
    return (
      <section className="max-w-6xl space-y-4">
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
  const { t } = useI18n();
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
  const previewQuery = apiQuery(api, branchId, "sessions/debit-preview", {
    sessionId: journal.sessionId,
  });
  const debits = useQuery({
    ...previewQuery,
    select: (r) => new Map(r.participants.map((p) => [p.clientId, p.debit])),
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
    refreshDebits();
    return true;
  };

  /**
   * Обновляет превью списаний после команды журнала: сразу и ещё раз через
   * [DEBIT_REFRESH_DELAY_MS] — списания выполняются асинхронно обработчиком событий.
   */
  const refreshDebits = () => {
    const refresh = () => queryClient.invalidateQueries({ queryKey: previewQuery.queryKey });
    void refresh();
    setTimeout(() => {
      void refresh();
    }, DEBIT_REFRESH_DELAY_MS);
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

  const closeDialog = (open: boolean) => {
    if (!open) {
      setDialog(null);
    }
  };

  return (
    <section className="max-w-6xl space-y-4 pb-16">
      <Button variant="ghost" size="sm" asChild className="-ml-2">
        <Link to="/schedule">
          <ArrowLeftIcon aria-hidden />
          {t("action.back")}
        </Link>
      </Button>

      <div className="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)] lg:items-start">
        <aside className="lg:sticky lg:top-4">
          <SessionInfoPanel
            api={api}
            group={journal.group}
            card={card}
            employees={employees.data}
            editable={editable}
            block={block}
            marked={journal.participants.length - unmarked}
            total={journal.participants.length}
            onReschedule={() => {
              setDialog("reschedule");
            }}
            onChangeHall={() => {
              setDialog("hall");
            }}
            onChangeCoaches={() => {
              setDialog("coaches");
            }}
            onComplete={() => {
              void complete();
            }}
            onCancel={() => {
              setCancelling(true);
            }}
            onNote={() => {
              setDialog("note");
            }}
          />
        </aside>
        <SessionParticipantsSection
          api={api}
          participants={journal.participants}
          debits={debits.data}
          catalog={labels.data ?? []}
          editable={editable}
          journalOpen={journalOpen}
          unmarked={unmarked}
          onSaveMark={saveMark}
          onRemove={(clientId) => {
            void api
              .call("sessions/journal/remove-participant", { sessionId, clientId })
              .then(apply);
          }}
          onAdd={() => {
            setDialog("addParticipant");
          }}
          onMarkRemainingAbsent={() => {
            setDialog("markRemainingAbsent");
          }}
        />
      </div>

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

      <ClientPickerSheet
        api={api}
        branchId={branchId}
        open={dialog === "addParticipant"}
        onOpenChange={(open) => {
          setDialog(open ? "addParticipant" : null);
        }}
        title={t("sessionDetail.addParticipantAction")}
        mode="single"
        unavailable={
          new Map(
            journal.participants.map((participant) => [
              participant.clientId,
              t("sessionDetail.alreadyParticipant"),
            ]),
          )
        }
        onSubmit={async ([client]) => {
          if (client === undefined) {
            return null;
          }
          const result = await api.call("sessions/journal/add-participant", {
            sessionId,
            clientId: client.id,
          });
          if (!result.ok) {
            return apiErrorMessage(t, result.error);
          }
          queryClient.setQueryData(journalKey, result.value);
          return null;
        }}
      />

      <SessionRescheduleSheet
        open={dialog === "reschedule"}
        onOpenChange={closeDialog}
        card={card}
        onSave={(date, startTime, endTime) => {
          setOverrides((current) => rescheduleSession(current, date, startTime, endTime));
        }}
      />

      <ChoiceSheet
        open={dialog === "hall"}
        onOpenChange={closeDialog}
        title={t("sessionDetail.changeHallDialogTitle")}
        items={halls.data ?? []}
        current={card.hall.id}
        emptyText={t("sessionDetail.hallsEmpty")}
        onChoose={(hallId: HallId | null) => {
          const hall = (halls.data ?? []).find((h) => h.id === hallId);
          if (hall !== undefined) {
            setOverrides((current) => changeHall(current, { id: hall.id, name: hall.name }));
          }
          return Promise.resolve(null);
        }}
      />

      <ChecklistSheet
        open={dialog === "coaches"}
        onOpenChange={closeDialog}
        title={t("sessionDetail.changeCoachesDialogTitle")}
        items={(employees.data ?? []).map((employee) => ({
          id: employee.id,
          name: employee.name,
          avatar: <Avatar api={api} uploadId={employee.avatarId} name={employee.name} />,
        }))}
        selected={card.coaches.map((coach) => coach.id)}
        emptyText={t("sessionDetail.coachesEmpty")}
        onSubmit={(selected: readonly EmployeeId[]) => {
          const byId = new Map((employees.data ?? []).map((employee) => [employee.id, employee]));
          const coaches: ScheduleCoachSchema[] = selected.flatMap((id) => {
            const employee = byId.get(id);
            return employee === undefined ? [] : [{ id: employee.id, name: employee.name }];
          });
          setOverrides((current) => changeCoaches(current, coaches));
          return Promise.resolve(null);
        }}
      />

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
