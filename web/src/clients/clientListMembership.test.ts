import { describe, expect, it } from "vitest";
import { ClientListMembershipSchema, type ClientListMembership } from "@/api/generated/contracts";
import { createFormatters } from "@/i18n/format";
import { createTranslator } from "@/i18n/messages";
import { ru } from "@/i18n/ru";
import { clientListMembershipLabel, noMembershipLabel } from "./clientListMembership";

const i18n = { t: createTranslator<typeof ru>("ru", ru), format: createFormatters("ru") };

/** Активный абонемент [name] в сроке до [endDate] с остатком [remaining] из [total]. */
function active(
  name: string,
  endDate: string,
  overrides: Partial<{
    total: number | null;
    remaining: number | null;
    termPosition: "NOT_STARTED" | "IN_TERM" | "EXPIRED";
    startDate: string;
    endingSoon: readonly ("BY_DATE" | "BY_SESSIONS")[];
    closePending: boolean;
  }> = {},
): ClientListMembership {
  const total = overrides.total === undefined ? 8 : overrides.total;
  return ClientListMembershipSchema.parse({
    id: "01900000-0000-7000-8000-000000000001",
    name,
    sessionsTotal: total,
    sessionsRemaining: overrides.remaining === undefined ? total : overrides.remaining,
    state: {
      type: "active",
      startDate: overrides.startDate ?? "2026-11-01",
      endDate,
      termPosition: overrides.termPosition ?? "IN_TERM",
      endingSoon: overrides.endingSoon ?? [],
      closePending: overrides.closePending ?? false,
    },
  });
}

describe("метка абонемента в списке клиентов", () => {
  it("действует: остаток и дата окончания", () => {
    expect(
      clientListMembershipLabel(active("Мини 8", "2026-12-03", { total: 12, remaining: 8 }), i18n),
    ).toEqual({ tone: "ok", icon: "ticket", text: "Мини 8 · 8 из 12 · до 03.12" });
  });

  it("безлимит", () => {
    expect(
      clientListMembershipLabel(active("Старшая", "2026-11-30", { total: null }), i18n),
    ).toEqual({ tone: "ok", icon: "ticket", text: "Старшая · Безлимит · до 30.11" });
  });

  it("к закрытию: срок вышел важнее закончившихся занятий", () => {
    expect(
      clientListMembershipLabel(
        active("Юниор 8", "2026-11-15", {
          remaining: 0,
          termPosition: "EXPIRED",
          closePending: true,
        }),
        i18n,
      ),
    ).toEqual({ tone: "warn", icon: "alert", text: "Юниор 8 · К закрытию · срок вышел" });
    expect(
      clientListMembershipLabel(
        active("Юниор 8", "2026-12-15", { remaining: 0, closePending: true }),
        i18n,
      ).text,
    ).toBe("Юниор 8 · К закрытию · занятия закончились");
  });

  it("заканчивается по обоим признакам: остаток и дата", () => {
    expect(
      clientListMembershipLabel(
        active("Мини 8", "2026-11-22", { remaining: 1, endingSoon: ["BY_DATE", "BY_SESSIONS"] }),
        i18n,
      ),
    ).toEqual({ tone: "warn", icon: "clock", text: "Мини 8 · Заканчивается · 1 из 8 · до 22.11" });
  });

  it("заканчивается только по занятиям — без даты", () => {
    expect(
      clientListMembershipLabel(
        active("Младшая 8", "2026-12-20", { remaining: 1, endingSoon: ["BY_SESSIONS"] }),
        i18n,
      ).text,
    ).toBe("Младшая 8 · Заканчивается · 1 из 8");
  });

  it("активный с будущей датой начала", () => {
    expect(
      clientListMembershipLabel(
        active("Мини 8", "2026-12-31", { startDate: "2026-12-01", termPosition: "NOT_STARTED" }),
        i18n,
      ),
    ).toEqual({ tone: "start", icon: "clock", text: "Мини 8 · Начнётся 01.12" });
  });

  it("не активирован: с даты и с первого посещения", () => {
    const inactive = (
      activationMode: "ON_DATE" | "ON_FIRST_VISIT",
      activateOn: string | null,
    ): ClientListMembership =>
      ClientListMembershipSchema.parse({
        ...active("Мини 8", "2026-12-31"),
        state: { type: "inactive", activationMode, activateOn },
      });
    expect(clientListMembershipLabel(inactive("ON_DATE", "2026-12-01"), i18n)).toEqual({
      tone: "start",
      icon: "clock",
      text: "Мини 8 · Начнётся 01.12",
    });
    expect(clientListMembershipLabel(inactive("ON_FIRST_VISIT", null), i18n).text).toBe(
      "Мини 8 · С первого посещения",
    );
  });

  it("нет абонемента — серая метка без значка", () => {
    expect(noMembershipLabel(i18n)).toEqual({ tone: "none", icon: null, text: "Нет абонемента" });
  });
});
