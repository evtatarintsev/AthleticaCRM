import { focusManager } from "@tanstack/react-query";
import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp, session } from "@/test/app";

afterEach(() => {
  focusManager.setFocused(undefined);
  vi.useRealTimers();
});

describe("напоминание о подтверждении email", () => {
  it("разрешает работу и сохраняется между разделами", async () => {
    const api = appServer({
      "auth/me": () => json({ ...session(), emailVerificationPending: true }),
      "groups/list": () => json({ groups: [], total: 0 }),
    });
    openApp("/settings", api.fetch);
    const user = userEvent.setup();
    expect(
      await screen.findByRole("region", { name: ru["emailVerification.reminderTitle"] }),
    ).toHaveTextContent("coach@example.com");
    await user.click(screen.getByRole("link", { name: ru["nav.groups"] }));
    expect(await screen.findByRole("heading", { name: ru["nav.groups"] })).toBeVisible();
    expect(
      screen.getByRole("region", { name: ru["emailVerification.reminderTitle"] }),
    ).toBeVisible();
  });

  it.each(["queued", "rate_limited"] as const)(
    "не допускает повторного клика и показывает ожидание: %s",
    async (status) => {
      const api = appServer({
        "auth/me": () => json({ ...session(), emailVerificationPending: true }),
        "auth/me/resend-email-confirmation": () => json({ status, retryAfterSeconds: 1 }),
      });
      openApp("/settings", api.fetch);
      const user = userEvent.setup();
      const button = await screen.findByRole("button", { name: ru["emailVerification.resend"] });
      button.focus();
      await user.keyboard("{Enter}{Enter}");
      expect(button).toBeDisabled();
      expect(api.to("auth/me/resend-email-confirmation")).toHaveLength(1);
      expect(
        screen.getByText(ru["emailVerification.wait"].replace("{seconds}", "1"), { exact: false }),
      ).toBeVisible();
      if (status === "queued")
        expect(screen.getByText(ru["emailVerification.queued"], { exact: false })).toBeVisible();
      await waitFor(
        () => {
          expect(button).toBeEnabled();
        },
        { timeout: 2000 },
      );
    },
  );

  it("ошибка отправки не закрывает доступ к CRM", async () => {
    const api = appServer({
      "auth/me": () => json({ ...session(), emailVerificationPending: true }),
      "auth/me/resend-email-confirmation": () => json({}, 500),
    });
    openApp("/settings", api.fetch);
    const user = userEvent.setup();
    const button = await screen.findByRole("button", { name: ru["emailVerification.resend"] });
    await user.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(ru["error.serviceUnavailable"]);
    expect(button).toBeEnabled();
    expect(screen.getByRole("link", { name: ru["nav.groups"] })).toBeVisible();
  });

  it.each(["tab", "window"])(
    "после подтверждения в другом окне перечитывает профиль и убирает напоминание: %s",
    async (target) => {
      let pending = true;
      const api = appServer({
        "auth/me": () => json({ ...session(), emailVerificationPending: pending }),
      });
      openApp("/settings", api.fetch);
      expect(
        await screen.findByRole("region", { name: ru["emailVerification.reminderTitle"] }),
      ).toBeVisible();
      pending = false;
      act(() => {
        if (target === "tab") {
          focusManager.setFocused(false);
          focusManager.setFocused(true);
        } else {
          window.dispatchEvent(new Event("focus"));
        }
      });
      await waitFor(() => {
        expect(
          screen.queryByRole("region", { name: ru["emailVerification.reminderTitle"] }),
        ).not.toBeInTheDocument();
      });
      expect(api.to("auth/me").length).toBeGreaterThan(1);
    },
  );

  it("для старого аккаунта напоминание отсутствует", async () => {
    const api = appServer({});
    openApp("/settings", api.fetch);
    await screen.findByRole("link", { name: ru["nav.groups"] });
    expect(
      screen.queryByRole("region", { name: ru["emailVerification.reminderTitle"] }),
    ).not.toBeInTheDocument();
    expect(api.to("auth/me/resend-email-confirmation")).toHaveLength(0);
  });
});
