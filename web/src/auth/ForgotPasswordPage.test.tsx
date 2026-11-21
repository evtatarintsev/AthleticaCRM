import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { empty, fakeServer, json, openApp } from "@/test/app";

describe("запрос сброса пароля", () => {
  it("со страницы входа ведёт к форме и после отправки показывает нейтральное сообщение", async () => {
    const api = fakeServer({
      "auth/me": () => json({ code: "UNAUTHORIZED", message: "" }, 401),
      "auth/refresh-token": () => json({ code: "UNAUTHORIZED", message: "" }, 401),
      "auth/forgot-password": () => empty(),
    });
    const { history } = openApp("/login", api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("link", { name: ru["auth.forgotPassword"] }));
    await user.type(await screen.findByLabelText(ru["auth.email"]), "  owner@example.com ");
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("status")).toHaveTextContent(ru["passwordReset.sent"]);
    expect(history.location.pathname).toBe("/forgot-password");
    expect(api.to("auth/forgot-password").map((request) => request.body)).toEqual([
      { email: "owner@example.com" },
    ]);
  });

  it("пустой email не отправляется", async () => {
    const api = fakeServer({ "auth/forgot-password": () => empty() });
    openApp("/forgot-password", api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["passwordReset.send"] }));
    expect(await screen.findByText(ru["error.required"])).toBeInTheDocument();
    expect(api.to("auth/forgot-password")).toHaveLength(0);
  });
});
