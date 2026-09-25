import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SaveCustomFieldsRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

const level = {
  fieldType: "select",
  fieldKey: "level",
  label: "Уровень",
  isRequired: true,
  isSearchable: false,
  isSortable: false,
  options: ["Новичок", "Профи"],
};

/** Сервер, который хранит набор полей и возвращает его после сохранения. */
function fieldsServer() {
  let fields: unknown = [level];
  return appServer({
    "custom-fields/list": () => json(fields),
    "custom-fields/save": ({ body }) => {
      fields = SaveCustomFieldsRequestSchema.parse(body).fields.map((f) => ({
        isRequired: false,
        isSearchable: false,
        isSortable: false,
        ...f,
      }));
      return json(fields);
    },
  });
}

/** Последний отправленный набор полей. */
function lastSaved(api: ReturnType<typeof fieldsServer>) {
  const request = api.to("custom-fields/save").at(-1);
  return request === undefined ? null : SaveCustomFieldsRequestSchema.parse(request.body).fields;
}

describe("дополнительные атрибуты", () => {
  it("показывает тип и обязательность, запрашивает поля клиента", async () => {
    const api = fieldsServer();
    openApp("/settings/client-additional-attributes", api.fetch);

    const row = (await screen.findByRole("button", { name: "Изменить «Уровень»" })).closest("tr");
    expect(row).toHaveTextContent(ru["customFields.type.select"]);
    expect(row).toHaveTextContent(ru["common.yes"]);
    expect(api.to("custom-fields/list")[0]?.query.get("entityType")).toBe("CLIENT");
  });

  it("создаёт числовое поле и отправляет весь набор", async () => {
    const api = fieldsServer();
    openApp("/settings/client-additional-attributes", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["customFields.add"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["customFields.create"] });
    await user.type(within(dialog).getByLabelText(ru["customFields.key"]), "weight");
    await user.type(within(dialog).getByLabelText(ru["customFields.label"]), "Вес");
    await user.selectOptions(
      within(dialog).getByLabelText(ru["customFields.type"]),
      ru["customFields.type.number"],
    );
    await user.type(within(dialog).getByLabelText(ru["customFields.minValue"]), "20");
    await user.click(within(dialog).getByLabelText(ru["customFields.sortable"]));
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("button", { name: "Изменить «Вес»" })).toBeVisible();
    expect(lastSaved(api)).toEqual([
      level,
      {
        fieldType: "number",
        fieldKey: "weight",
        label: "Вес",
        isRequired: false,
        isSearchable: false,
        isSortable: true,
        minValue: 20,
        maxValue: null,
      },
    ]);
  });

  it("не даёт создать поле с занятым ключом", async () => {
    const api = fieldsServer();
    openApp("/settings/client-additional-attributes", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["customFields.add"] }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(ru["customFields.key"]), "level");
    await user.type(within(dialog).getByLabelText(ru["customFields.label"]), "Дубль");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await within(dialog).findByText(ru["customFields.keyTaken"])).toBeVisible();
    expect(api.to("custom-fields/save")).toHaveLength(0);
  });

  it("редактирует опции выбора, ключ не меняется", async () => {
    const api = fieldsServer();
    openApp("/settings/client-additional-attributes", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Изменить «Уровень»" }));
    const dialog = await screen.findByRole("dialog", { name: ru["customFields.edit"] });
    expect(within(dialog).getByLabelText(ru["customFields.key"])).toHaveAttribute("readonly");
    const options = within(dialog).getByLabelText(ru["customFields.options"]);
    expect(options).toHaveValue("Новичок\nПрофи");
    await user.type(options, "\nМастер");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(lastSaved(api)).toEqual([{ ...level, options: ["Новичок", "Профи", "Мастер"] }]);
    });
  });

  it("удаляет выбранные поля", async () => {
    const api = fieldsServer();
    openApp("/settings/client-additional-attributes", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Выбрать «Уровень»" }));
    await user.click(screen.getByRole("button", { name: ru["directory.deleteSelected"] }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: ru["action.delete"] }));

    expect(await screen.findByText(ru["customFields.empty"])).toBeVisible();
    expect(lastSaved(api)).toEqual([]);
  });
});
