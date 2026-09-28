import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  GroupIdSchema,
  HallIdSchema,
  LocalTimeSchema,
  SessionIdSchema,
  type ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { mondayOf, todayLocalDate } from "@/lib/localDate";
import { appServer, json, openApp } from "@/test/app";

const session: ScheduleSessionSchema = {
  id: SessionIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000601"),
  group: { id: GroupIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000602"), name: "Юниоры У10" },
  date: mondayOf(todayLocalDate()),
  startTime: LocalTimeSchema.parse("10:00"),
  endTime: LocalTimeSchema.parse("11:00"),
  hall: { id: HallIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000603"), name: "Зал А" },
  coaches: [],
  disciplines: [],
  status: "SCHEDULED",
  colorKey: "ORANGE",
};

function server() {
  return appServer({
    "schedule/list": () => json({ sessions: [session] }),
    "disciplines/list": () => json({ disciplines: [] }),
    "halls/list": () => json({ halls: [] }),
    "employees/list": () => json({ employees: [], total: 0 }),
  });
}

describe("расписание", () => {
  it("клик по занятию в сетке открывает его карточку", async () => {
    const { history } = openApp("/schedule", server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByText(session.group.name));

    expect(history.location.pathname).toBe(`/sessions/${session.id}`);
  });
});
