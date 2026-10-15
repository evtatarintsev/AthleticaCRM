import { Link } from "@tanstack/react-router";
import { UserPlusIcon, XIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  AttendanceLabelSchema,
  ClientId,
  JournalParticipantSchema,
  ParticipationKind,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import type { PlainMessageKey } from "@/i18n/context";
import { useI18n } from "@/i18n/context";
import { isPlainClick } from "@/lib/plainClick";
import { Avatar } from "@/ui/Avatar";
import type { MarkDraft } from "../attendance";
import { ParticipantAttendance } from "../ParticipantAttendance";
import { ParticipantClientSheet, type ParticipantRef } from "./ParticipantClientSheet";

/**
 * Правая колонка карточки занятия — состав и журнал посещаемости: аватар и имя участника,
 * вид участия, отметка. Клик по имени открывает упрощённую карточку клиента в боковой панели,
 * не уводя с занятия; ctrl/cmd-клик и средняя кнопка — полную карточку по ссылке. В заголовке — сколько отмечено,
 * массовая отметка оставшихся и добавление разового участника.
 * Аватаров в журнале пока нет — показываются инициалы.
 */
export function SessionParticipantsSection({
  api,
  participants,
  catalog,
  editable,
  journalOpen,
  unmarked,
  onSaveMark,
  onRemove,
  onAdd,
  onMarkRemainingAbsent,
}: {
  readonly api: ApiClient;
  readonly participants: readonly JournalParticipantSchema[];
  /** Справочник меток посещаемости. */
  readonly catalog: readonly AttendanceLabelSchema[];
  /** Можно ли менять состав (занятие запланировано). */
  readonly editable: boolean;
  /** Можно ли ставить отметки (занятие не отменено). */
  readonly journalOpen: boolean;
  /** Число неотмеченных участников. */
  readonly unmarked: number;
  readonly onSaveMark: (clientId: ClientId, mark: MarkDraft) => Promise<boolean>;
  readonly onRemove: (clientId: ClientId) => void;
  readonly onAdd: () => void;
  readonly onMarkRemainingAbsent: () => void;
}) {
  const { t } = useI18n();
  const total = participants.length;
  const [opened, setOpened] = useState<ParticipantRef | null>(null);

  return (
    <section className="rounded-lg border bg-card" aria-labelledby="session-participants-title">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2">
        <div className="flex items-baseline gap-2">
          <h2 id="session-participants-title" className="font-medium">
            {t("sessionDetail.participantsTitle")}
          </h2>
          {total > 0 && (
            <span className="text-sm text-muted-foreground tabular-nums">
              {t("sessionDetail.participantsMarked", { marked: total - unmarked, total })}
            </span>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          {editable && unmarked > 0 && (
            <Button variant="outline" size="sm" onClick={onMarkRemainingAbsent}>
              {t("sessionDetail.markRemainingAbsentAction")}
            </Button>
          )}
          {editable && (
            <Button variant="outline" size="sm" onClick={onAdd}>
              <UserPlusIcon aria-hidden />
              {t("sessionDetail.addParticipantAction")}
            </Button>
          )}
        </div>
      </div>
      {total === 0 ? (
        <p className="px-4 py-6 text-center text-sm text-muted-foreground">
          {t("sessionDetail.noParticipants")}
        </p>
      ) : (
        <ul className="divide-y">
          {participants.map((participant) => (
            <li
              key={participant.clientId}
              aria-label={participant.name}
              className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 px-4 py-3"
            >
              <div className="flex min-w-0 items-center gap-3">
                <Avatar api={api} uploadId={null} name={participant.name} className="size-9" />
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                    <Link
                      to="/clients/$clientId"
                      params={{ clientId: participant.clientId }}
                      aria-haspopup="dialog"
                      title={t("participantCard.linkHint")}
                      onClick={(event) => {
                        if (isPlainClick(event)) {
                          event.preventDefault();
                          setOpened({ clientId: participant.clientId, name: participant.name });
                        }
                      }}
                      className="truncate text-sm font-medium hover:underline"
                    >
                      {participant.name}
                    </Link>
                    {editable && participant.kind === "ONE_TIME" && (
                      <button
                        type="button"
                        aria-label={t("sessionDetail.removeParticipantAria", {
                          name: participant.name,
                        })}
                        onClick={() => {
                          onRemove(participant.clientId);
                        }}
                        className="rounded-full text-muted-foreground hover:text-destructive"
                      >
                        <XIcon aria-hidden className="size-3.5" />
                      </button>
                    )}
                  </div>
                  <ParticipationBadge kind={participant.kind} />
                </div>
              </div>
              <ParticipantAttendance
                participant={participant}
                catalog={catalog}
                editable={journalOpen}
                onSave={(mark) => onSaveMark(participant.clientId, mark)}
              />
            </li>
          ))}
        </ul>
      )}
      <ParticipantClientSheet
        api={api}
        participant={opened}
        onClose={() => {
          setOpened(null);
        }}
      />
    </section>
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

/** Подпись вида участия: постоянный участник группы или разовый. */
function ParticipationBadge({ kind }: { readonly kind: ParticipationKind }) {
  const { t } = useI18n();
  return (
    <span className="block text-xs text-muted-foreground">{t(participationLabelKey(kind))}</span>
  );
}
