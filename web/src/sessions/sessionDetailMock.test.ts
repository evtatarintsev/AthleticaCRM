import { describe, expect, it } from "vitest";
import { LocalDateSchema, LocalTimeSchema, SessionIdSchema } from "@/api/generated/contracts";
import { uuidv7 } from "@/lib/uuid";
import {
  cancelSession,
  changeCoaches,
  changeHall,
  mockSessionDetail,
  rescheduleSession,
  setAttendance,
  setNote,
} from "./sessionDetailMock";

const sessionId = (value: string) => SessionIdSchema.parse(value);

describe("mockSessionDetail", () => {
  it("возвращает одну и ту же фикстуру для одного и того же id", () => {
    const id = sessionId(uuidv7());
    expect(mockSessionDetail(id)).toEqual(mockSessionDetail(id));
  });

  it("даёт фикстуры с разным статусом для разных id", () => {
    const statuses = new Set(
      [
        "00000000-0000-7000-8000-000000000000",
        "00000000-0000-7000-8000-000000000001",
        "00000000-0000-7000-8000-000000000002",
      ].map((value) => mockSessionDetail(sessionId(value)).status),
    );
    expect(statuses.size).toBeGreaterThan(1);
  });

  it("подставляет запрошенный id в данные фикстуры", () => {
    const id = sessionId(uuidv7());
    expect(mockSessionDetail(id).sessionId).toBe(id);
  });
});

describe("переходы состояния карточки занятия", () => {
  const detail = mockSessionDetail(sessionId(uuidv7()));

  it("cancelSession переводит статус в CANCELLED", () => {
    expect(cancelSession(detail).status).toBe("CANCELLED");
  });

  it("rescheduleSession меняет только дату и время", () => {
    const date = LocalDateSchema.parse("2030-01-01");
    const startTime = LocalTimeSchema.parse("10:00");
    const endTime = LocalTimeSchema.parse("11:00");
    const next = rescheduleSession(detail, date, startTime, endTime);
    expect(next).toEqual({ ...detail, date, startTime, endTime });
  });

  it("changeHall меняет только зал", () => {
    const hall = { id: detail.hall.id, name: "Новый зал" };
    expect(changeHall(detail, hall)).toEqual({ ...detail, hall });
  });

  it("changeCoaches меняет только список тренеров", () => {
    const coach = detail.coaches[0];
    if (coach === undefined) {
      throw new Error("фикстура без тренеров");
    }
    const coaches = [{ id: coach.id, name: "Новый тренер" }];
    expect(changeCoaches(detail, coaches)).toEqual({ ...detail, coaches });
  });

  it("setNote меняет только заметку", () => {
    expect(setNote(detail, "Новая заметка")).toEqual({ ...detail, note: "Новая заметка" });
  });

  it("setAttendance меняет статус одного участника, остальных не трогает", () => {
    const participant = detail.participants[0];
    if (participant === undefined) {
      throw new Error("фикстура без участников");
    }
    const next = setAttendance(detail, participant.id, "SICK");
    expect(next.participants[0]).toEqual({ ...participant, attendance: "SICK" });
    expect(next.participants.slice(1)).toEqual(detail.participants.slice(1));
  });
});
