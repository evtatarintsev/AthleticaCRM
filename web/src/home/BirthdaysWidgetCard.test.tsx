import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ClientListItemSchema,
  ClientListRequestSchema,
  DashboardWidgetIdSchema,
  DisplaySettingsSchema,
  UploadIdSchema,
} from "@/api/generated/contracts";
import { appServer, json, openApp } from "@/test/app";

const widgetId = DashboardWidgetIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000901");
const clientId = "0199a0b2-7c3e-7d2a-9f10-000000000902";
const uploadId = UploadIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000903");
const avatarUrl = "https://files.example/avatar.png";

const displaySettings = DisplaySettingsSchema.parse({
  clients: { columns: [], sort: null, savedViews: [] },
  groups: { columns: [], sort: null, savedViews: [] },
  employees: { columns: [], sort: null, savedViews: [] },
  tasks: { columns: [], sort: null, savedViews: [] },
  dashboard: {
    widgets: [{ type: "birthdays", id: widgetId, title: null, window: "TODAY", limit: 10 }],
    layout: [widgetId],
  },
});

const client = (avatarId: string | null) =>
  ClientListItemSchema.parse({
    id: clientId,
    name: "Алиса Иванова-Смирнова Длинная-Фамилия",
    avatarId,
    birthday: "1995-10-16",
    gender: "FEMALE",
    groups: [],
    balance: { minorUnits: 0, currency: "RUB" },
    customFields: [],
    contacts: [],
    state: "ACTIVE",
    memberships: [],
    unpaidSessions: 0,
  });

function server(avatarId: string | null) {
  return appServer({
    "display-settings": () => json(displaySettings),
    "clients/list": ({ body }) =>
      json(
        ClientListRequestSchema.parse(body).hasDebt === true
          ? { clients: [], total: 0 }
          : { clients: [client(avatarId)], total: 1 },
      ),
    "upload/info": () =>
      json({
        id: uploadId,
        url: avatarUrl,
        originalName: "avatar.png",
        contentType: "image/png",
        sizeBytes: 1,
      }),
  });
}

afterEach(() => {
  vi.useRealTimers();
});

describe("виджет дней рождения", () => {
  it("показывает аватар, возраст под именем, дату справа и ссылку на всю строку", async () => {
    const fake = server(uploadId);
    openApp("/", fake.fetch);

    const link = await screen.findByRole("link", {
      name: /Алиса Иванова-Смирнова Длинная-Фамилия/,
    });
    expect(link).toHaveAttribute("href", `/clients/${clientId}`);
    expect(screen.getByText("Алиса Иванова-Смирнова Длинная-Фамилия")).toHaveClass(
      "truncate",
      "font-medium",
    );
    expect(screen.getByText(/^\d+ (год|года|лет)$/)).toHaveClass("block", "text-xs");
    expect(screen.getByText("16 октября")).toBeVisible();
    expect(document.querySelector('[class~="md:grid-cols-2"]')).toBeInTheDocument();
    await waitFor(() => {
      expect(document.querySelector(`img[src="${avatarUrl}"]`)).toBeInTheDocument();
    });
    expect(fake.to("upload/info")).toHaveLength(1);
  });

  it("показывает инициалы без аватара и сохраняет переход по имени", async () => {
    const fake = server(null);
    const { history } = openApp("/", fake.fetch);
    const user = userEvent.setup();

    const link = await screen.findByRole("link", {
      name: /Алиса Иванова-Смирнова Длинная-Фамилия/,
    });
    expect(screen.getByText("АИ")).toBeInTheDocument();
    await user.click(link);

    expect(history.location.pathname).toBe(`/clients/${clientId}`);
    expect(fake.to("upload/info")).toHaveLength(0);
  });

  it("возвращается к инициалам при ошибке загрузки изображения", async () => {
    const fake = server(uploadId);
    openApp("/", fake.fetch);

    const link = await screen.findByRole("link", {
      name: /Алиса Иванова-Смирнова Длинная-Фамилия/,
    });
    const image = await waitFor(() => {
      const element = link.querySelector("img");
      if (element === null) {
        throw new Error("Аватар ещё не загружен");
      }
      return element;
    });
    fireEvent.error(image);

    await waitFor(() => {
      expect(link.querySelector("img")).not.toBeInTheDocument();
    });
    expect(screen.getByText("АИ")).toBeInTheDocument();
  });
  it("выделяет сегодняшний день рождения с декоративным тортом, остальные показывает датой", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date(2026, 9, 16, 12));
    const first = client(null);
    const fake = appServer({
      "display-settings": () => json(displaySettings),
      "clients/list": () =>
        json({
          clients: [
            first,
            {
              ...first,
              id: "0199a0b2-7c3e-7d2a-9f10-000000000904",
              name: "Лев Орлов",
              birthday: "2014-10-20",
            },
          ],
          total: 2,
        }),
    });
    openApp("/", fake.fetch);
    const badge = await screen.findByText("сегодня");
    expect(badge.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(badge.closest("a")).toHaveClass("from-pink-50");
    expect(screen.getByText("31 год")).toBeVisible();
    expect(screen.getByText("12 лет")).toBeVisible();
    expect(screen.getByText("20 октября")).toBeVisible();
  });
});
