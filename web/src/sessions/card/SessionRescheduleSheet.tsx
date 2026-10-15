import { BellIcon } from "lucide-react";
import { useState } from "react";
import {
  LocalDateSchema,
  LocalTimeSchema,
  type LocalDate,
  type LocalTime,
} from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import type { SessionCard } from "../sessionDetailMock";

/** Классы полей даты и времени: как у `Input`. */
const INPUT_CLASSES =
  "w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30";

/**
 * Панель «Перенести занятие»: новая дата и время. Ниже полей — место под уведомление
 * участников о переносе, которого пока нет.
 */
export function SessionRescheduleSheet({
  open,
  onOpenChange,
  card,
  onSave,
}: {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly card: SessionCard;
  readonly onSave: (date: LocalDate, startTime: LocalTime, endTime: LocalTime) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t("sessionDetail.rescheduleDialogTitle")}
    >
      <RescheduleForm card={card} onSave={onSave} />
    </EditSheet>
  );
}

/** Форма переноса; монтируется заново при каждом открытии панели. */
function RescheduleForm({
  card,
  onSave,
}: {
  readonly card: SessionCard;
  readonly onSave: (date: LocalDate, startTime: LocalTime, endTime: LocalTime) => void;
}) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [date, setDate] = useState(card.date);
  const [startTime, setStartTime] = useState(card.startTime);
  const [endTime, setEndTime] = useState(card.endTime);
  const dirty = date !== card.date || startTime !== card.startTime || endTime !== card.endTime;
  const ordered = startTime < endTime;

  return (
    <EditSheetForm
      dirty={dirty}
      submitting={false}
      submitDisabled={!dirty || !ordered}
      submitLabel={t("sessionDetail.rescheduleAction")}
      onSubmit={() => {
        onSave(date, startTime, endTime);
        close();
      }}
    >
      <label className="block space-y-1.5">
        <span className="text-sm font-medium">{t("sessionDetail.field.date")}</span>
        <input
          type="date"
          value={date}
          onChange={(event) => {
            setDate(localDateValue(event.target.value, date));
          }}
          className={INPUT_CLASSES}
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
            className={INPUT_CLASSES}
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
            className={INPUT_CLASSES}
          />
        </label>
      </div>
      {!ordered && (
        <p role="alert" className="text-sm text-destructive">
          {t("sessionDetail.timeOrderError")}
        </p>
      )}
      <p className="flex items-center gap-2 rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
        <BellIcon aria-hidden className="size-4 shrink-0" />
        {t("sessionDetail.rescheduleNotifyStub")}
      </p>
    </EditSheetForm>
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
