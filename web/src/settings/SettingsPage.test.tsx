import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { appServer, openApp } from "@/test/app";

describe("страница настроек", () => {
  it("пункты с экраном — ссылки веб-клиента, заготовки без экрана — без ссылки", async () => {
    openApp("/settings", appServer({}).fetch);

    const links = await screen.findAllByRole("link", { name: /./ });
    const settingsLinks = links.filter((link) =>
      (link.getAttribute("href") ?? "").includes("/settings"),
    );
    const hrefs = settingsLinks.map((link) => link.getAttribute("href"));
    expect(hrefs).toEqual(
      expect.arrayContaining([
        "/settings?panel=edit-profile",
        "/settings?panel=switch-branch",
        "/settings?panel=change-password",
        "/settings/branches",
        "/settings/disciplines",
        "/settings/halls",
        "/settings/roles",
        "/settings/client-sources",
        "/settings/client-additional-attributes",
        "/settings/client-import",
        "/settings/tariffs",
        "/settings/basic",
        "/settings/org-balance",
        "/settings/activity-log",
        "/settings/channels",
      ]),
    );
    expect(screen.queryByRole("link", { name: new RegExp(ru["settings.itemRanks"]) })).toBeNull();
  });

  it("каждый пункт веб-клиента открывает свою страницу", async () => {
    const user = userEvent.setup();
    const { history } = openApp("/settings", appServer({}).fetch);
    const titles = [
      ru["settings.itemHalls"],
      ru["settings.itemDisciplines"],
      ru["settings.itemClientSources"],
      ru["settings.itemBranches"],
      ru["settings.itemSubscriptionTemplates"],
      ru["settings.itemRoles"],
      ru["settings.itemClientAdditionalAttributes"],
      ru["settings.itemBasicSettings"],
      ru["settings.itemOrgBalance"],
      ru["settings.itemActivityLog"],
      ru["settings.itemClientImport"],
      ru["settings.itemChannels"],
    ];
    for (const title of titles) {
      await screen.findByRole("heading", { level: 1, name: ru["settings.title"] });
      await user.click(await screen.findByRole("link", { name: new RegExp(title) }));
      await waitFor(() => {
        expect(history.location.pathname).not.toBe("/settings");
      });
      expect(screen.queryByRole("heading", { name: ru["notFound.title"] })).toBeNull();
      expect(await screen.findByRole("heading", { level: 1 })).toBeVisible();
      history.push("/settings");
    }
  }, 15000);

  it("пункты раздела «Пользователь» открывают панель, не уходя со страницы", async () => {
    const user = userEvent.setup();
    const { history } = openApp("/settings", appServer({}).fetch);
    const items = [
      [ru["settings.itemEditProfile"], ru["profile.title"]],
      [ru["settings.itemChangePassword"], ru["password.title"]],
      [ru["settings.itemSwitchBranch"], ru["branch.title"]],
    ] as const;
    for (const [item, title] of items) {
      await user.click(await screen.findByRole("link", { name: new RegExp(item) }));
      const dialog = await screen.findByRole("dialog", { name: title });
      expect(history.location.pathname).toBe("/settings");
      await user.click(within(dialog).getByRole("button", { name: ru["action.cancel"] }));
      await waitFor(() => {
        expect(screen.queryByRole("dialog", { name: title })).toBeNull();
      });
    }
  });
});
