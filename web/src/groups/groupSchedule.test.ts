import { describe, expect, it } from "vitest";
import {
  DayOfWeekSchema,
  HallIdSchema,
  LocalTimeSchema,
  type ScheduleSlot,
} from "@/api/generated/contracts";
import { uuidv7 } from "@/lib/uuid";
import {
  cardErrors,
  cardsToSlotInputs,
  newCard,
  slotsToCards,
  type SlotCard,
} from "./groupSchedule";

const hallA = HallIdSchema.parse(uuidv7());
const hallB = HallIdSchema.parse(uuidv7());
const day = (value: string) => DayOfWeekSchema.parse(value);
const time = (value: string) => LocalTimeSchema.parse(value);

function slot(dayOfWeek: string, startAt: string, endAt: string, hallId = hallA): ScheduleSlot {
  return {
    dayOfWeek: day(dayOfWeek),
    startAt: time(startAt),
    endAt: time(endAt),
    hallId,
    hallName: null,
    validity: null,
  };
}

describe("slotsToCards", () => {
  it("группирует слоты с одинаковым временем и залом в одну карточку", () => {
    const cards = slotsToCards([
      slot("MONDAY", "15:00", "17:00"),
      slot("WEDNESDAY", "15:00", "17:00"),
      slot("FRIDAY", "15:00", "17:00"),
    ]);
    expect(cards).toHaveLength(1);
    expect(cards[0]?.days).toEqual(new Set([day("MONDAY"), day("WEDNESDAY"), day("FRIDAY")]));
  });

  it("разные залы дают разные карточки", () => {
    const cards = slotsToCards([
      slot("MONDAY", "15:00", "17:00", hallA),
      slot("WEDNESDAY", "15:00", "17:00", hallB),
    ]);
    expect(cards).toHaveLength(2);
  });

  it("сортирует карточки по первому дню, затем по времени начала", () => {
    const cards = slotsToCards([
      slot("SATURDAY", "10:00", "12:00", hallB),
      slot("MONDAY", "18:00", "19:00"),
      slot("MONDAY", "09:00", "10:00"),
    ]);
    expect(cards.map((c) => c.startAt)).toEqual(["09:00", "18:00", "10:00"]);
  });
});

describe("cardsToSlotInputs", () => {
  it("разворачивает карточку в слот на каждый отмеченный день", () => {
    const card: SlotCard = {
      id: 0,
      days: new Set([day("MONDAY"), day("FRIDAY")]),
      startAt: "09:00",
      endAt: "10:00",
      hallId: hallA,
    };
    expect(cardsToSlotInputs([card])).toEqual([
      { dayOfWeek: day("MONDAY"), startAt: time("09:00"), endAt: time("10:00"), hallId: hallA },
      { dayOfWeek: day("FRIDAY"), startAt: time("09:00"), endAt: time("10:00"), hallId: hallA },
    ]);
  });

  it("карточка без зала не даёт слотов", () => {
    const card = newCard(0, null);
    expect(
      cardsToSlotInputs([
        { ...card, days: new Set([day("MONDAY")]), startAt: "09:00", endAt: "10:00" },
      ]),
    ).toEqual([]);
  });

  it("пустой список карточек даёт пустое расписание", () => {
    expect(cardsToSlotInputs([])).toEqual([]);
  });
});

describe("cardErrors", () => {
  it("карточка без дней недоступна для сохранения", () => {
    const card = newCard(0, hallA);
    expect(cardErrors([card]).get(0)).toContain("noDays");
  });

  it("карточка без зала недоступна для сохранения", () => {
    const card = { ...newCard(0, null), days: new Set([day("MONDAY")]) };
    expect(cardErrors([card]).get(0)).toContain("noHall");
  });

  it("окончание раньше начала — ошибка", () => {
    const card: SlotCard = {
      id: 0,
      days: new Set([day("MONDAY")]),
      startAt: "17:00",
      endAt: "15:00",
      hallId: hallA,
    };
    expect(cardErrors([card]).get(0)).toContain("endNotAfterStart");
  });

  it("одинаковый день и время начала в двух карточках помечает обе", () => {
    const a: SlotCard = {
      id: 0,
      days: new Set([day("MONDAY"), day("WEDNESDAY")]),
      startAt: "15:00",
      endAt: "17:00",
      hallId: hallA,
    };
    const b: SlotCard = {
      id: 1,
      days: new Set([day("MONDAY")]),
      startAt: "15:00",
      endAt: "16:00",
      hallId: hallA,
    };
    const errors = cardErrors([a, b]);
    expect(errors.get(0)).toContain("duplicate");
    expect(errors.get(1)).toContain("duplicate");
  });

  it("валидные непересекающиеся карточки без ошибок", () => {
    const a: SlotCard = {
      id: 0,
      days: new Set([day("MONDAY")]),
      startAt: "09:00",
      endAt: "10:00",
      hallId: hallA,
    };
    const b: SlotCard = {
      id: 1,
      days: new Set([day("MONDAY")]),
      startAt: "18:00",
      endAt: "19:00",
      hallId: hallA,
    };
    expect(cardErrors([a, b]).size).toBe(0);
  });

  it("пустой список карточек разрешён", () => {
    expect(cardErrors([]).size).toBe(0);
  });
});
