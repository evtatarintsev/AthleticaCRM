import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { AuthMeResponseSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { en } from "@/i18n/en";
import { fakeServer, json, openApp, session } from "@/test/app";

describe("подтверждение email", () => {
  it("переключает публичную страницу на английский", async () => {
    const api = fakeServer({ "auth/confirm-email": () => json({ status: "confirmed" }) });
    openApp("/confirm-email#token=english", api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "English" }));
    await user.click(screen.getByRole("button", { name: en["emailVerification.confirm"] }));
    expect(await screen.findByRole("status")).toHaveTextContent(en["emailVerification.confirmed"]);
  });
  it.each(["confirmed", "already_confirmed"] as const)(
    "публичная страница требует явного действия: %s",
    async (status) => {
      const api = fakeServer({ "auth/confirm-email": () => json({ status }) });
      const { history } = openApp("/confirm-email#token=secret-token", api.fetch);
      const user = userEvent.setup();
      const button = await screen.findByRole("button", { name: ru["emailVerification.confirm"] });
      await waitFor(() => {
        expect(history.location.hash).toBe("");
      });
      expect(api.requests).toHaveLength(0);
      button.focus();
      await user.keyboard("{Enter}");
      expect(await screen.findByRole("status")).toHaveTextContent(
        status === "confirmed"
          ? ru["emailVerification.confirmed"]
          : ru["emailVerification.alreadyConfirmed"],
      );
      expect(api.to("auth/confirm-email").map((request) => request.body)).toEqual([
        { token: "secret-token" },
      ]);
      expect(api.requests.every((request) => !request.path.includes("secret-token"))).toBe(true);
      expect(
        screen.queryByRole("button", { name: ru["emailVerification.confirm"] }),
      ).not.toBeInTheDocument();
      expect(screen.getByRole("link", { name: ru["emailVerification.openCrm"] })).toHaveAttribute(
        "href",
        "/",
      );
    },
  );

  it("действующая чужая сессия не уводит страницу и не заменяется", async () => {
    const api = fakeServer({ "auth/confirm-email": () => json({ status: "confirmed" }) });
    const { history } = openApp(
      "/confirm-email#token=another-account-token",
      api.fetch,
      AuthMeResponseSchema.parse(session()),
    );
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["emailVerification.confirm"] }));
    expect(await screen.findByRole("status")).toHaveTextContent(ru["emailVerification.confirmed"]);
    expect(history.location.pathname).toBe("/confirm-email");
    expect(api.requests.map((request) => request.path)).toEqual(["auth/confirm-email"]);
  });

  it.each(["expired", "confirmed"] as const)(
    "новая ссылка в той же вкладке заменяет токен и сбрасывает результат: %s",
    async (previousResult) => {
      let expired = previousResult === "expired";
      const api = fakeServer({
        "auth/confirm-email": () =>
          expired
            ? json(
                {
                  code: "INVALID_EMAIL_CONFIRMATION",
                  message: ru["emailVerification.invalidLink"],
                  fields: null,
                },
                400,
              )
            : json({ status: "confirmed" }),
      });
      const { history } = openApp("/confirm-email#token=old-link", api.fetch);
      const user = userEvent.setup();
      await user.click(
        await screen.findByRole("button", { name: ru["emailVerification.confirm"] }),
      );
      await screen.findByRole(expired ? "alert" : "status");

      expired = false;
      act(() => {
        history.push("/confirm-email#token=replacement-link");
      });
      await waitFor(() => {
        expect(history.location.hash).toBe("");
      });
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(screen.queryByRole("status")).not.toBeInTheDocument();
      expect(api.to("auth/confirm-email")).toHaveLength(1);
      await user.click(screen.getByRole("button", { name: ru["emailVerification.confirm"] }));
      expect(await screen.findByRole("status")).toHaveTextContent(
        ru["emailVerification.confirmed"],
      );
      expect(api.to("auth/confirm-email").map((request) => request.body)).toEqual([
        { token: "old-link" },
        { token: "replacement-link" },
      ]);
    },
  );

  it("отсутствующая ссылка не вызывает API", async () => {
    const api = fakeServer({});
    openApp("/confirm-email", api.fetch);
    expect(await screen.findByRole("alert")).toHaveTextContent(ru["emailVerification.invalidLink"]);
    expect(api.requests).toHaveLength(0);
  });

  it("просроченная ссылка показывает ошибку и путь к повторной отправке", async () => {
    const api = fakeServer({
      "auth/confirm-email": () =>
        json(
          {
            code: "INVALID_EMAIL_CONFIRMATION",
            message: ru["emailVerification.invalidLink"],
            fields: null,
          },
          400,
        ),
    });
    openApp("/confirm-email#token=expired", api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["emailVerification.confirm"] }));
    expect(await screen.findByRole("alert")).toHaveTextContent(ru["emailVerification.invalidLink"]);
    expect(screen.getByRole("link", { name: ru["emailVerification.openCrm"] })).toHaveAttribute(
      "href",
      "/",
    );
  });
});
