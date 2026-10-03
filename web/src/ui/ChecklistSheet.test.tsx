import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { ru } from "@/i18n/ru";
import { renderPage } from "@/test/render";
import { ChecklistSheet, type ChecklistItem } from "./ChecklistSheet";

const items: readonly ChecklistItem<string>[] = [
  { id: "ivanov", name: "Иванов" },
  { id: "kozlov", name: "Козлов" },
  { id: "sidorova", name: "Сидорова" },
];

/**
 * Открывает панель со списком [list], отмеченным набором [selected] и сохранением [onSubmit].
 */
function openChecklist({
  list = items,
  selected = ["ivanov"],
  onSubmit = () => Promise.resolve(null),
}: {
  list?: readonly ChecklistItem<string>[];
  selected?: readonly string[];
  onSubmit?: (ids: readonly string[]) => Promise<string | null>;
}) {
  /** Экран, держащий панель открытой, пока она сама не попросит закрыться. */
  function Harness() {
    const [open, setOpen] = useState(true);
    return (
      <ChecklistSheet
        open={open}
        onOpenChange={setOpen}
        title="Тренеры"
        items={list}
        selected={selected}
        emptyText="Список пуст"
        onSubmit={onSubmit}
      />
    );
  }

  renderPage(<Harness />);
}

/** Ждёт, пока панель закроется. */
const closed = () =>
  waitFor(() => {
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

describe("панель с чекбоксами", () => {
  it("при открытии отмечает текущий набор", async () => {
    openChecklist({});

    expect(await screen.findByRole("checkbox", { name: "Иванов" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Козлов" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Сидорова" })).not.toBeChecked();
  });

  it("показывает сообщение при пустом списке", async () => {
    openChecklist({ list: [], selected: [] });

    expect(await screen.findByText("Список пуст")).toBeVisible();
  });

  it("сохраняет новый набор целиком и закрывается", async () => {
    const onSubmit = vi.fn(() => Promise.resolve(null));
    openChecklist({ onSubmit });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Иванов" }));
    await user.click(screen.getByRole("checkbox", { name: "Сидорова" }));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await closed();
    expect(onSubmit).toHaveBeenCalledWith(["sidorova"]);
  });

  it("снятие всех отметок передаёт пустой набор", async () => {
    const onSubmit = vi.fn(() => Promise.resolve(null));
    openChecklist({ onSubmit });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Иванов" }));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await closed();
    expect(onSubmit).toHaveBeenCalledWith([]);
  });

  it("при ошибке остаётся открытой с прежними отметками", async () => {
    openChecklist({ onSubmit: () => Promise.resolve("Группа не найдена") });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Козлов" }));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Группа не найдена");
    expect(screen.getByRole("checkbox", { name: "Иванов" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Козлов" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Козлов" })).toBeEnabled();
  });

  it("отмена после изменения отметок спрашивает подтверждение", async () => {
    const onSubmit = vi.fn(() => Promise.resolve(null));
    openChecklist({ onSubmit });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Козлов" }));
    await user.click(screen.getByRole("button", { name: ru["action.cancel"] }));

    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await closed();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("без изменений закрывается сразу, в том числе после возврата отметки", async () => {
    openChecklist({});
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Козлов" }));
    await user.click(screen.getByRole("checkbox", { name: "Козлов" }));
    await user.click(screen.getByRole("button", { name: ru["action.cancel"] }));

    await closed();
    expect(screen.queryByText(ru["editSheet.discardTitle"])).not.toBeInTheDocument();
  });
});
