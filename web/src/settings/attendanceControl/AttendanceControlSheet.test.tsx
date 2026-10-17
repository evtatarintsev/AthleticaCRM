import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { UpdateAttendanceControlSettingsRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

/** Сервер с настройками контроля посещаемости [initial]; [rejectSave] — отклонять сохранение. */
function settingsServer(
  initial = { overdueTaskEnabled: true, overdueTaskDelayMinutes: 120 },
  rejectSave = false,
) {
  let settings = initial;
  return appServer({
    "org/attendance-control": () => json(settings),
    "org/attendance-control/update": ({ body }) => {
      if (rejectSave) {
        return json(
          { code: "INVALID_JOURNAL_CLOSE_DELAY", message: "Недопустимое время", fields: null },
          400,
        );
      }
      settings = UpdateAttendanceControlSettingsRequestSchema.parse(body);
      return json(settings);
    },
  });
}

/** Открывает панель контроля посещаемости и возвращает её диалог. */
async function openSheet(fetch: Parameters<typeof openApp>[1]) {
  openApp("/settings?panel=attendance-control", fetch);
  return screen.findByRole("dialog", { name: ru["attendanceControl.title"] });
}

describe("контроль посещаемости", () => {
  it("пункт настроек открывает панель", async () => {
    const user = userEvent.setup();
    const { history } = openApp("/settings", settingsServer().fetch);

    await user.click(
      await screen.findByRole("link", { name: new RegExp(ru["settings.itemAttendanceControl"]) }),
    );

    expect(history.location.search).toContain("panel=attendance-control");
    expect(
      await screen.findByRole("dialog", { name: ru["attendanceControl.title"] }),
    ).toBeVisible();
  });

  it("по умолчанию задача включена через 2 часа", async () => {
    const dialog = await openSheet(settingsServer().fetch);

    expect(
      await within(dialog).findByLabelText(ru["attendanceControl.overdueTaskEnabled"]),
    ).toBeChecked();
    const delay = within(dialog).getByLabelText(ru["attendanceControl.overdueTaskDelay"]);
    expect(delay).toHaveValue("120");
    expect(delay).toBeEnabled();
  });

  it("выключение задачи блокирует выбор времени и сохраняется", async () => {
    const api = settingsServer();
    const dialog = await openSheet(api.fetch);
    const user = userEvent.setup();

    await user.click(
      await within(dialog).findByLabelText(ru["attendanceControl.overdueTaskEnabled"]),
    );
    expect(within(dialog).getByLabelText(ru["attendanceControl.overdueTaskDelay"])).toBeDisabled();
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(api.to("org/attendance-control/update").at(-1)?.body).toEqual({
        overdueTaskEnabled: false,
        overdueTaskDelayMinutes: 120,
      });
    });
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["attendanceControl.title"] })).toBeNull();
    });
  });

  it("отправляет выбранное время в минутах", async () => {
    const api = settingsServer();
    const dialog = await openSheet(api.fetch);
    const user = userEvent.setup();

    await user.selectOptions(
      await within(dialog).findByLabelText(ru["attendanceControl.overdueTaskDelay"]),
      "4 ч",
    );
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(api.to("org/attendance-control/update").at(-1)?.body).toEqual({
        overdueTaskEnabled: true,
        overdueTaskDelayMinutes: 240,
      });
    });
  });

  it("нестандартное сохранённое время показано отдельным вариантом", async () => {
    const dialog = await openSheet(
      settingsServer({ overdueTaskEnabled: true, overdueTaskDelayMinutes: 45 }).fetch,
    );

    const delay = await within(dialog).findByLabelText(ru["attendanceControl.overdueTaskDelay"]);
    expect(delay).toHaveValue("45");
    expect(within(delay).getByRole("option", { name: "45 мин" })).toBeInTheDocument();
  });

  it("ошибка сохранения остаётся в панели", async () => {
    const dialog = await openSheet(settingsServer(undefined, true).fetch);
    const user = userEvent.setup();

    await user.click(
      await within(dialog).findByLabelText(ru["attendanceControl.overdueTaskEnabled"]),
    );
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await within(dialog).findByText("Недопустимое время")).toBeVisible();
    expect(screen.getByRole("dialog", { name: ru["attendanceControl.title"] })).toBeVisible();
  });
});
