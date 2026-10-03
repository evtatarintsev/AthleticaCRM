import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { renderPage } from "@/test/render";
import { manualField } from "./field";
import { NameComboboxField } from "./NameComboboxField";
import type { SuggestionGroup } from "./nameSuggestions";

const groups: readonly SuggestionGroup[] = [
  {
    key: "aquatics",
    label: "Плавание",
    items: [
      { key: "swimming", label: "Плавание", terms: ["Плавание", "Swimming"] },
      {
        key: "artistic_swimming",
        label: "Синхронное плавание",
        terms: ["Синхронное плавание", "Artistic swimming", "синхронка"],
      },
    ],
  },
  {
    key: "dance",
    label: "Танцы",
    items: [
      {
        key: "breaking",
        label: "Брейкинг",
        terms: ["Брейкинг", "Breaking", "брейк-данс", "break dance"],
      },
    ],
  },
];

/** Поле с подсказками и его текущее значение рядом. */
function Harness({ existing = new Map<string, string>() }: { existing?: Map<string, string> }) {
  const [value, setValue] = useState("");
  return (
    <>
      <NameComboboxField
        field={manualField("name", value, setValue)}
        label="Название"
        groups={groups}
        existing={existing}
      />
      <output aria-label="значение">{value}</output>
    </>
  );
}

/** Тексты вариантов списка подсказок. */
function optionTexts(): string[] {
  return within(screen.getByRole("listbox"))
    .getAllByRole("option")
    .map((o) => o.textContent);
}

describe("поле названия с подсказками", () => {
  it("по фокусу показывает все подсказки по группам", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("combobox", { name: "Название" }));

    const list = screen.getByRole("listbox", { name: ru["directory.suggestions"] });
    expect(within(list).getAllByRole("group")).toHaveLength(2);
    expect(within(list).getByRole("group", { name: "Танцы" })).toBeVisible();
    expect(optionTexts()).toEqual(["Плавание", "Синхронное плавание", "Брейкинг"]);
    expect(screen.getByRole("combobox")).toHaveAttribute("aria-expanded", "true");
  });

  it("фильтрует по вводу и показывает сработавшее написание", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();

    await user.type(await screen.findByRole("combobox"), "брейк-д");

    expect(optionTexts()).toEqual(["Брейкингбрейк-данс", "Добавить «брейк-д» как есть"]);
  });

  it("выбирает подсказку клавиатурой", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();
    const input = await screen.findByRole("combobox");

    await user.click(input);
    await user.keyboard("{ArrowDown}{ArrowDown}");
    const active = input.getAttribute("aria-activedescendant") ?? "";
    expect(document.getElementById(active)).toHaveTextContent("Синхронное плавание");
    await user.keyboard("{Enter}");

    expect(screen.getByLabelText("значение")).toHaveTextContent("Синхронное плавание");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(input).toHaveAttribute("aria-expanded", "false");
  });

  it("стрелка вверх с начала переходит на последний вариант", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("combobox"));
    await user.keyboard("{ArrowUp}{Enter}");

    expect(screen.getByLabelText("значение")).toHaveTextContent("Брейкинг");
  });

  it("выбирает подсказку мышью", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();

    await user.type(await screen.findByRole("combobox"), "swim");
    await user.click(screen.getByRole("option", { name: /Синхронное плавание/ }));

    expect(screen.getByLabelText("значение")).toHaveTextContent("Синхронное плавание");
  });

  it("вариант «как есть» последний и оставляет введённый текст", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();

    await user.type(await screen.findByRole("combobox"), " Керлинг ");
    expect(optionTexts()).toEqual(["Добавить «Керлинг» как есть"]);
    await user.click(screen.getByRole("option", { name: "Добавить «Керлинг» как есть" }));

    expect(screen.getByLabelText("значение")).toHaveTextContent("Керлинг");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("нет варианта «как есть», если ввод совпадает с подсказкой", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();

    await user.type(await screen.findByRole("combobox"), "Брейкинг");

    expect(optionTexts()).toEqual(["Брейкинг"]);
  });

  it("Esc и Tab закрывают список без выбора", async () => {
    renderPage(<Harness />);
    const user = userEvent.setup();
    const input = await screen.findByRole("combobox");

    await user.click(input);
    await user.keyboard("{ArrowDown}{Escape}");
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(screen.getByLabelText("значение")).toHaveTextContent("");

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("listbox")).toBeVisible();
    await user.tab();
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("помечает уже добавленные подсказки, но даёт их выбрать", async () => {
    renderPage(<Harness existing={new Map([["breaking", "break dance"]])} />);
    const user = userEvent.setup();

    await user.type(await screen.findByRole("combobox"), "брейк");
    const option = screen.getByRole("option", { name: /Брейкинг/ });
    expect(option).toHaveTextContent("уже добавлено как «break dance»");
    await user.click(option);

    expect(screen.getByLabelText("значение")).toHaveTextContent("Брейкинг");
  });
});
