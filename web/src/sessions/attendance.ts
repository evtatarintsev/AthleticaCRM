import type {
  AttendanceLabelId,
  AttendanceLabelSchema,
  AttendancePresence,
  JournalParticipantSchema,
  SessionJournalResponse,
} from "@/api/generated/contracts";

/** Отметка участника в том виде, в каком её редактирует карточка: присутствие и метки. */
export interface MarkDraft {
  /** Присутствие; `null` — участник не отмечен. */
  readonly presence: AttendancePresence | null;
  /** Выбранные метки. */
  readonly labelIds: readonly AttendanceLabelId[];
}

/** Сохранённая отметка участника [participant]. */
export function savedMark(participant: JournalParticipantSchema): MarkDraft {
  return {
    presence: participant.presence,
    labelIds: participant.labels.map((label) => label.id),
  };
}

/** Применима ли метка [label] к присутствию [presence]. */
export function isApplicable(label: AttendanceLabelSchema, presence: AttendancePresence): boolean {
  switch (label.scope) {
    case "ANY":
      return true;
    case "PRESENT":
      return presence === "PRESENT";
    case "ABSENT":
      return presence === "ABSENT";
  }
}

/**
 * Метки, предлагаемые для присутствия [presence]: неархивные применимые метки справочника
 * [catalog] и уже стоящие на отметке метки [current] — архивная метка новым отметкам
 * не предлагается, но с проставленной не пропадает.
 */
export function offeredLabels(
  catalog: readonly AttendanceLabelSchema[],
  current: readonly AttendanceLabelSchema[],
  presence: AttendancePresence,
): readonly AttendanceLabelSchema[] {
  const offered = catalog.filter((label) => !label.isArchived && isApplicable(label, presence));
  const kept = current.filter(
    (label) => isApplicable(label, presence) && !offered.some((o) => o.id === label.id),
  );
  return [...offered, ...kept].sort((a, b) => a.position - b.position);
}

/**
 * Черновик [draft] с присутствием [presence]: метки, неприменимые к новому состоянию
 * по справочнику [labels], снимаются.
 */
export function withPresence(
  draft: MarkDraft,
  presence: AttendancePresence,
  labels: readonly AttendanceLabelSchema[],
): MarkDraft {
  const applicable = new Set(
    labels.filter((label) => isApplicable(label, presence)).map((label) => label.id),
  );
  return { presence, labelIds: draft.labelIds.filter((id) => applicable.has(id)) };
}

/** Черновик [draft] с переключённой меткой [labelId]. */
export function toggleLabel(draft: MarkDraft, labelId: AttendanceLabelId): MarkDraft {
  return {
    ...draft,
    labelIds: draft.labelIds.includes(labelId)
      ? draft.labelIds.filter((id) => id !== labelId)
      : [...draft.labelIds, labelId],
  };
}

/** Можно ли сохранить черновик [draft]: «не пришёл» требует хотя бы одной метки. */
export function canSave(draft: MarkDraft): boolean {
  return draft.presence !== "ABSENT" || draft.labelIds.length > 0;
}

/** Совпадают ли отметки [a] и [b] без учёта порядка меток. */
export function sameMark(a: MarkDraft, b: MarkDraft): boolean {
  return (
    a.presence === b.presence &&
    a.labelIds.length === b.labelIds.length &&
    a.labelIds.every((id) => b.labelIds.includes(id))
  );
}

/** Число неотмеченных участников журнала [journal]. */
export function unmarkedCount(journal: SessionJournalResponse): number {
  return journal.participants.filter((participant) => participant.presence === null).length;
}

/** Почему занятие нельзя провести: оно уже не запланировано, ещё не началось или журнал не заполнен. */
export type CompleteBlock = "notScheduled" | "notStarted" | "unmarked";

/** Препятствие к проведению занятия [journal]; `null` — провести можно. */
export function completeBlock(journal: SessionJournalResponse): CompleteBlock | null {
  if (journal.status !== "SCHEDULED") {
    return "notScheduled";
  }
  if (!journal.hasStarted) {
    return "notStarted";
  }
  return unmarkedCount(journal) > 0 ? "unmarked" : null;
}
