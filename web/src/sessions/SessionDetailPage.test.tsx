import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";
import { mockSessionDetail } from "./sessionDetailMock";
import { SessionIdSchema } from "@/api/generated/contracts";

/** Id, чей хэш детерминированно выбирает фикстуру запланированного занятия. */
const scheduledSessionId = "03d5f31b-a2ab-4351-b5e6-c9d308b683e2";
/** Id, чей хэш детерминированно выбирает фикстуру отменённого занятия. */
const cancelledSessionId = "d8f3c8af-5317-46a8-a232-47f53c967a70";

function server() {
  return appServer({
    "halls/list": () => json({ halls: [] }),
    "employees/list": () => json({ employees: [], total: 0 }),
  });
}

describe("карточка занятия", () => {
  it("показывает группу, зал, тренеров и участников из фикстуры", async () => {
    const fixture = mockSessionDetail(SessionIdSchema.parse(scheduledSessionId));
    openApp(`/sessions/${scheduledSessionId}`, server().fetch);

    expect(await screen.findByText(fixture.group.name)).toBeVisible();
    expect(screen.getByText(ru["sessionDetail.status.SCHEDULED"])).toBeVisible();
    expect(screen.getByText((content) => content.includes(fixture.hall.name))).toBeVisible();
    const [firstCoach] = fixture.coaches;
    if (firstCoach !== undefined) {
      expect(screen.getByText((content) => content.includes(firstCoach.name))).toBeVisible();
    }
    const [firstParticipant] = fixture.participants;
    if (firstParticipant !== undefined) {
      expect(screen.getByText(firstParticipant.name)).toBeVisible();
    }
  });

  it("отменяет запланированное занятие через подтверждение", async () => {
    openApp(`/sessions/${scheduledSessionId}`, server().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["sessionDetail.cancelAction"] }));
    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: ru["sessionDetail.cancelAction"] }),
    );

    expect(await screen.findByText(ru["sessionDetail.status.CANCELLED"])).toBeVisible();
  });

  it("скрывает действия над занятием, недоступные для отменённого занятия", async () => {
    openApp(`/sessions/${cancelledSessionId}`, server().fetch);

    expect(await screen.findByText(ru["sessionDetail.status.CANCELLED"])).toBeVisible();
    expect(
      screen.queryByRole("button", { name: ru["sessionDetail.cancelAction"] }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: ru["sessionDetail.rescheduleAction"] }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: ru["sessionDetail.changeHallAction"] }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: ru["sessionDetail.changeCoachesAction"] }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: ru["sessionDetail.noteAction"] })).toBeVisible();
  });

  it("некорректный id занятия в адресе даёт «не найдено»", async () => {
    openApp("/sessions/not-a-uuid", server().fetch);

    expect(await screen.findByText(ru["notFound.title"])).toBeVisible();
  });
});
