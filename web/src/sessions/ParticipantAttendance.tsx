import { useState } from "react";
import type {
  AttendanceLabelSchema,
  AttendancePresence,
  JournalParticipantSchema,
} from "@/api/generated/contracts";
import type { PlainMessageKey } from "@/i18n/context";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import {
  canSave,
  offeredLabels,
  sameMark,
  savedMark,
  toggleLabel,
  withPresence,
  type MarkDraft,
} from "./attendance";

/** Состояния присутствия в порядке отображения переключателя. */
const PRESENCES: readonly AttendancePresence[] = ["PRESENT", "ABSENT"];

/** Ключ словаря подписи присутствия [presence]. */
function presenceLabelKey(presence: AttendancePresence): PlainMessageKey {
  switch (presence) {
    case "PRESENT":
      return "sessionDetail.attendance.PRESENT";
    case "ABSENT":
      return "sessionDetail.attendance.ABSENT";
  }
}

/**
 * Отметка одного участника: переключатель «пришёл / не пришёл» и метки, применимые
 * к выбранному состоянию. Неотмеченный участник показан состоянием «не отмечен».
 *
 * Изменение сохраняется сразу через [onSave] и до ответа сервера показывается как есть;
 * при ошибке отметка возвращается к последнему сохранённому значению. «Не пришёл» без
 * метки не сохраняется: страница просит выбрать метку.
 */
export function ParticipantAttendance({
  participant,
  catalog,
  editable,
  onSave,
}: {
  readonly participant: JournalParticipantSchema;
  readonly catalog: readonly AttendanceLabelSchema[];
  readonly editable: boolean;
  readonly onSave: (mark: MarkDraft) => Promise<boolean>;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState<MarkDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const saved = savedMark(participant);
  const shown = draft ?? saved;
  const known = [...catalog, ...participant.labels];

  const change = (next: MarkDraft) => {
    if (sameMark(next, saved)) {
      setDraft(null);
      return;
    }
    setDraft(next);
    if (!canSave(next)) {
      return;
    }
    setSaving(true);
    void onSave(next).then(() => {
      setSaving(false);
      setDraft(null);
    });
  };

  const disabled = !editable || saving;

  return (
    <div className="space-y-2">
      <div
        role="group"
        aria-label={t("sessionDetail.attendance.groupAria", { name: participant.name })}
        className="flex flex-wrap items-center gap-1"
      >
        {shown.presence === null && (
          <span className="mr-1 text-xs text-muted-foreground">
            {t("sessionDetail.attendance.unmarked")}
          </span>
        )}
        {PRESENCES.map((presence) => {
          const active = shown.presence === presence;
          return (
            <button
              key={presence}
              type="button"
              aria-pressed={active}
              disabled={disabled}
              onClick={() => {
                change(withPresence(shown, presence, known));
              }}
              className={cn(
                "rounded-md border px-2 py-1 text-xs font-medium disabled:opacity-60",
                active
                  ? presence === "PRESENT"
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-destructive bg-destructive text-white"
                  : "text-muted-foreground hover:bg-accent",
              )}
            >
              {t(presenceLabelKey(presence))}
            </button>
          );
        })}
      </div>
      {shown.presence !== null && (
        <div
          role="group"
          aria-label={t("sessionDetail.attendance.labelsAria", { name: participant.name })}
          className="flex flex-wrap gap-1"
        >
          {offeredLabels(catalog, participant.labels, shown.presence).map((label) => {
            const active = shown.labelIds.includes(label.id);
            return (
              <button
                key={label.id}
                type="button"
                aria-pressed={active}
                disabled={disabled}
                onClick={() => {
                  change(toggleLabel(shown, label.id));
                }}
                className={cn(
                  "rounded-full border px-2 py-0.5 text-xs disabled:opacity-60",
                  active
                    ? "border-foreground bg-secondary text-secondary-foreground"
                    : "text-muted-foreground hover:bg-accent",
                )}
              >
                {label.name}
              </button>
            );
          })}
        </div>
      )}
      {!canSave(shown) && (
        <p role="alert" className="text-xs text-destructive">
          {t("sessionDetail.attendance.labelRequired")}
        </p>
      )}
    </div>
  );
}
