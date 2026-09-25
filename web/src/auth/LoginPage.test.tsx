import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import type { ApiResult } from "../api/client";
import {
  BranchIdSchema,
  type AuthBranchesResponse,
  type LoginResponse,
} from "../api/generated/contracts";
import { t } from "../i18n";
import type { AuthApi } from "./authApi";
import { LoginPage } from "./LoginPage";

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
  render(
    <MemoryRouter>
      <LoginPage api={api} onAuthenticated={onAuthenticated} />
    </MemoryRouter>,
  );
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(t.email), "  coach@example.com ");
  await user.type(screen.getByLabelText(t.password), "secret");
  await user.click(screen.getByRole("button", { name: t.actionLogin }));
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

    expect(screen.getByRole("heading", { name: t.branchTitle })).toBeInTheDocument();
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

    expect(screen.getByRole("alert")).toHaveTextContent(t.errorInvalidCredentials);
    expect(api.login).not.toHaveBeenCalled();
    expect(onAuthenticated).not.toHaveBeenCalled();
  });

  it("сообщает о недоступности сервиса", async () => {
    const api = fakeApi({ ok: false, error: { kind: "unavailable" } });
    await renderAndSubmit(api);

    expect(screen.getByRole("alert")).toHaveTextContent(t.errorServiceUnavailable);
  });

  it("размечает поля для автозаполнения", () => {
    render(
      <MemoryRouter>
        <LoginPage api={fakeApi({ ok: true, value: { branches: [] } })} />
      </MemoryRouter>,
    );
    expect(screen.getByLabelText(t.email)).toHaveAttribute("autocomplete", "username");
    expect(screen.getByLabelText(t.password)).toHaveAttribute("autocomplete", "current-password");
  });
});
