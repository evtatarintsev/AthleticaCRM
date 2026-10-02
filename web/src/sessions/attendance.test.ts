import { describe, expect, it } from "vitest";
import {
  AttendanceLabelSchemaSchema,
  SessionJournalResponseSchema,
  type AttendanceLabelSchema,
} from "@/api/generated/contracts";
import {
  canSave,
  completeBlock,
  offeredLabels,
  sameMark,
  toggleLabel,
  withPresence,
} from "./attendance";

const label = (
  id: string,
  name: string,
  scope: AttendanceLabelSchema["scope"],
  position: number,
  isArchived = false,
) =>
  AttendanceLabelSchemaSchema.parse({
    id: `0199a0b2-0000-7000-8000-60000000000${id}`,
    name,
    scope,
    position,
    isArchived,
  });

const late = label("1", "Опоздал", "PRESENT", 1);
const sick = label("2", "Болеет", "ABSENT", 2);
const noUniform = label("3", "Без формы", "ANY", 3);
const old = label("4", "Старая", "ABSENT", 4, true);
const catalog = [late, sick, noUniform];

const journal = (status: "SCHEDULED" | "COMPLETED", hasStarted: boolean, presence: string | null) =>
  SessionJournalResponseSchema.parse({
    sessionId: "0199a0b2-0000-7000-8000-500000000001",
    group: { id: "0199a0b2-0000-7000-8000-100000000001", name: "Юниоры" },
    date: "2026-10-01",
    startTime: "17:00",
    endTime: "18:00",
    hall: { id: "0199a0b2-0000-7000-8000-200000000001", name: "Зал А" },
    coaches: [],
    status,
    hasStarted,
    participants: [
      {
        clientId: "0199a0b2-0000-7000-8000-400000000001",
        name: "Аня",
        kind: "REGULAR",
        presence,
        labels: [],
      },
    ],
  });

describe("метки отметки", () => {
  it("к присутствию предлагаются только метки присутствия и общие", () => {
    expect(offeredLabels(catalog, [], "PRESENT").map((l) => l.name)).toEqual([
      "Опоздал",
      "Без формы",
    ]);
  });

  it("к отсутствию предлагаются только метки отсутствия и общие", () => {
    expect(offeredLabels(catalog, [], "ABSENT").map((l) => l.name)).toEqual([
      "Болеет",
      "Без формы",
    ]);
  });

  it("архивная метка не предлагается, но остаётся на проставленной отметке", () => {
    expect(offeredLabels([...catalog, old], [], "ABSENT")).not.toContain(old);
    expect(offeredLabels(catalog, [old], "ABSENT")).toContain(old);
  });

  it("смена присутствия снимает неприменимые метки", () => {
    const draft = { presence: "PRESENT" as const, labelIds: [late.id, noUniform.id] };
    expect(withPresence(draft, "ABSENT", catalog)).toEqual({
      presence: "ABSENT",
      labelIds: [noUniform.id],
    });
  });

  it("toggleLabel добавляет и снимает метку", () => {
    const draft = { presence: "ABSENT" as const, labelIds: [] };
    const withSick = toggleLabel(draft, sick.id);
    expect(withSick.labelIds).toEqual([sick.id]);
    expect(toggleLabel(withSick, sick.id).labelIds).toEqual([]);
  });

  it("«не пришёл» без метки сохранить нельзя, «пришёл» без метки — можно", () => {
    expect(canSave({ presence: "ABSENT", labelIds: [] })).toBe(false);
    expect(canSave({ presence: "ABSENT", labelIds: [sick.id] })).toBe(true);
    expect(canSave({ presence: "PRESENT", labelIds: [] })).toBe(true);
  });

  it("sameMark не зависит от порядка меток", () => {
    expect(
      sameMark(
        { presence: "PRESENT", labelIds: [late.id, noUniform.id] },
        { presence: "PRESENT", labelIds: [noUniform.id, late.id] },
      ),
    ).toBe(true);
  });
});

describe("проведение занятия", () => {
  it("недоступно при неотмеченных участниках", () => {
    expect(completeBlock(journal("SCHEDULED", true, null))).toBe("unmarked");
  });

  it("недоступно до начала занятия", () => {
    expect(completeBlock(journal("SCHEDULED", false, "PRESENT"))).toBe("notStarted");
  });

  it("недоступно для проведённого занятия", () => {
    expect(completeBlock(journal("COMPLETED", true, "PRESENT"))).toBe("notScheduled");
  });

  it("доступно, когда все отмечены", () => {
    expect(completeBlock(journal("SCHEDULED", true, "PRESENT"))).toBeNull();
  });
});
