import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { appServer, openApp } from "@/test/app";

describe("страница настроек", () => {
  it("перенесённые пункты открываются в веб-клиенте, остальные ведут в KMP-клиент", async () => {
    openApp("/settings", appServer({}).fetch);

    const links = await screen.findAllByRole("link", { name: /./ });
    const settingsLinks = links.filter((link) =>
      (link.getAttribute("href") ?? "").includes("/settings"),
    );
    const hrefs = settingsLinks.map((link) => link.getAttribute("href"));
    expect(hrefs).toEqual(
      expect.arrayContaining([
        "/web/settings/edit-profile",
        "/web/settings/switch-branch",
        "/web/settings/change-password",
        "/web/settings/branches",
        "/web/settings/disciplines",
        "/web/settings/halls",
        "/web/settings/roles",
        "/web/settings/client-sources",
        "/web/settings/client-additional-attributes",
        "/web/settings/tariffs",
        "/settings/basic",
        "/settings/activity-log",
      ]),
    );
    expect(
      screen.getByRole("link", { name: new RegExp(ru["settings.itemChannels"]) }),
    ).toHaveAttribute("href", "/settings");
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
      ru["settings.itemEditProfile"],
      ru["settings.itemChangePassword"],
      ru["settings.itemSwitchBranch"],
    ];
    for (const title of titles) {
      await screen.findByRole("heading", { level: 1, name: ru["settings.title"] });
      await user.click(await screen.findByRole("link", { name: new RegExp(title) }));
      await waitFor(() => {
        expect(history.location.pathname).not.toBe("/web/settings");
      });
      expect(screen.queryByRole("heading", { name: ru["notFound.title"] })).toBeNull();
      expect(await screen.findByRole("heading", { level: 1 })).toBeVisible();
      history.push("/web/settings");
    }
  });
});
