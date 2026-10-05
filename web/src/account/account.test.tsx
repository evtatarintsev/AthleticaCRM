import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { MarkNotificationsReadRequestSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, empty, json, openApp, session } from "@/test/app";

const north = { id: "0199a0b2-7c3e-7d2a-9f10-000000000004", name: "Север" };
const uploadId = "0199a0b2-7c3e-7d2a-9f10-000000000005";
const me = session;
const server = appServer;

describe("профиль", () => {
  it("сохраняет обрезанное имя, и новое имя видно в меню аккаунта", async () => {
    let session = me();
    const api = server({
      "auth/me": () => json(session),
      "auth/me/update": () => {
        session = me("Пётр Иванов");
        return empty();
      },
    });
    openApp("/settings/edit-profile", api.fetch);
    const user = userEvent.setup();

    const name = await screen.findByLabelText(ru["profile.name"]);
    expect(name).toHaveValue("Иван Петров");
    expect(name).toHaveAttribute("autocomplete", "name");
    await user.clear(name);
    await user.type(name, "  Пётр Иванов ");
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: ru["account.menu"] })).toHaveTextContent(
        "Пётр Иванов",
      );
    });
    expect(api.to("auth/me/update").map((r) => r.body)).toEqual([
      { name: "Пётр Иванов", avatarId: null },
    ]);
  });

  it("пустое имя не отправляется", async () => {
    const api = server({ "auth/me/update": () => empty() });
    openApp("/settings/edit-profile", api.fetch);
    const user = userEvent.setup();

    await user.clear(await screen.findByLabelText(ru["profile.name"]));
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByText(ru["error.required"])).toBeInTheDocument();
    expect(api.to("auth/me/update")).toHaveLength(0);
  });

  it("загружает выбранную картинку и сохраняет её как аватар", async () => {
    const api = server({
      upload: () =>
        json({
          id: uploadId,
          url: "https://files.example/avatar.png",
          originalName: "avatar.png",
          contentType: "image/png",
          sizeBytes: 3,
        }),
      "auth/me/update": () => empty(),
    });
    openApp("/settings/edit-profile", api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("button", { name: ru["profile.addPhoto"] });
    const input = document.body.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    if (input !== null) {
      await user.upload(input, new File(["png"], "avatar.png", { type: "image/png" }));
    }
    expect(await screen.findByRole("button", { name: ru["profile.changePhoto"] })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(api.to("auth/me/update").map((r) => r.body)).toEqual([
        { name: "Иван Петров", avatarId: uploadId },
      ]);
    });
    const [upload] = api.to("upload");
    expect(upload?.body instanceof FormData ? upload.body.get("file") : null).toBeInstanceOf(File);
  });
});

describe("панель редактирования", () => {
  it("клик мимо не закрывает панель, «Отмена» без изменений закрывает", async () => {
    const { history } = openApp("/settings/edit-profile", server({}).fetch);
    const user = userEvent.setup();

    const dialog = await screen.findByRole("dialog", { name: ru["profile.title"] });
    expect(history.location.search).toBe("?panel=edit-profile");
    const overlay = document.body.querySelector('[data-slot="sheet-overlay"]');
    expect(overlay).not.toBeNull();
    if (overlay !== null) {
      await user.click(overlay);
    }
    expect(dialog).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: ru["action.cancel"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["profile.title"] })).toBeNull();
    });
    expect(history.location.search).toBe("");
  });

  it("закрытие с изменениями спрашивает подтверждение", async () => {
    openApp("/settings/edit-profile", server({}).fetch);
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(ru["profile.name"]), "!");
    await user.keyboard("{Escape}");
    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["action.cancel"] }));
    expect(screen.getByLabelText(ru["profile.name"])).toHaveValue("Иван Петров!");

    await user.keyboard("{Escape}");
    await user.click(await screen.findByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["profile.title"] })).toBeNull();
    });
  });

  it("смена пароля открывается поверх профиля, Esc закрывает только верхнюю панель", async () => {
    openApp("/settings/edit-profile", server({}).fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["profile.changePassword"] }));
    expect(await screen.findByRole("dialog", { name: ru["password.title"] })).toBeInTheDocument();
    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["password.title"] })).toBeNull();
    });
    expect(screen.getByRole("dialog", { name: ru["profile.title"] })).toBeInTheDocument();
  });
});

