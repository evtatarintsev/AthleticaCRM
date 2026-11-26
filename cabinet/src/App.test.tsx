import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { I18nProvider } from "./i18n/I18nProvider";

describe("App", () => {
  it("показывает заголовок кабинета на языке интерфейса", () => {
    render(
      <I18nProvider initialLocale="en">
        <App />
      </I18nProvider>,
    );
    expect(screen.getByRole("heading", { name: "Client portal" })).toBeInTheDocument();
  });
});
