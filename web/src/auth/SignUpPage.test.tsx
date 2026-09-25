import { describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ApiResult } from "../api/client";
import type { LoginResponse } from "../api/generated/contracts";
import { ru } from "../i18n/ru";
import { renderPage } from "../test/render";
import type { AuthApi } from "./authApi";
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
  renderPage(<SignUpPage api={api} onAuthenticated={onAuthenticated} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(ru["auth.orgName"]), " Лига ");
  await user.type(screen.getByLabelText(ru["auth.yourName"]), "Иван");
  await user.type(screen.getByLabelText(ru["auth.email"]), "ivan@example.com");
  await user.type(screen.getByLabelText(ru["auth.password"]), "secret");
  await user.selectOptions(screen.getByLabelText(ru["auth.currency"]), "KZT");
  await user.click(screen.getByRole("button", { name: ru["auth.actionRegister"] }));
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
        kind: "business",
        status: 400,
        code: "USER_ALREADY_REGISTERED",
        message: "Уже зарегистрирован",
      },
    });
    const onAuthenticated = await renderAndSubmit(api);

    expect(screen.getByRole("alert")).toHaveTextContent("Уже зарегистрирован");
    expect(onAuthenticated).not.toHaveBeenCalled();
  });
});
