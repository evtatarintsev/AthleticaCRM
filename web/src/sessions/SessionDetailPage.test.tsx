import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { describe, expect, it, vi } from "vitest";
import {
  AttendanceLabelSchemaSchema,
  ClientDetailResponseSchema,
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
import { activeState, testMembership } from "@/test/memberships";

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

/** Тренер занятия — детали для ответа `employees/detail`. */
const coach = {
  id: "0199a0b2-0000-7000-8000-300000000001",
  name: "Сергей Волков",
  avatarId: null,
  isOwner: false,
  isActive: true,
  joinedAt: "2024-01-01T00:00:00Z",
  roles: [],
  phoneNo: null,
  email: "volkov@example.com",
  grantedPermissions: [],
  revokedPermissions: [],
  allBranchesAccess: true,
  branchIds: [],
};

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

/** Полная карточка участницы «Аня» с телефоном и почтой. */
const annaDetail = ClientDetailResponseSchema.parse({
  ...client("1", "Аня"),
  groups: [{ id: "0199a0b2-0000-7000-8000-100000000001", name: "Юниоры У8" }],
  docs: [],
  leadSourceId: null,
  contacts: [
    { id: "0199a0b2-0000-7000-8000-700000000001", type: "PHONE", value: "+70000000001" },
    { id: "0199a0b2-0000-7000-8000-700000000002", type: "EMAIL", value: "anna@example.com" },
  ],
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
    coaches: [{ id: coach.id, name: coach.name }],
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
    "sessions/debit-preview": () => json(debitPreview(journal)),
    "attendance-labels/list": () => json({ labels }),
    "halls/list": () => json({ halls: [] }),
    "employees/list": () => json({ employees: [], total: 0 }),
    "employees/detail": () => json(coach),
    "clients/detail": () => json(annaDetail),
    "memberships/list": () =>
      json({
        memberships: [
          testMembership({
            id: "0199a0b2-0000-7000-8000-800000000001",
            sessionsRemaining: 3,
            state: activeState("2026-09-01", "2026-10-31"),
          }),
        ],
      }),
    "attendance/client": () =>
      json({
        items: [
          {
            sessionId: "0199a0b2-0000-7000-8000-500000000002",
            group: { id: "0199a0b2-0000-7000-8000-100000000001", name: "Юниоры У8" },
            date: "2026-09-28",
            startTime: "17:00",
            presence: "ABSENT",
            labels: [sick],
          },
        ],
      }),
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

/** Абонемент списания в превью: «8 занятий» с остатком [remaining]. */
const eightSessions = (remaining: number) => ({
  id: "0199a0b2-0000-7000-8000-800000000001",
  name: "8 занятий",
  sessionsRemaining: remaining,
  sessionsTotal: 8,
});

/**
 * Превью списаний поддельного сервера: у участника «1» есть абонемент (до проведения — спишется,
 * остаток 4; после — списан, остаток 3), у участника «3» до проведения абонемент заморожен
 * до 23.10, у остальных абонемента нет.
 */
function debitPreview(journal: SessionJournalResponse) {
  const completed = journal.status === "COMPLETED";
  return {
    participants: journal.participants.map((p) => ({
      clientId: p.clientId,
      debit: p.clientId.endsWith("1")
        ? completed
          ? { type: "debited", membership: eightSessions(3) }
          : { type: "will_debit", membership: eightSessions(4), activates: false, freeze: null }
        : p.clientId.endsWith("3") && !completed
          ? {
              type: "will_debit",
              membership: eightSessions(5),
              activates: false,
              freeze: { endDate: "2026-10-23", reason: "отпуск" },
            }
          : completed && p.presence !== "PRESENT"
            ? { type: "not_debited" }
            : { type: "no_membership" },
    })),
  };
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
  it("клик по тренеру открывает его карточку панелью, после закрытия остаётся занятие", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    const { history } = openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    const coachLink = await screen.findByRole("link", { name: coach.name });
    expect(coachLink).toHaveAttribute("href", expect.stringContaining(`employee=${coach.id}`));
    await user.click(coachLink);

    const card = within(await screen.findByRole("dialog", { name: coach.name }));
    expect(await card.findByText(coach.email)).toBeVisible();
    expect(history.location.pathname).toBe(`/sessions/${sessionId}`);

    await user.click(card.getByRole("button", { name: ru["action.close"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(history.location.pathname).toBe(`/sessions/${sessionId}`);
    expect(screen.getByText("Юниоры У8")).toBeVisible();
  });

  it("клик по участнику открывает краткую карточку клиента панелью, не уходя с занятия", async () => {
    const server = journalServer(journalOf([participant("1", "Аня")]));
    const { history } = openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    const clientLink = await screen.findByRole("link", { name: "Аня" });
    expect(clientLink).toHaveAttribute("href", `/clients/${annaDetail.id}`);
    await user.click(clientLink);

    const card = within(await screen.findByRole("dialog", { name: "Аня" }));
    expect(await card.findByRole("link", { name: "+70000000001" })).toHaveAttribute(
      "href",
      "tel:+70000000001",
    );
    expect(card.getByRole("link", { name: "anna@example.com" })).toHaveAttribute(
      "href",
      "mailto:anna@example.com",
    );
    expect(await card.findAllByText("8 занятий")).not.toHaveLength(0);
    expect(await card.findByText(ru["sessionDetail.attendance.ABSENT"])).toBeVisible();
    expect(card.getByRole("link", { name: ru["participantCard.openFull"] })).toHaveAttribute(
      "href",
      `/clients/${annaDetail.id}`,
    );
    expect(history.location.pathname).toBe(`/sessions/${sessionId}`);

    await user.click(card.getByRole("button", { name: ru["action.close"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(history.location.pathname).toBe(`/sessions/${sessionId}`);
  });

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
    expect(complete).toHaveAccessibleDescription(
      new RegExp(ru["sessionDetail.completeBlocked.title"]),
    );
    expect(
      screen.getByText(ru["sessionDetail.completeBlocked.unmarked"].one.replace("{count}", "1")),
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

  it("показывает абонемент списания участника или «Нет абонемента»", async () => {
    const server = journalServer(journalOf([participant("1", "Аня"), participant("2", "Борис")]));
    openApp(`/sessions/${sessionId}`, server.fetch);

    expect(
      await within(await screen.findByRole("listitem", { name: "Аня" })).findByText(
        /8 занятий · осталось 4/,
      ),
    ).toBeVisible();
    expect(
      await within(row("Борис")).findByText(ru["sessionDetail.debit.noMembership"]),
    ).toBeVisible();
  });

  it("показывает заморозку абонемента вместо остатка", async () => {
    const server = journalServer(journalOf([participant("3", "Вера")]));
    openApp(`/sessions/${sessionId}`, server.fetch);

    const frozen = await within(await screen.findByRole("listitem", { name: "Вера" })).findByText(
      /8 занятий · Заморозка до .*\(отпуск\)/,
    );
    expect(frozen).toBeVisible();
    expect(within(row("Вера")).queryByText(/осталось/)).not.toBeInTheDocument();
  });

  it("после проведения показывает фактическое списание", async () => {
    const server = journalServer(journalOf([participant("1", "Аня", "REGULAR", "PRESENT")]));
    openApp(`/sessions/${sessionId}`, server.fetch);
    const user = userEvent.setup();

    await within(await screen.findByRole("listitem", { name: "Аня" })).findByText(/осталось 4/);
    await user.click(screen.getByRole("button", { name: ru["sessionDetail.completeAction"] }));

    expect(await within(row("Аня")).findByText(/8 занятий · осталось 3/)).toBeVisible();
    expect(server.to("sessions/debit-preview").length).toBeGreaterThan(1);
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
