import { describe, expect, it } from "vitest";
import {
  EmployeeIdSchema,
  HallIdSchema,
  LocalDateSchema,
  LocalTimeSchema,
  SessionJournalResponseSchema,
} from "@/api/generated/contracts";
import {
  cancelSession,
  changeCoaches,
  changeHall,
  rescheduleSession,
  sessionCard,
  setNote,
} from "./sessionDetailMock";

const journal = SessionJournalResponseSchema.parse({
  sessionId: "0199a0b2-0000-7000-8000-500000000001",
  group: { id: "0199a0b2-0000-7000-8000-100000000001", name: "Юниоры" },
  date: "2026-10-01",
  startTime: "17:00",
  endTime: "18:00",
  hall: { id: "0199a0b2-0000-7000-8000-200000000001", name: "Зал А" },
  coaches: [{ id: "0199a0b2-0000-7000-8000-300000000001", name: "Сергей" }],
  status: "SCHEDULED",
  hasStarted: true,
  participants: [],
});

describe("локальные изменения карточки занятия", () => {
  it("без изменений карточка совпадает с данными сервера", () => {
    expect(sessionCard(journal, {})).toEqual({
      status: journal.status,
      date: journal.date,
      startTime: journal.startTime,
      endTime: journal.endTime,
      hall: journal.hall,
      coaches: journal.coaches,
      note: "",
    });
  });

  it("cancelSession переводит статус в CANCELLED", () => {
    expect(sessionCard(journal, cancelSession({})).status).toBe("CANCELLED");
  });

  it("rescheduleSession меняет только дату и время", () => {
    const date = LocalDateSchema.parse("2030-01-01");
    const startTime = LocalTimeSchema.parse("10:00");
    const endTime = LocalTimeSchema.parse("11:00");
    expect(sessionCard(journal, rescheduleSession({}, date, startTime, endTime))).toEqual({
      ...sessionCard(journal, {}),
      date,
      startTime,
      endTime,
    });
  });

  it("changeHall меняет только зал", () => {
    const hall = { id: HallIdSchema.parse("0199a0b2-0000-7000-8000-200000000002"), name: "Зал Б" };
    expect(sessionCard(journal, changeHall({}, hall))).toEqual({
      ...sessionCard(journal, {}),
      hall,
    });
  });

  it("changeCoaches меняет только список тренеров", () => {
    const coaches = [
      { id: EmployeeIdSchema.parse("0199a0b2-0000-7000-8000-300000000002"), name: "Ирина" },
    ];
    expect(sessionCard(journal, changeCoaches({}, coaches))).toEqual({
      ...sessionCard(journal, {}),
      coaches,
    });
  });

  it("setNote меняет только заметку", () => {
    expect(sessionCard(journal, setNote({}, "Заметка"))).toEqual({
      ...sessionCard(journal, {}),
      note: "Заметка",
    });
  });
});
