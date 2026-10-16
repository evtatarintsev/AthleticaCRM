import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientDetailResponseSchema,
  MembershipSchemaSchema,
  type MembershipSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { addDays, todayLocalDate } from "@/lib/localDate";
import { appServer, json, openApp } from "@/test/app";

const today = todayLocalDate();

const client = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000505",
  name: "Борис Орлов",
  avatarId: null,
  birthday: null,
  gender: "MALE",
  groups: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000901", name: "Утренняя группа" }],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

/** Абонемент [name] с началом и окончанием через [startIn] и [endIn] дней от сегодня. */
function membership(
  id: string,
  name: string,
  startIn: number,
  endIn: number,
  remaining: number | null,
  total: number | null,
): MembershipSchema {
  return MembershipSchemaSchema.parse({
    id,
    name,
    sessionsTotal: total,
    sessionsRemaining: remaining,
    startDate: addDays(today, startIn),
    endDate: addDays(today, endIn),
    price: { minorUnits: 400000, currency: "RUB" },
    status: endIn < 0 ? "EXPIRED" : "ACTIVE",
  });
}

const ending = membership("0199a0b2-7c3e-7d2a-9f10-000000000a11", "1111", -10, 5, 12, 12);
const upcoming = membership("0199a0b2-7c3e-7d2a-9f10-000000000a12", "Борьба", 7, 37, null, null);
const expired = membership("0199a0b2-7c3e-7d2a-9f10-000000000a13", "Летний", -90, -30, 0, 8);

/** Поддельный сервер карточки клиента с абонементами [memberships]. */
function membershipsServer(memberships: readonly MembershipSchema[]) {
  return appServer({
    "custom-fields/list": () => json([]),
    "clients/detail": () => json(client),
    "clients/notes/list": () => json({ notes: [] }),
    "memberships/list": () => json({ memberships }),
  });
}

/** Кнопка плитки абонемента [name] в быстрой информации (первая на странице). */
async function tileButton(name: string) {
  const buttons = await screen.findAllByRole("button", {
    name: ru["clientCard.membership.open"].replace("{name}", name),
  });
  const first = buttons[0];
  if (first === undefined) {
    throw new Error(`Нет плитки абонемента «${name}»`);
  }
  return first;
}

describe("абонементы в карточке клиента", () => {
  it("показывает плитку на каждый неистёкший абонемент и выделяет причину окончания", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([ending, upcoming, expired]).fetch);

    const endingTile = within(await tileButton("1111"));
    expect(endingTile.getByText("Осталось 12 из 12")).not.toHaveClass("text-amber-700");
    expect(endingTile.getByText(/^до /)).toHaveClass("text-amber-700");

    const upcomingTile = within(await tileButton("Борьба"));
    expect(upcomingTile.getByText(/^с /)).toBeInTheDocument();

    expect(screen.getAllByText(ru["clientCard.quick.subscription"])).toHaveLength(2);
    expect(screen.getByText(ru["clientCard.subscriptionUpcoming"])).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: ru["clientCard.alerts"] })).not.toBeInTheDocument();
  });

  it("без неистёкших абонементов — одна некликабельная плитка", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([expired]).fetch);

    expect(await screen.findByText(ru["clientCard.quick.noSubscription"])).toBeInTheDocument();
    expect(screen.getAllByText(ru["clientCard.quick.subscription"])).toHaveLength(1);
    const alerts = within(screen.getByRole("list", { name: ru["clientCard.alerts"] }));
    expect(alerts.getByText(ru["clientCard.alert.noActiveSubscription"])).toBeInTheDocument();
  });

  it("плитка открывает панель абонемента, кнопки действий ничего не делают", async () => {
    const api = membershipsServer([ending]);
    openApp(`/clients/${client.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await tileButton("1111"));

    const sheet = within(await screen.findByRole("dialog", { name: "1111" }));
    expect(sheet.getByText(ru["clientCard.subscriptionEnding"])).toBeInTheDocument();
    expect(sheet.getByText("Осталось 12 из 12")).toBeInTheDocument();
    for (const action of ["renew", "freeze", "closeLabel"] as const) {
      expect(
        sheet.getByRole("button", { name: ru[`clientCard.membership.${action}`] }),
      ).toBeInTheDocument();
    }

    const before = api.requests.length;
    await user.click(sheet.getByRole("button", { name: ru["clientCard.membership.freeze"] }));
    expect(screen.getByRole("dialog", { name: "1111" })).toBeInTheDocument();
    expect(api.requests).toHaveLength(before);

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("строка истёкшего абонемента открывает панель только с «Продлить»", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([expired]).fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: ru["clientCard.membership.open"].replace("{name}", "Летний"),
      }),
    );

    const sheet = within(await screen.findByRole("dialog", { name: "Летний" }));
    expect(sheet.getByText(ru["clients.detail.subscriptionExpired"])).toBeInTheDocument();
    expect(
      sheet.getByRole("button", { name: ru["clientCard.membership.renew"] }),
    ).toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["clientCard.membership.freeze"] }),
    ).not.toBeInTheDocument();
    expect(
      sheet.queryByRole("button", { name: ru["clientCard.membership.closeLabel"] }),
    ).not.toBeInTheDocument();
  });

  it("плитка открывается с клавиатуры", async () => {
    openApp(`/clients/${client.id}`, membershipsServer([ending]).fetch);
    const user = userEvent.setup();

    (await tileButton("1111")).focus();
    await user.keyboard("{Enter}");

    expect(await screen.findByRole("dialog", { name: "1111" })).toBeInTheDocument();
  });
});
