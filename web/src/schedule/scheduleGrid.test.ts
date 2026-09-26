import { describe, expect, it } from "vitest";
import {
  GroupIdSchema,
  HallIdSchema,
  LocalDateSchema,
  LocalTimeSchema,
  SessionIdSchema,
  type ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { createTranslator } from "@/i18n/messages";
import { ru } from "@/i18n/ru";
import { uuidv7 } from "@/lib/uuid";
import { durationLabel, occupiedHours, sessionsAt } from "./scheduleGrid";

const t = createTranslator<typeof ru>("ru", ru);

const date = (value: string) => LocalDateSchema.parse(value);
const time = (value: string) => LocalTimeSchema.parse(value);

function session(dateValue: string, startTime: string, endTime: string): ScheduleSessionSchema {
  return {
    id: SessionIdSchema.parse(uuidv7()),
    group: { id: GroupIdSchema.parse(uuidv7()), name: "Группа" },
    date: date(dateValue),
    startTime: time(startTime),
    endTime: time(endTime),
    hall: { id: HallIdSchema.parse(uuidv7()), name: "Зал" },
    coaches: [],
    disciplines: [],
    status: "SCHEDULED",
    colorKey: "ORANGE",
  };
}

describe("occupiedHours", () => {
  it("возвращает только часы, в которые начинается хотя бы одно занятие", () => {
    const sessions = [
      session("2025-06-02", "09:00", "10:00"),
      session("2025-06-03", "18:30", "19:30"),
    ];
    expect(occupiedHours(sessions)).toEqual([9, 18]);
  });

  it("для недели без занятий возвращает пустой список", () => {
    expect(occupiedHours([])).toEqual([]);
  });
});

describe("sessionsAt", () => {
  it("сортирует несколько занятий одного часа по возрастанию времени начала", () => {
    const later = session("2025-06-02", "09:30", "10:00");
    const earlier = session("2025-06-02", "09:00", "09:30");
    expect(sessionsAt([later, earlier], "2025-06-02", 9)).toEqual([earlier, later]);
  });

  it("не возвращает занятия другого дня или часа", () => {
    const target = session("2025-06-02", "09:00", "10:00");
    const otherDay = session("2025-06-03", "09:00", "10:00");
    expect(sessionsAt([target, otherDay], "2025-06-02", 9)).toEqual([target]);
  });
});

describe("durationLabel", () => {
  it("опускает нулевую часть — только часы", () => {
    expect(durationLabel(t, time("09:00"), time("11:00"))).toBe(t("schedule.hours", { count: 2 }));
  });

  it("опускает нулевую часть — только минуты", () => {
    expect(durationLabel(t, time("09:00"), time("09:30"))).toBe(
      t("schedule.minutes", { count: 30 }),
    );
  });

  it("показывает обе части, когда обе ненулевые", () => {
    expect(durationLabel(t, time("09:00"), time("10:30"))).toBe(
      `${t("schedule.hours", { count: 1 })} ${t("schedule.minutes", { count: 30 })}`,
    );
  });
});
