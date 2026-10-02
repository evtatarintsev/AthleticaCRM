import { screen, waitFor } from "@testing-library/react";
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
        "/settings?panel=branches",
        "/settings?panel=disciplines",
        "/settings?panel=halls",
        "/settings/roles",
        "/settings?panel=client-sources",
        "/settings?panel=client-additional-attributes",
        "/settings?panel=client-import",
        "/settings/tariffs",
        "/settings?panel=basic",
        "/settings?panel=org-balance",
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
      ru["settings.itemSubscriptionTemplates"],
      ru["settings.itemRoles"],
      ru["settings.itemActivityLog"],
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

  it("пункты разделов «Пользователь», «Основное» и «Клиенты» открывают панель на странице", async () => {
    const user = userEvent.setup();
    const { history } = openApp("/settings", appServer({}).fetch);
    const items = [
      [ru["settings.itemEditProfile"], ru["profile.title"]],
      [ru["settings.itemChangePassword"], ru["password.title"]],
      [ru["settings.itemSwitchBranch"], ru["branch.title"]],
      [ru["settings.itemBasicSettings"], ru["orgSettings.title"]],
      [ru["settings.itemOrgBalance"], ru["orgBalance.title"]],
      [ru["settings.itemBranches"], ru["branches.title"]],
      [ru["settings.itemDisciplines"], ru["disciplines.title"]],
      [ru["settings.itemHalls"], ru["halls.title"]],
      [ru["settings.itemClientSources"], ru["leadSources.title"]],
      [ru["settings.itemClientAdditionalAttributes"], ru["customFields.title"]],
      [ru["settings.itemClientImport"], ru["import.title"]],
    ] as const;
    for (const [item, title] of items) {
      await user.click(await screen.findByRole("link", { name: new RegExp(item) }));
      expect(await screen.findByRole("dialog", { name: title })).toBeInTheDocument();
      expect(history.location.pathname).toBe("/settings");
      await user.keyboard("{Escape}");
      await waitFor(() => {
        expect(screen.queryByRole("dialog", { name: title })).toBeNull();
      });
    }
  }, 15000);
});
