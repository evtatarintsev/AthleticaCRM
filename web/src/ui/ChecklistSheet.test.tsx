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
  searchLabel,
}: {
  list?: readonly ChecklistItem<string>[];
  selected?: readonly string[];
  onSubmit?: (ids: readonly string[]) => Promise<string | null>;
  searchLabel?: string;
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
        searchLabel={searchLabel}
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

  it("без подписи поиска поля поиска нет", async () => {
    openChecklist({});

    await screen.findByRole("checkbox", { name: "Иванов" });
    expect(screen.queryByRole("searchbox")).not.toBeInTheDocument();
  });

  it("поиск оставляет записи с подстрокой в подписи без учёта регистра", async () => {
    openChecklist({ searchLabel: "Поиск" });
    const user = userEvent.setup();

    await user.type(await screen.findByRole("searchbox", { name: "Поиск" }), "ВА");

    expect(screen.getByRole("checkbox", { name: "Иванов" })).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Сидорова" })).toBeInTheDocument();
    expect(screen.queryByRole("checkbox", { name: "Козлов" })).not.toBeInTheDocument();
  });

  it("показывает сообщение, если поиск ничего не нашёл", async () => {
    openChecklist({ searchLabel: "Поиск" });
    const user = userEvent.setup();

    await user.type(await screen.findByRole("searchbox", { name: "Поиск" }), "Петров");

    expect(screen.getByText(ru["picker.nothingFound"])).toBeVisible();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("сохраняет отмеченные записи, скрытые поиском", async () => {
    const onSubmit = vi.fn(() => Promise.resolve(null));
    openChecklist({ searchLabel: "Поиск", onSubmit });
    const user = userEvent.setup();

    await user.type(await screen.findByRole("searchbox", { name: "Поиск" }), "Сидор{Enter}");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: "Сидорова" }));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await closed();
    expect(onSubmit).toHaveBeenCalledWith(["ivanov", "sidorova"]);
  });

  it("заблокированная запись видна с причиной, находится поиском и не отмечается", async () => {
    const onSubmit = vi.fn(() => Promise.resolve(null));
    openChecklist({
      list: [{ id: "ivanov", name: "Иванов", disabledReason: "уже в группе" }, ...items.slice(1)],
      searchLabel: "Поиск",
      onSubmit,
    });
    const user = userEvent.setup();

    await user.type(await screen.findByRole("searchbox", { name: "Поиск" }), "иван");
    const blocked = screen.getByRole("checkbox", { name: /Иванов/ });
    expect(blocked).toBeDisabled();
    expect(blocked).not.toBeChecked();
    expect(screen.getByText("уже в группе")).toBeVisible();
    await user.click(blocked);
    expect(blocked).not.toBeChecked();

    await user.clear(screen.getByRole("searchbox", { name: "Поиск" }));
    await user.click(screen.getByRole("checkbox", { name: "Козлов" }));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await closed();
    expect(onSubmit).toHaveBeenCalledWith(["kozlov"]);
  });

  it("с обязательным выбором подтверждение недоступно без отметок", async () => {
    /** Экран с панелью, где подтверждение требует хотя бы одной отметки. */
    function Harness() {
      const [open, setOpen] = useState(true);
      return (
        <ChecklistSheet
          open={open}
          onOpenChange={setOpen}
          title="Группы"
          items={items}
          selected={[]}
          emptyText="Список пуст"
          submitLabel="Добавить"
          requireSelection
          onSubmit={() => Promise.resolve(null)}
        />
      );
    }
    renderPage(<Harness />);
    const user = userEvent.setup();

    const submit = await screen.findByRole("button", { name: "Добавить" });
    expect(submit).toBeDisabled();
    await user.click(screen.getByRole("checkbox", { name: "Козлов" }));
    expect(submit).toBeEnabled();
  });

  it("запись, заблокированная после ошибки, не уходит в повторное подтверждение", async () => {
    const onSubmit = vi.fn<(ids: readonly string[]) => Promise<string | null>>();
    /** Экран, который после первой ошибки блокирует уже обработанную запись «Иванов». */
    function Harness() {
      const [open, setOpen] = useState(true);
      const [list, setList] = useState<readonly ChecklistItem<string>[]>(items);
      return (
        <ChecklistSheet
          open={open}
          onOpenChange={setOpen}
          title="Группы"
          items={list}
          selected={[]}
          emptyText="Список пуст"
          onSubmit={async (ids) => {
            const failure = await onSubmit(ids);
            if (failure !== null) {
              setList([
                { id: "ivanov", name: "Иванов", disabledReason: "уже в группе" },
                ...items.slice(1),
              ]);
            }
            return failure;
          }}
        />
      );
    }
    onSubmit.mockResolvedValueOnce("Ошибка").mockResolvedValueOnce(null);
    renderPage(<Harness />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Иванов" }));
    await user.click(screen.getByRole("checkbox", { name: "Козлов" }));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Ошибка");
    expect(screen.getByRole("checkbox", { name: /Иванов/ })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "Козлов" })).toBeChecked();
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await closed();
    expect(onSubmit).toHaveBeenNthCalledWith(1, ["ivanov", "kozlov"]);
    expect(onSubmit).toHaveBeenNthCalledWith(2, ["kozlov"]);
  });
});
