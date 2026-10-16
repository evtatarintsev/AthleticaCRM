import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientListItemSchema,
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
  });

function server(avatarId: string | null) {
  return appServer({
    "display-settings": () => json(displaySettings),
    "clients/list": () => json({ clients: [client(avatarId)], total: 1 }),
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

describe("виджет дней рождения", () => {
  it("показывает аватар, единый стиль ссылки и обрезает длинное имя в строке", async () => {
    const fake = server(uploadId);
    openApp("/", fake.fetch);

    const link = await screen.findByRole("link", {
      name: "Алиса Иванова-Смирнова Длинная-Фамилия",
    });
    expect(link).toHaveAttribute("href", `/clients/${clientId}`);
    expect(link).toHaveClass("font-medium", "hover:underline", "truncate", "min-w-0");
    expect(link).not.toHaveClass("text-primary");
    const nameGroup = link.parentElement;
    if (nameGroup === null) {
      throw new Error("Имя клиента должно находиться в строке аватара");
    }
    expect(nameGroup).toHaveClass("flex", "min-w-0", "flex-1", "items-center", "gap-2");
    expect(screen.getByText(/\d+ лет/)).toHaveClass("shrink-0", "text-sm");
    expect(document.querySelector('[class~="lg:flex-row"]')).toBeInTheDocument();
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
      name: "Алиса Иванова-Смирнова Длинная-Фамилия",
    });
    expect(screen.getByText("АИ")).toBeInTheDocument();
    await user.click(link);

    expect(history.location.pathname).toBe(`/clients/${clientId}`);
    expect(fake.to("upload/info")).toHaveLength(0);
  });

  it("возвращается к инициалам при ошибке загрузки изображения", async () => {
    const fake = server(uploadId);
    openApp("/", fake.fetch);

    await screen.findByRole("link", { name: "Алиса Иванова-Смирнова Длинная-Фамилия" });
    const image = await waitFor(() => {
      const element = document.querySelector("img");
      if (element === null) {
        throw new Error("Аватар ещё не загружен");
      }
      return element;
    });
    fireEvent.error(image);

    await waitFor(() => {
      expect(document.querySelector("img")).not.toBeInTheDocument();
    });
    expect(screen.getByText("АИ")).toBeInTheDocument();
  });
});
