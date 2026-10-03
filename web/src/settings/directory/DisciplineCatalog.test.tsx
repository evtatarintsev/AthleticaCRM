import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CreateDisciplineRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, empty, openApp } from "@/test/app";

const swimming = { id: "0199a0b2-7c3e-7d2a-9f10-000000000201", name: "Плавание" };

const catalog = {
  sports: [
    {
      key: "aquatics",
      names: { ru: "Плавание", en: "Swimming" },
      disciplines: [{ key: "swimming", names: { ru: "Плавание", en: "Swimming" }, aliases: [] }],
    },
    {
      key: "gymnastics",
      names: { ru: "Гимнастика", en: "Gymnastics" },
      disciplines: [
        {
          key: "rhythmic_gymnastics",
          names: { ru: "Художественная гимнастика", en: "Rhythmic gymnastics" },
          aliases: ["художка"],
        },
      ],
    },
  ],
};

/** Сервер с дисциплинами организации и каталогом; [catalogFails] — каталог отвечает ошибкой. */
function disciplinesServer(catalogFails = false) {
  return appServer({
    "disciplines/list": () => json({ disciplines: [swimming] }),
    "disciplines/create": () => empty(),
    "sport-catalog/list": () =>
      catalogFails ? json({ code: "INTERNAL", message: "" }, 500) : json(catalog),
    "halls/list": () => json({ halls: [] }),
  });
}

/** Открывает панель создания дисциплины и возвращает её. */
async function openCreate(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: ru["action.add"] }));
  return screen.findByRole("dialog", { name: ru["disciplines.create"] });
}

/** Название из запроса создания дисциплины. */
function createdName(api: ReturnType<typeof disciplinesServer>) {
  return api.to("disciplines/create").map((r) => CreateDisciplineRequestSchema.parse(r.body).name);
}

describe("создание дисциплины из каталога", () => {
  it("выбор из списка создаёт дисциплину с названием каталога", async () => {
    const api = disciplinesServer();
    openApp("/settings?panel=disciplines", api.fetch);
    const user = userEvent.setup();

    const dialog = await openCreate(user);
    const list = await within(dialog).findByRole("listbox");
    expect(within(list).getByRole("group", { name: "Гимнастика" })).toBeVisible();
    expect(within(list).getByRole("option", { name: /^Плавание/ })).toHaveTextContent(
      "уже добавлено как «Плавание»",
    );
    await user.click(within(list).getByRole("option", { name: "Художественная гимнастика" }));
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(createdName(api)).toEqual(["Художественная гимнастика"]);
    });
  });

  it("своё название сохраняется как введено", async () => {
    const api = disciplinesServer();
    openApp("/settings?panel=disciplines", api.fetch);
    const user = userEvent.setup();

    const dialog = await openCreate(user);
    await within(dialog).findByRole("listbox");
    await user.type(within(dialog).getByRole("combobox"), "Break dance");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(createdName(api)).toEqual(["Break dance"]);
    });
  });

  it("первый Esc закрывает список, второй — панель", async () => {
    openApp("/settings?panel=disciplines", disciplinesServer().fetch);
    const user = userEvent.setup();

    const dialog = await openCreate(user);
    await within(dialog).findByRole("listbox");
    await user.keyboard("{Escape}");
    expect(within(dialog).queryByRole("listbox")).not.toBeInTheDocument();
    expect(dialog).toBeInTheDocument();

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["disciplines.create"] })).toBeNull();
    });
  });

  it("переименование дисциплины — без списка", async () => {
    openApp("/settings?panel=disciplines", disciplinesServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Изменить «Плавание»" }));
    const dialog = await screen.findByRole("dialog", { name: ru["disciplines.edit"] });

    expect(within(dialog).queryByRole("combobox")).toBeNull();
    expect(within(dialog).getByLabelText(ru["directory.name"])).toHaveValue("Плавание");
  });

  it("создание зала — без списка", async () => {
    openApp("/settings?panel=halls", disciplinesServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["action.add"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["halls.create"] });

    expect(within(dialog).queryByRole("combobox")).toBeNull();
  });

  it("при ошибке каталога поле обычное и создание работает", async () => {
    const api = disciplinesServer(true);
    openApp("/settings?panel=disciplines", api.fetch);
    const user = userEvent.setup();

    const dialog = await openCreate(user);
    await waitFor(() => {
      expect(api.to("sport-catalog/list").length).toBeGreaterThan(0);
    });
    expect(within(dialog).queryByRole("combobox")).toBeNull();
    await user.type(within(dialog).getByLabelText(ru["directory.name"]), "Плавание в ластах");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(createdName(api)).toEqual(["Плавание в ластах"]);
    });
    expect(within(dialog).queryByRole("alert")).toBeNull();
  });
});
