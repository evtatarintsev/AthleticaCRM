import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  GroupIdSchema,
  HallIdSchema,
  LocalTimeSchema,
  ScheduleListRequestSchema,
  SessionIdSchema,
  type ScheduleSessionSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
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
    "groups/list-for-select": () => json([session.group]),
  });
}

/** Адрес расписания с фильтром по группе занятия. */
const groupFilterPath = `/schedule?groupIds=${encodeURIComponent(JSON.stringify([session.group.id]))}`;

/** Фильтры по группам из запросов `schedule/list` поддельного сервера [fake]. */
const requestedGroupIds = (fake: ReturnType<typeof server>) =>
  fake.to("schedule/list").map((r) => ScheduleListRequestSchema.parse(r.body).groupIds);

describe("расписание", () => {
  it("клик по занятию в сетке открывает его карточку", async () => {
    const { history } = openApp("/schedule", server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByText(session.group.name));

    expect(history.location.pathname).toBe(`/sessions/${session.id}`);
  });

  it("адрес с фильтром по группе отправляет фильтр и показывает чип с названием группы", async () => {
    const fake = server();
    openApp(groupFilterPath, fake.fetch);

    expect(
      await screen.findByText(ru["schedule.filter.group"].replace("{name}", session.group.name)),
    ).toBeVisible();
    expect(requestedGroupIds(fake)[0]).toEqual([session.group.id]);
  });

  it("снятие чипа убирает фильтр по группе из адреса и запроса", async () => {
    const fake = server();
    const { history } = openApp(groupFilterPath, fake.fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: ru["schedule.filter.removeGroup"] }),
    );

    await waitFor(() => {
      expect(history.location.search).not.toContain("groupIds");
    });
    await waitFor(() => {
      expect(requestedGroupIds(fake).at(-1)).toBeUndefined();
    });
  });

  it("переключение недели сохраняет фильтр по группе", async () => {
    const fake = server();
    openApp(groupFilterPath, fake.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["schedule.nextWeek"] }));

    await waitFor(() => {
      expect(fake.to("schedule/list").length).toBeGreaterThan(1);
    });
    expect(requestedGroupIds(fake).every((ids) => ids?.[0] === session.group.id)).toBe(true);
  });
});
