import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { LoginSearchSchema, postLoginTarget } from "@/auth/redirect";
import { ru } from "@/i18n/ru";
import { empty, fakeServer, json, openApp } from "@/test/app";

describe("новый пароль по ссылке", () => {
  it("открытие ссылки ничего не отправляет, сброс ведёт ко входу", async () => {
    const unauthorized = () => json({ code: "UNAUTHORIZED", message: "" }, 401);
    const api = fakeServer({
      "auth/reset-password": () => empty(),
      "auth/me": unauthorized,
      "auth/refresh-token": unauthorized,
    });
    const { history } = openApp("/reset-password#token=secret-token", api.fetch);
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(ru["password.new"]), "new-password");
    await waitFor(() => {
      expect(history.location.hash).toBe("");
    });
    expect(api.requests).toHaveLength(0);
    await user.type(screen.getByLabelText(ru["password.confirm"]), "new-password");
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("status")).toHaveTextContent(ru["passwordReset.done"]);
    expect(api.to("auth/reset-password").map((request) => request.body)).toEqual([
      { token: "secret-token", newPassword: "new-password" },
    ]);
    expect(api.requests.every((request) => !request.path.includes("secret-token"))).toBe(true);
    expect(screen.getByRole("link", { name: ru["auth.actionLogin"] })).toHaveAttribute(
      "href",
      "/login",
    );
    const loginLink = screen.getByRole("link", { name: ru["auth.actionLogin"] });
    await user.hover(loginLink);
    await waitFor(() => {
      expect(api.to("auth/refresh-token").length).toBeGreaterThan(0);
    });
    expect(history.location.pathname).toBe("/reset-password");
    await user.click(loginLink);
    expect(await screen.findByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    const search = Object.fromEntries(new URLSearchParams(history.location.search));
    expect(postLoginTarget(LoginSearchSchema.parse(search).redirect)).toBe("/");
  });

  it("несовпадающие пароли не отправляются", async () => {
    const api = fakeServer({ "auth/reset-password": () => empty() });
    openApp("/reset-password#token=secret-token", api.fetch);
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(ru["password.new"]), "one");
    await user.type(screen.getByLabelText(ru["password.confirm"]), "two");
    await user.click(screen.getByRole("button", { name: ru["passwordReset.submit"] }));
    expect(await screen.findByText(ru["error.passwordsDontMatch"])).toBeInTheDocument();
    expect(api.to("auth/reset-password")).toHaveLength(0);
  });

  it("недействительная ссылка показывает ошибку и путь к новому письму", async () => {
    const message = "Ссылка недействительна или срок её действия истёк.";
    const api = fakeServer({
      "auth/reset-password": () =>
        json({ code: "INVALID_PASSWORD_RESET", message, fields: null }, 400),
    });
    openApp("/reset-password#token=used-token", api.fetch);
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(ru["password.new"]), "new-password");
    await user.type(screen.getByLabelText(ru["password.confirm"]), "new-password");
    await user.keyboard("{Enter}");
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("link", { name: ru["passwordReset.requestNew"] })).toHaveAttribute(
      "href",
      "/forgot-password",
    );
  });
});
