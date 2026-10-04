import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import type { UserPermission } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { renderPage } from "@/test/render";
import type { PermissionOverrides } from "./employeeForm";
import { PermissionsSheet } from "./PermissionsSheet";

/** Открывает панель прав с решениями [value], правами ролей [rolePermissions] и [onApply]. */
function openPermissions({
  value = {},
  rolePermissions = new Set(),
  onApply = () => undefined,
}: {
  value?: PermissionOverrides;
  rolePermissions?: ReadonlySet<UserPermission>;
  onApply?: (value: PermissionOverrides) => void;
}) {
  /** Экран, держащий панель открытой, пока она сама не попросит закрыться. */
  function Harness() {
    const [open, setOpen] = useState(true);
    return (
      <PermissionsSheet
        open={open}
        onOpenChange={setOpen}
        value={value}
        rolePermissions={rolePermissions}
        onApply={onApply}
      />
    );
  }

  renderPage(<Harness />);
}

/** Переключатель состояния права [permission]. */
const stateGroup = (permission: UserPermission) =>
  screen.getByRole("radiogroup", { name: ru[`permission.${permission}.name`] });

/** Ждёт, пока панель закроется. */
const closed = () =>
  waitFor(() => {
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

describe("панель прав сотрудника", () => {
  it("отмечает текущие решения, остальные права — «По роли»", async () => {
    openPermissions({ value: { CAN_VIEW_ALL_TASKS: "grant" } });

    await screen.findByRole("dialog");
    expect(
      within(stateGroup("CAN_VIEW_ALL_TASKS")).getByRole("radio", {
        name: ru["employees.permissionGrant"],
      }),
    ).toBeChecked();
    expect(
      within(stateGroup("CAN_MANAGE_TASKS")).getByRole("radio", {
        name: ru["employees.permissionInherit"],
      }),
    ).toBeChecked();
  });

  it("переключение из «Выдано» в «Отозвано» отдаёт только отзыв", async () => {
    const onApply = vi.fn();
    openPermissions({ value: { CAN_VIEW_ALL_TASKS: "grant" }, onApply });
    const user = userEvent.setup();

    await screen.findByRole("dialog");
    await user.click(
      within(stateGroup("CAN_VIEW_ALL_TASKS")).getByRole("radio", {
        name: ru["employees.permissionRevoke"],
      }),
    );
    await user.click(screen.getByRole("button", { name: ru["action.done"] }));

    await closed();
    expect(onApply).toHaveBeenCalledWith({ CAN_VIEW_ALL_TASKS: "revoke" });
  });

  it("возврат в «По роли» убирает решение", async () => {
    const onApply = vi.fn();
    openPermissions({ value: { CAN_MANAGE_TASKS: "revoke" }, onApply });
    const user = userEvent.setup();

    await screen.findByRole("dialog");
    await user.click(
      within(stateGroup("CAN_MANAGE_TASKS")).getByRole("radio", {
        name: ru["employees.permissionInherit"],
      }),
    );
    await user.click(screen.getByRole("button", { name: ru["action.done"] }));

    await closed();
    expect(onApply).toHaveBeenCalledWith({});
  });

  it("поиск находит право по слову из описания", async () => {
    openPermissions({});
    const user = userEvent.setup();
    const description = ru["permission.CAN_VIEW_CLIENT_BALANCE.description"];
    const word = description.split(" ").reduce((a, b) => (b.length > a.length ? b : a));

    await user.type(
      await screen.findByRole("searchbox", { name: ru["employees.searchPermissions"] }),
      word,
    );

    expect(stateGroup("CAN_VIEW_CLIENT_BALANCE")).toBeInTheDocument();
  });

  it("показывает сообщение, если поиск ничего не нашёл", async () => {
    openPermissions({});
    const user = userEvent.setup();

    await user.type(
      await screen.findByRole("searchbox", { name: ru["employees.searchPermissions"] }),
      "абракадабра",
    );

    expect(screen.getByText(ru["picker.nothingFound"])).toBeVisible();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("помечает права из выбранных ролей", async () => {
    openPermissions({ rolePermissions: new Set<UserPermission>(["CAN_VIEW_CLIENT_BALANCE"]) });

    await screen.findByRole("dialog");
    expect(screen.getAllByText(ru["employees.permissionFromRole"])).toHaveLength(1);
    const row = stateGroup("CAN_VIEW_CLIENT_BALANCE").closest("li");
    expect(row).not.toBeNull();
    expect(row).toHaveTextContent(ru["employees.permissionFromRole"]);
  });

  it("закрытие без «Готово» не меняет решения и после изменений спрашивает подтверждение", async () => {
    const onApply = vi.fn();
    openPermissions({ onApply });
    const user = userEvent.setup();

    await screen.findByRole("dialog");
    await user.click(
      within(stateGroup("CAN_MANAGE_TASKS")).getByRole("radio", {
        name: ru["employees.permissionGrant"],
      }),
    );
    await user.click(screen.getByRole("button", { name: ru["action.cancel"] }));
    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));

    await closed();
    expect(onApply).not.toHaveBeenCalled();
  });
});
