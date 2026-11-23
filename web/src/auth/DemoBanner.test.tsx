import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp, session } from "@/test/app";

describe("баннер демо-режима", () => {
  it("виден на страницах CRM в демо-режиме", async () => {
    const api = appServer({ "auth/me": () => json({ ...session(), demo: true }) });
    openApp("/settings", api.fetch);
    expect(await screen.findByRole("region", { name: ru["demo.bannerTitle"] })).toHaveTextContent(
      ru["demo.banner"],
    );
  });

  it("не показывается без демо-режима", async () => {
    const api = appServer({ "auth/me": () => json({ ...session(), demo: false }) });
    openApp("/settings", api.fetch);
    expect(await screen.findByRole("link", { name: ru["nav.groups"] })).toBeVisible();
    expect(screen.queryByRole("region", { name: ru["demo.bannerTitle"] })).not.toBeInTheDocument();
  });
});