describe("смена пароля", () => {
  /** Заполняет форму смены пароля. */
  async function fill(current: string, next: string, confirm: string) {
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText(ru["password.current"]), current);
    await user.type(screen.getByLabelText(ru["password.new"]), next);
    await user.type(screen.getByLabelText(ru["password.confirm"]), confirm);
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));
  }

  it("поля размечены для менеджера паролей", async () => {
    openApp("/settings/change-password", server({}).fetch);

    expect(await screen.findByLabelText(ru["password.current"])).toHaveAttribute(
      "autocomplete",
      "current-password",
    );
    expect(screen.getByLabelText(ru["password.new"])).toHaveAttribute(
      "autocomplete",
      "new-password",
    );
    expect(screen.getByLabelText(ru["password.confirm"])).toHaveAttribute(
      "autocomplete",
      "new-password",
    );
  });

  it("несовпадающее подтверждение не отправляется", async () => {
    const api = server({ "auth/me/change-password": () => empty() });
    openApp("/settings/change-password", api.fetch);

    await fill("old-secret", "new-secret", "other-secret");

    expect(await screen.findByText(ru["error.passwordsDontMatch"])).toBeInTheDocument();
    expect(api.to("auth/me/change-password")).toHaveLength(0);
  });

  it("отправляет старый и новый пароль и закрывает панель", async () => {
    const api = server({ "auth/me/change-password": () => empty() });
    const { history } = openApp("/settings/change-password", api.fetch);

    await fill("old-secret", "new-secret", "new-secret");

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["password.title"] })).toBeNull();
    });
    expect(history.location.search).toBe("");
    expect(api.to("auth/me/change-password").map((r) => r.body)).toEqual([
      { oldPassword: "old-secret", newPassword: "new-secret" },
    ]);
  });

  it("неверный текущий пароль — сообщение сервера", async () => {
    const api = server({
      "auth/me/change-password": () =>
        json({ code: "WRONG_PASSWORD", message: "Неверный пароль", fields: null }, 400),
    });
    openApp("/settings/change-password", api.fetch);

    await fill("wrong", "new-secret", "new-secret");

    expect(await screen.findByRole("alert")).toHaveTextContent("Неверный пароль");
  });
});

describe("смена филиала", () => {
  /** Сервер с двумя филиалами; переключение меняет филиал сессии. */
  function twoBranches() {
    let branch = center;
    return server({
      "auth/me": () => json(me("Иван Петров", null, branch)),
      "auth/my-branches": () => json({ branches: [center, north] }),
      "auth/switch-branch": ({ body }) => {
        branch = JSON.stringify(body) === JSON.stringify({ branchId: north.id }) ? north : center;
        return json({ accessToken: "", refreshToken: "" });
      },
    });
  }

  it("в панели: текущий филиал выбран, выбор другого и «Сохранить» переключают сессию", async () => {
    const api = twoBranches();
    openApp("/settings/switch-branch", api.fetch);
    const user = userEvent.setup();

    expect(await screen.findByRole("radio", { name: /Центр/ })).toBeChecked();
    await user.click(screen.getByRole("radio", { name: "Север" }));
    expect(api.to("auth/switch-branch")).toHaveLength(0);
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["branch.title"] })).toBeNull();
    });
    expect(api.to("auth/switch-branch").map((r) => r.body)).toEqual([{ branchId: north.id }]);
  });

  it("из меню аккаунта: данные раздела перезагружаются для нового филиала", async () => {
    const api = twoBranches();
    openApp("/", api.fetch);
    const user = userEvent.setup();

    await waitFor(() => {
      expect(api.to("notifications")).toHaveLength(1);
    });
    await user.click(await screen.findByRole("button", { name: ru["account.menu"] }));
    const branchMenu = await screen.findByRole("menuitem", {
      name: `${ru["account.switchBranch"]}: Центр`,
    });
    branchMenu.focus();
    await user.keyboard("{ArrowRight}");
    expect(await screen.findByRole("menuitemradio", { name: "Центр" })).toBeChecked();
    await user.keyboard("{ArrowDown}{Enter}");

    await waitFor(() => {
      expect(api.to("notifications")).toHaveLength(2);
    });
    expect(api.to("auth/switch-branch").map((r) => r.body)).toEqual([{ branchId: north.id }]);
    const switchIndex = api.requests.findIndex((r) => r.path === "auth/switch-branch");
    expect(api.requests.findLastIndex((r) => r.path === "notifications")).toBeGreaterThan(
      switchIndex,
    );
  });
});

