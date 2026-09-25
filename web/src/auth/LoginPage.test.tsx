import type { DeepKeys } from "@tanstack/react-form";
import { describe, expect, expectTypeOf, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ApiResult } from "../api/client";
import {
  BranchIdSchema,
  type AuthBranchesResponse,
  type LoginResponse,
} from "../api/generated/contracts";
import { ru } from "../i18n/ru";
import { renderPage } from "../test/render";
import type { AuthApi } from "./authApi";
import { LoginPage, type LoginFormValues } from "./LoginPage";

const centerId = BranchIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000001");
const northId = BranchIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000002");

const tokens: ApiResult<LoginResponse> = {
  ok: true,
  value: { accessToken: "a", refreshToken: "r" },
};

/** Фейковый API: [branches] — ответ на запрос филиалов, [login] — ответ на вход. */
function fakeApi(
  branches: ApiResult<AuthBranchesResponse>,
  login: ApiResult<LoginResponse> = tokens,
) {
  return {
    branches: vi.fn<AuthApi["branches"]>().mockResolvedValue(branches),
    login: vi.fn<AuthApi["login"]>().mockResolvedValue(login),
    signUp: vi.fn<AuthApi["signUp"]>(),
  } satisfies AuthApi;
}

/** Рендерит экран входа и заполняет учётные данные. */
async function renderAndSubmit(api: AuthApi, onAuthenticated = vi.fn<() => void>()) {
  renderPage(<LoginPage api={api} onAuthenticated={onAuthenticated} />);
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(ru["auth.email"]), "  coach@example.com ");
  await user.type(screen.getByLabelText(ru["auth.password"]), "secret");
  await user.click(screen.getByRole("button", { name: ru["auth.actionLogin"] }));
  return { user, onAuthenticated };
}

describe("LoginPage", () => {
  it("с одним филиалом входит в него сразу", async () => {
    const api = fakeApi({ ok: true, value: { branches: [{ id: centerId, name: "Центр" }] } });
    const { onAuthenticated } = await renderAndSubmit(api);

    expect(api.login).toHaveBeenCalledWith({
      username: "coach@example.com",
      password: "secret",
      branchId: centerId,
    });
    expect(onAuthenticated).toHaveBeenCalledOnce();
  });

  it("с несколькими филиалами просит выбрать", async () => {
    const api = fakeApi({
      ok: true,
      value: {
        branches: [
          { id: centerId, name: "Центр" },
          { id: northId, name: "Север" },
        ],
      },
    });
    const { user, onAuthenticated } = await renderAndSubmit(api);

    expect(
      await screen.findByRole("heading", { name: ru["auth.branchTitle"] }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Север" }));

    expect(api.login).toHaveBeenCalledWith(expect.objectContaining({ branchId: northId }));
    expect(onAuthenticated).toHaveBeenCalledOnce();
  });

  it("показывает ошибку неверных учётных данных", async () => {
    const api = fakeApi({
      ok: false,
      error: { kind: "business", status: 400, code: "INVALID_CREDENTIALS", message: "" },
    });
    const { onAuthenticated } = await renderAndSubmit(api);

    expect(screen.getByRole("alert")).toHaveTextContent(ru["error.invalidCredentials"]);
    expect(api.login).not.toHaveBeenCalled();
    expect(onAuthenticated).not.toHaveBeenCalled();
  });

  it("сообщает о недоступности сервиса", async () => {
    const api = fakeApi({ ok: false, error: { kind: "unavailable" } });
    await renderAndSubmit(api);

    expect(screen.getByRole("alert")).toHaveTextContent(ru["error.serviceUnavailable"]);
  });

  it("размечает поля для автозаполнения", async () => {
    renderPage(
      <LoginPage api={fakeApi({ ok: true, value: { branches: [] } })} onAuthenticated={vi.fn()} />,
    );
    expect(await screen.findByLabelText(ru["auth.email"])).toHaveAttribute(
      "autocomplete",
      "username",
    );
    expect(screen.getByLabelText(ru["auth.password"])).toHaveAttribute(
      "autocomplete",
      "current-password",
    );
  });

  it("не отправляет пустую форму и связывает ошибку с полем", async () => {
    const api = fakeApi({ ok: true, value: { branches: [] } });
    renderPage(<LoginPage api={api} onAuthenticated={vi.fn()} />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["auth.actionLogin"] }));

    const email = screen.getByLabelText(ru["auth.email"]);
    expect(email).toHaveAttribute("aria-invalid", "true");
    expect(email).toHaveAccessibleDescription(ru["error.required"]);
    expect(api.branches).not.toHaveBeenCalled();
  });

  it("показывает интерфейс на английском", async () => {
    renderPage(
      <LoginPage api={fakeApi({ ok: true, value: { branches: [] } })} onAuthenticated={vi.fn()} />,
      "en",
    );
    expect(await screen.findByRole("heading", { name: "Log in" })).toBeInTheDocument();
  });

  it("путь поля формы проверяется компилятором", () => {
    expectTypeOf<"username">().toExtend<DeepKeys<LoginFormValues>>();
    expectTypeOf<"usernme">().not.toExtend<DeepKeys<LoginFormValues>>();
  });
});
