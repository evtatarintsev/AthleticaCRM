import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";
import {
  AttendanceLabelSchemaSchema,
  ClientListItemSchema,
  JournalParticipantRequestSchema,
  MarkAttendanceRequestSchema,
  MarkRemainingAbsentRequestSchema,
  SessionJournalResponseSchema,
  type JournalParticipantSchema,
  type SessionJournalResponse,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

const sessionId = "0199a0b2-0000-7000-8000-500000000001";

const late = AttendanceLabelSchemaSchema.parse({
  id: "0199a0b2-0000-7000-8000-600000000001",
  name: "Опоздал",
  scope: "PRESENT",
  position: 1,
  isArchived: false,
});
const sick = AttendanceLabelSchemaSchema.parse({
  id: "0199a0b2-0000-7000-8000-600000000002",
  name: "Болеет",
  scope: "ABSENT",
  position: 2,
  isArchived: false,
});
const truant = AttendanceLabelSchemaSchema.parse({
  id: "0199a0b2-0000-7000-8000-600000000003",
  name: "Прогул",
  scope: "ABSENT",
  position: 3,
  isArchived: false,
});
const labels = [late, sick, truant];

const participant = (
  id: string,
  name: string,
  kind: "REGULAR" | "ONE_TIME" = "REGULAR",
  presence: "PRESENT" | "ABSENT" | null = null,
) => ({ clientId: `0199a0b2-0000-7000-8000-40000000000${id}`, name, kind, presence, labels: [] });

const client = (id: string, name: string) =>
  ClientListItemSchema.parse({
    id: `0199a0b2-0000-7000-8000-40000000000${id}`,
    name,
    avatarId: null,
    birthday: null,
    gender: "MALE",
    groups: [],
    balance: { minorUnits: 0, currency: "RUB" },
    customFields: [],
    contacts: [],
    state: "ACTIVE",
  });

/** Журнал запланированного, уже начавшегося занятия с участниками [participants]. */
function journalOf(
  participants: readonly ReturnType<typeof participant>[],
  status: "SCHEDULED" | "COMPLETED" | "CANCELLED" = "SCHEDULED",
): SessionJournalResponse {
  return SessionJournalResponseSchema.parse({
    sessionId,
    group: { id: "0199a0b2-0000-7000-8000-100000000001", name: "Юниоры У8" },
    date: "2026-10-01",
    startTime: "17:00",
    endTime: "18:00",
    hall: { id: "0199a0b2-0000-7000-8000-200000000001", name: "Зал А" },
    coaches: [{ id: "0199a0b2-0000-7000-8000-300000000001", name: "Сергей Волков" }],
    status,
    hasStarted: true,
    participants,
  });
}

/**
 * Поддельный сервер журнала: хранит журнал и применяет к нему команды так же,
 * как сервер. [failMark] — отметка отвечает бизнес-ошибкой.
 */
function journalServer(initial: SessionJournalResponse, failMark = false) {
  let journal = initial;
  const labelsOf = (ids: readonly string[]) => labels.filter((label) => ids.includes(label.id));
  const update = (change: (p: JournalParticipantSchema) => JournalParticipantSchema) => {
    journal = { ...journal, participants: journal.participants.map(change) };
  };
  const server = appServer({
    "sessions/journal": () => json(journal),
    "attendance-labels/list": () => json({ labels }),
    "halls/list": () => json({ halls: [] }),
    "employees/list": () => json({ employees: [], total: 0 }),
    "clients/list": () => json({ clients: [client("1", "Аня"), client("9", "Гость")], total: 2 }),
    "sessions/journal/mark": ({ body }) => {
      if (failMark) {
        return json(
          { code: "ATTENDANCE_SESSION_CANCELLED", message: "Журнал недоступен", fields: null },
          400,
        );
      }
      const request = MarkAttendanceRequestSchema.parse(body);
      update((p) =>
        p.clientId === request.clientId
          ? { ...p, presence: request.presence, labels: labelsOf(request.labelIds ?? []) }
          : p,
      );
      return json(journal);
    },
    "sessions/journal/mark-remaining-absent": ({ body }) => {
      const request = MarkRemainingAbsentRequestSchema.parse(body);
      update((p) =>
        p.presence === null ? { ...p, presence: "ABSENT", labels: labelsOf([request.labelId]) } : p,
      );
      return json(journal);
    },
    "sessions/journal/add-participant": ({ body }) => {
      const request = JournalParticipantRequestSchema.parse(body);
      journal = {
        ...journal,
        participants: [
          ...journal.participants,
          {
            clientId: request.clientId,
            name: "Гость",
            kind: "ONE_TIME",
            presence: null,
            labels: [],
          },
        ],
      };
      return json(journal);
    },
    "sessions/journal/remove-participant": ({ body }) => {
      const request = JournalParticipantRequestSchema.parse(body);
      journal = {
        ...journal,
        participants: journal.participants.filter((p) => p.clientId !== request.clientId),
      };
      return json(journal);
    },
    "sessions/complete": () => {
      journal = { ...journal, status: "COMPLETED" };
      return json(journal);
    },
  });
  return server;
}

/** Строка участника [name] в составе. */
const row = (name: string) => screen.getByRole("listitem", { name });

/** Кнопка присутствия [label] в строке участника [name]. */
const presenceButton = (name: string, label: string) =>
  within(within(row(name)).getByRole("group", { name: `Отметка: ${name}` })).getByRole("button", {
    name: label,
  });

/** Группа меток участника [name]. */
const labelGroup = (name: string) =>
  within(row(name)).getByRole("group", { name: `Метки: ${name}` });

describe("карточка занятия", () => {
  it("показывает данные занятия и состав с сервера, различая вид участия", async () => {
    const server = journalServer(
      journalOf([participant("1", "Аня"), participant("2", "Гость", "ONE_TIME")]),
    );
    openApp(`/sessions/${sessionId}`, server.fetch);

    expect(await screen.findByText("Юниоры У8")).toBeVisible();
    expect(screen.getByText(ru["sessionDetail.status.SCHEDULED"])).toBeVisible();
    expect(screen.getByText((content) => content.includes("Зал А"))).toBeVisible();
    expect(screen.getByText((content) => content.includes("Сергей Волков"))).toBeVisible();
    expect(within(row("Аня")).getByText(ru["sessionDetail.participant.REGULAR"])).toBeVisible();
    expect(within(row("Гость")).getByText(ru["sessionDetail.participant.ONE_TIME"])).toBeVisible();
    expect(within(row("Аня")).getByText(ru["sessionDetail.attendance.unmarked"])).toBeVisible();
  });

  it("сохраняет отметку на сервере: после перезагрузки участник по-прежнему отмечен", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText("Аня");
    await user.click(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"]));
    await waitFor(() => {
      expect(server.to("sessions/journal/mark")).toHaveLength(1);
    });

    cleanup();
    openApp(`/sessions/${sessionId}`, server.fetch);
    await screen.findByText("Аня");
    expect(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"])).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("предлагает метки, применимые к выбранному состоянию", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText("Аня");
    await user.click(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"]));

    await waitFor(() => {
      expect(within(labelGroup("Аня")).getByRole("button", { name: "Опоздал" })).toBeVisible();
    });
    expect(within(labelGroup("Аня")).queryByRole("button", { name: "Болеет" })).toBeNull();

    await user.click(presenceButton("Аня", ru["sessionDetail.attendance.ABSENT"]));

    expect(within(labelGroup("Аня")).getByRole("button", { name: "Болеет" })).toBeVisible();
    expect(within(labelGroup("Аня")).queryByRole("button", { name: "Опоздал" })).toBeNull();
  });

  it("не сохраняет «не пришёл» без метки и просит её выбрать", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText("Аня");
    await user.click(presenceButton("Аня", ru["sessionDetail.attendance.ABSENT"]));

    expect(
      within(row("Аня")).getByText(ru["sessionDetail.attendance.labelRequired"]),
    ).toBeVisible();
    expect(server.to("sessions/journal/mark")).toHaveLength(0);

    await user.click(within(labelGroup("Аня")).getByRole("button", { name: "Болеет" }));

    await waitFor(() => {
      expect(server.to("sessions/journal/mark")).toHaveLength(1);
    });
    const request = MarkAttendanceRequestSchema.parse(server.to("sessions/journal/mark")[0]?.body);
    expect(request.presence).toBe("ABSENT");
    expect(request.labelIds).toEqual([sick.id]);
  });

  it("при ошибке сервера показывает её и возвращает последнюю сохранённую отметку", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]), true);
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText("Аня");
    await user.click(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"]));

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith("Журнал недоступен");
    });
    await waitFor(() => {
      expect(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"])).toHaveAttribute(
        "aria-pressed",
        "false",
      );
    });
    expect(within(row("Аня")).getByText(ru["sessionDetail.attendance.unmarked"])).toBeVisible();
  });

  it("массовая отметка отмечает только оставшихся и не меняет проставленные", async () => {
    const server = journalServer(
      journalOf([participant("1", "Аня", "REGULAR", "PRESENT"), participant("2", "Борис")]),
    );
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: ru["sessionDetail.markRemainingAbsentAction"] }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.selectOptions(within(dialog).getByRole("combobox"), "Прогул");
    await user.click(
      within(dialog).getByRole("button", { name: ru["sessionDetail.markRemainingAbsentAction"] }),
    );

    await waitFor(() => {
      expect(presenceButton("Борис", ru["sessionDetail.attendance.ABSENT"])).toHaveAttribute(
        "aria-pressed",
        "true",
      );
    });
    expect(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"])).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      MarkRemainingAbsentRequestSchema.parse(
        server.to("sessions/journal/mark-remaining-absent")[0]?.body,
      ),
    ).toEqual({ sessionId, labelId: truant.id });
    expect(server.to("sessions/journal/mark")).toHaveLength(0);
  });

  it("проведение недоступно при незаполненном журнале и проводит заполненный", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    const complete = await screen.findByRole("button", {
      name: ru["sessionDetail.completeAction"],
    });
    expect(complete).toBeDisabled();
    expect(
      screen.getByText(ru["sessionDetail.completeBlocked.unmarked"].replace("{count}", String(1))),
    ).toBeVisible();

    await user.click(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"]));
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: ru["sessionDetail.completeAction"] }),
      ).toBeEnabled();
    });
    await user.click(screen.getByRole("button", { name: ru["sessionDetail.completeAction"] }));

    expect(await screen.findByText(ru["sessionDetail.status.COMPLETED"])).toBeVisible();
    expect(
      screen.queryByRole("button", { name: ru["sessionDetail.completeAction"] }),
    ).not.toBeInTheDocument();
  });

  it("в проведённом занятии отметку можно изменить", async () => {
    const server = journalServer(
      journalOf([participant("1", "Аня", "REGULAR", "PRESENT")], "COMPLETED"),
    );
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await screen.findByText("Аня");
    await user.click(presenceButton("Аня", ru["sessionDetail.attendance.ABSENT"]));
    await user.click(within(labelGroup("Аня")).getByRole("button", { name: "Болеет" }));

    await waitFor(() => {
      expect(server.to("sessions/journal/mark")).toHaveLength(1);
    });
    expect(screen.getByText(ru["sessionDetail.status.COMPLETED"])).toBeVisible();
  });

  it("добавляет разового участника и убирает его; постоянного убрать нельзя", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: ru["sessionDetail.addParticipantAction"] }),
    );
    const sheet = await screen.findByRole("dialog");
    expect(await within(sheet).findByRole("button", { name: /Аня/ })).toBeDisabled();
    expect(within(sheet).getByText(ru["sessionDetail.alreadyParticipant"])).toBeVisible();
    await user.click(within(sheet).getByRole("button", { name: "Гость" }));

    await waitFor(() => {
      expect(
        within(row("Гость")).getByText(ru["sessionDetail.participant.ONE_TIME"]),
      ).toBeVisible();
    });
    expect(
      screen.queryByRole("button", {
        name: ru["sessionDetail.removeParticipantAria"].replace("{name}", "Аня"),
      }),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole("button", {
        name: ru["sessionDetail.removeParticipantAria"].replace("{name}", "Гость"),
      }),
    );

    await waitFor(() => {
      expect(screen.queryByRole("listitem", { name: "Гость" })).not.toBeInTheDocument();
    });
  });

  it("отменяет запланированное занятие через подтверждение", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["sessionDetail.cancelAction"] }));
    const dialog = await screen.findByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: ru["sessionDetail.cancelAction"] }),
    );

    expect(await screen.findByText(ru["sessionDetail.status.CANCELLED"])).toBeVisible();
  });

  it("скрывает действия и блокирует отметки отменённого занятия", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")], "CANCELLED"));
    openApp(`/sessions/${sessionId}`, server.fetch);

    expect(await screen.findByText(ru["sessionDetail.status.CANCELLED"])).toBeVisible();
    (
      [
        "sessionDetail.completeAction",
        "sessionDetail.cancelAction",
        "sessionDetail.rescheduleAction",
        "sessionDetail.changeHallAction",
        "sessionDetail.changeCoachesAction",
        "sessionDetail.addParticipantAction",
        "sessionDetail.markRemainingAbsentAction",
      ] as const
    ).forEach((key) => {
      expect(screen.queryByRole("button", { name: ru[key] })).not.toBeInTheDocument();
    });
    expect(presenceButton("Аня", ru["sessionDetail.attendance.PRESENT"])).toBeDisabled();
    expect(screen.getByRole("button", { name: ru["sessionDetail.noteAction"] })).toBeVisible();
  });

  it("некорректный id занятия в адресе даёт «не найдено»", async () => {
    openApp("/sessions/not-a-uuid", journalServer(journalOf([])).fetch);

    expect(await screen.findByText(ru["notFound.title"])).toBeVisible();
  });
});