describe("выход", () => {
  it("завершает сессию, а «Назад» не показывает данные прежней сессии", async () => {
    let authenticated = true;
    const api = server({
      "auth/me": () =>
        authenticated ? json(me()) : json({ code: "UNAUTHORIZED", message: "" }, 401),
      "auth/refresh-token": () => json({ code: "UNAUTHORIZED", message: "" }, 401),
      "auth/logout": () => {
        authenticated = false;
        return empty();
      },
    });
    const { history } = openApp("/settings", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["account.menu"] }));
    await user.click(await screen.findByRole("menuitem", { name: ru["account.logout"] }));

    expect(await screen.findByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    expect(api.to("auth/logout")).toHaveLength(1);

    history.back();

    await waitFor(() => {
      expect(history.location.pathname).toBe("/login");
    });
    expect(screen.getByRole("heading", { name: ru["auth.loginTitle"] })).toBeInTheDocument();
    expect(screen.queryByText("Иван Петров")).not.toBeInTheDocument();
  });
});

describe("уведомления", () => {
  const unread = {
    id: "0199a0b2-7c3e-7d2a-9f10-000000000010",
    title: "Новый клиент",
    body: "Анна записалась в группу",
    subject: { type: "none" },
    isRead: false,
    createdAt: "2025-02-10T11:55:00Z",
  };
  const other = {
    ...unread,
    id: "0199a0b2-7c3e-7d2a-9f10-000000000011",
    title: "Изменено расписание группы «Йога»",
    subject: { type: "group", id: "0199a0b2-7c3e-7d2a-9f10-000000000020" },
  };

  /** Сервер, который хранит отметки о прочтении уведомлений [unread] и [other]. */
  function withNotifications() {
    const read = new Set<string>();
    return server({
      notifications: () => {
        const notifications = [unread, other].map((n) => ({ ...n, isRead: read.has(n.id) }));
        return json({ notifications, unreadCount: notifications.filter((n) => !n.isRead).length });
      },
      "notifications/mark-as-read": ({ body }) => {
        MarkNotificationsReadRequestSchema.parse(body).ids.forEach((id) => {
          read.add(id);
        });
        return empty();
      },
      "notifications/mark-all-read": () => {
        read.add(unread.id);
        read.add(other.id);
        return empty();
      },
    });
  }

  it("показывает число непрочитанных, отметка одного уменьшает его", async () => {
    const api = withNotifications();
    openApp("/", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Уведомления, 2 непрочитанных" }));
    const dialog = await screen.findByRole("dialog", { name: ru["notifications.title"] });
    expect(within(dialog).getByText("Новый клиент")).toBeInTheDocument();
    const [markFirst] = within(dialog).getAllByRole("button", {
      name: ru["notifications.markRead"],
    });
    expect(markFirst).toBeDefined();
    if (markFirst !== undefined) {
      await user.click(markFirst);
    }

    await waitFor(() => {
      expect(
        within(dialog).getAllByRole("button", { name: ru["notifications.markRead"] }),
      ).toHaveLength(1);
    });
    expect(api.to("notifications/mark-as-read").map((r) => r.body)).toEqual([{ ids: [unread.id] }]);
    await user.keyboard("{Escape}");
    expect(
      await screen.findByRole("button", { name: "Уведомления, 1 непрочитанное" }),
    ).toBeInTheDocument();
  });

  it("«Прочитать все» снимает отметку со всех", async () => {
    const api = withNotifications();
    openApp("/", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: /Уведомления, 2/ }));
    await user.click(await screen.findByRole("button", { name: ru["notifications.markAllRead"] }));

    await waitFor(() => {
      expect(screen.queryAllByRole("button", { name: ru["notifications.markRead"] })).toHaveLength(
        0,
      );
    });
    expect(api.to("notifications/mark-all-read")).toHaveLength(1);
    await user.keyboard("{Escape}");
    expect(
      await screen.findByRole("button", { name: ru["notifications.title"] }),
    ).toBeInTheDocument();
  });

  it("заголовок с объектом ведёт на его страницу и отмечает уведомление прочитанным", async () => {
    const api = withNotifications();
    const { history } = openApp("/", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: /Уведомления, 2/ }));
    const dialog = await screen.findByRole("dialog", { name: ru["notifications.title"] });
    expect(within(dialog).queryByRole("link", { name: unread.title })).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("link", { name: other.title }));

    await waitFor(() => {
      expect(history.location.pathname).toBe(`/groups/${other.subject.id}`);
    });
    expect(
      screen.queryByRole("dialog", { name: ru["notifications.title"] }),
    ).not.toBeInTheDocument();
    await waitFor(() => {
      expect(api.to("notifications/mark-as-read").map((r) => r.body)).toEqual([
        { ids: [other.id] },
      ]);
    });
  });
});
