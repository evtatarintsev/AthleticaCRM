import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { renderPage } from "@/test/render";
import type { ChecklistItem } from "./ChecklistSheet";
import { ChoiceSheet } from "./ChoiceSheet";

const items: readonly ChecklistItem<string>[] = [
  { id: "ivanov", name: "Иванов" },
  { id: "kozlov", name: "Козлов" },
];

/** Открывает панель со списком [list], текущим выбором [current] и обработкой [onChoose]. */
function openChoice({
  list = items,
  current = "ivanov",
  onChoose = () => Promise.resolve(null),
}: {
  list?: readonly ChecklistItem<string>[];
  current?: string | null;
  onChoose?: (id: string | null) => Promise<string | null>;
}) {
  /** Экран, держащий панель открытой, пока она сама не попросит закрыться. */
  function Harness() {
    const [open, setOpen] = useState(true);
    return (
      <ChoiceSheet
        open={open}
        onOpenChange={setOpen}
        title="Исполнитель"
        items={list}
        current={current}
        noneLabel="Не назначен"
        emptyText="Сотрудников нет"
        onChoose={onChoose}
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

describe("панель одиночного выбора", () => {
  it("отмечает текущий выбор", async () => {
    openChoice({});

    expect(await screen.findByRole("button", { name: "Иванов" })).toHaveAttribute(
      "aria-current",
      "true",
    );
    expect(screen.getByRole("button", { name: "Козлов" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("button", { name: "Не назначен" })).not.toHaveAttribute("aria-current");
  });

  it("нажатие сразу передаёт выбор и закрывает панель", async () => {
    const onChoose = vi.fn(() => Promise.resolve(null));
    openChoice({ onChoose });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Козлов" }));

    await closed();
    expect(onChoose).toHaveBeenCalledWith("kozlov");
  });

  it("пункт «ничего не выбрано» передаёт null", async () => {
    const onChoose = vi.fn(() => Promise.resolve(null));
    openChoice({ onChoose });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Не назначен" }));

    await closed();
    expect(onChoose).toHaveBeenCalledWith(null);
  });

  it("при ошибке остаётся открытой с текстом ошибки", async () => {
    openChoice({ onChoose: () => Promise.resolve("Задача не найдена") });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Козлов" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Задача не найдена");
    expect(screen.getByRole("button", { name: "Козлов" })).toBeEnabled();
  });

  it("при пустом списке показывает только пункт «ничего не выбрано» и сообщение", async () => {
    openChoice({ list: [], current: null });

    expect(await screen.findByText("Сотрудников нет")).toBeVisible();
    expect(screen.getByRole("button", { name: "Не назначен" })).toHaveAttribute(
      "aria-current",
      "true",
    );
  });
});
