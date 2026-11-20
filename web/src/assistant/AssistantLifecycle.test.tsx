import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { createApiClient } from "@/api/client";
import { EmployeeIdSchema } from "@/api/generated/contracts";
import { I18nProvider } from "@/i18n/I18nProvider";
import { ru } from "@/i18n/ru";
import { createQueryClient } from "@/query/queries";
import { fakeServer, json } from "@/test/app";
import type { ApiClient } from "@/api/client";
import type { EmployeeId } from "@/api/generated/contracts";
import { AssistantHost } from "./AssistantHost";

/** Помощник с открытием, которым в приложении управляет раскладка. */
function Host({ api, employeeId }: { api: ApiClient; employeeId: EmployeeId }) {
  const [open, setOpen] = useState(false);
  return <AssistantHost api={api} employeeId={employeeId} open={open} onOpenChange={setOpen} />;
}

const employee = EmployeeIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000002");
const other = EmployeeIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000003");
const dialog = { id: "0199a0b2-7c3e-7d2a-9f10-000000000012", messages: [] };

describe("жизненный цикл помощника", () => {
  it("смена сотрудника/организации очищает ввод и кэш прежнего сотрудника", async () => {
    const server = fakeServer({
      "assistant/status": () => json({ available: true, enabled: true, permitted: true }),
      "assistant/dialogs/current": () => json(dialog),
    });
    const api = createApiClient({
      fetch: server.fetch,
      language: () => "ru",
      onSessionExpired: () => undefined,
      reportContractViolation: () => undefined,
    });
    const cache = createQueryClient();
    const tree = (id: typeof employee) => (
      <QueryClientProvider client={cache}>
        <I18nProvider initialLocale="ru">
          <Host key={id} api={api} employeeId={id} />
        </I18nProvider>
      </QueryClientProvider>
    );
    const view = render(tree(employee));
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["assistant.title"] }));
    await user.type(
      await screen.findByRole("textbox", { name: ru["assistant.input"] }),
      "личный черновик",
    );
    view.rerender(tree(other));
    await user.click(await screen.findByRole("button", { name: ru["assistant.title"] }));
    expect(await screen.findByRole("textbox", { name: ru["assistant.input"] })).toHaveValue("");
    expect(cache.getQueriesData({ queryKey: ["assistant", employee] })).toEqual([]);
    view.unmount();
    expect(cache.getQueriesData({ queryKey: ["assistant", other] })).toEqual([]);
  });

  it("поздний ответ после выхода не возвращает личные данные в кэш", async () => {
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    let sends = 0;
    const server = fakeServer({
      "assistant/status": () => json({ available: true, enabled: true, permitted: true }),
      "assistant/dialogs/current": () => json(dialog),
      "assistant/messages/send": () => json(dialog),
    });
    const api = createApiClient({
      fetch: async (input, init) => {
        if (input.endsWith("/assistant/messages/send")) {
          sends++;
          await pending;
        }
        return server.fetch(input, init);
      },
      language: () => "ru",
      onSessionExpired: () => undefined,
      reportContractViolation: () => undefined,
    });
    const cache = createQueryClient();
    const view = render(
      <QueryClientProvider client={cache}>
        <I18nProvider initialLocale="ru">
          <Host api={api} employeeId={employee} />
        </I18nProvider>
      </QueryClientProvider>,
    );
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["assistant.title"] }));
    await user.type(
      await screen.findByRole("textbox", { name: ru["assistant.input"] }),
      "личный запрос",
    );
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(sends).toBe(1);
    view.unmount();
    release?.();
    await waitFor(() => {
      expect(server.to("assistant/messages/send")).toHaveLength(1);
    });
    expect(cache.getQueriesData({ queryKey: ["assistant", employee] })).toEqual([]);
  });
});
