import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import type { ApiResult, AuthApi } from "../api/client";
import type { LoginResponse } from "../api/schemas";
import { t } from "../i18n";
import { SignUpPage } from "./SignUpPage";
import { browserTimezone } from "./timezones";

/** Фейковый API, отвечающий на регистрацию [signUp]. */
function fakeApi(signUp: ApiResult<LoginResponse>) {
  return {
    branches: vi.fn<AuthApi["branches"]>(),
    login: vi.fn<AuthApi["login"]>(),
    signUp: vi.fn<AuthApi["signUp"]>().mockResolvedValue(signUp),
  } satisfies AuthApi;
}

/** Рендерит экран регистрации, заполняет и отправляет форму. */
async function renderAndSubmit(api: AuthApi, onAuthenticated = vi.fn<() => void>()) {
  render(
    <MemoryRouter>
      <SignUpPage api={api} onAuthenticated={onAuthenticated} />
    </MemoryRouter>,
  );
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(t.orgName), "Лига");
  await user.type(screen.getByLabelText(t.yourName), "Иван");
  await user.type(screen.getByLabelText(t.email), "ivan@example.com");
  await user.type(screen.getByLabelText(t.password), "secret");
  await user.selectOptions(screen.getByLabelText(t.currency), "KZT");
  await user.click(screen.getByRole("button", { name: t.actionRegister }));
  return onAuthenticated;
}

describe("SignUpPage", () => {
  it("отправляет форму и переходит в приложение", async () => {
    const api = fakeApi({ ok: true, value: { accessToken: "a", refreshToken: "r" } });
    const onAuthenticated = await renderAndSubmit(api);

    expect(api.signUp).toHaveBeenCalledWith({
      companyName: "Лига",
      userName: "Иван",
      login: "ivan@example.com",
      password: "secret",
      timezone: browserTimezone(),
      currency: "KZT",
    });
    expect(onAuthenticated).toHaveBeenCalledOnce();
  });

  it("показывает сообщение сервера при ошибке", async () => {
    const api = fakeApi({
      ok: false,
      error: {
        kind: "validation",
        code: "USER_ALREADY_REGISTERED",
        message: "Уже зарегистрирован",
      },
    });
    const onAuthenticated = await renderAndSubmit(api);

    expect(screen.getByRole("alert")).toHaveTextContent("Уже зарегистрирован");
    expect(onAuthenticated).not.toHaveBeenCalled();
  });
});
