import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import type { ApiResult, AuthApi } from "../api/client";
import type { AuthBranchesResponse, LoginResponse } from "../api/schemas";
import { t } from "../i18n";
import { LoginPage } from "./LoginPage";

const tokens: ApiResult<LoginResponse> = {
  ok: true,
  value: { accessToken: "a", refreshToken: "r" },
};

/** Фейковый API: [branches] — ответ на запрос филиалов, [login] — ответ на вход. */
function fakeApi(
  branches: ApiResult<AuthBranchesResponse>,
  login: ApiResult<LoginResponse> = tokens,
): AuthApi {
  return {
    branches: vi.fn().mockResolvedValue(branches),
    login: vi.fn().mockResolvedValue(login),
    signUp: vi.fn(),
  };
}

/** Рендерит экран входа и заполняет учётные данные. */
async function renderAndSubmit(api: AuthApi, onAuthenticated = vi.fn()) {
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
    const api = fakeApi({ ok: true, value: { branches: [{ id: "b1", name: "Центр" }] } });
    const { onAuthenticated } = await renderAndSubmit(api);

    expect(api.login).toHaveBeenCalledWith({
      username: "coach@example.com",
      password: "secret",
      branchId: "b1",
    });
    expect(onAuthenticated).toHaveBeenCalledOnce();
  });

  it("с несколькими филиалами просит выбрать", async () => {
    const api = fakeApi({
      ok: true,
      value: {
        branches: [
          { id: "b1", name: "Центр" },
          { id: "b2", name: "Север" },
        ],
      },
    });
    const { user, onAuthenticated } = await renderAndSubmit(api);

    expect(screen.getByRole("heading", { name: t.branchTitle })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Север" }));

    expect(api.login).toHaveBeenCalledWith(expect.objectContaining({ branchId: "b2" }));
    expect(onAuthenticated).toHaveBeenCalledOnce();
  });

  it("показывает ошибку неверных учётных данных", async () => {
    const api = fakeApi({
      ok: false,
      error: { kind: "validation", code: "INVALID_CREDENTIALS", message: "" },
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
