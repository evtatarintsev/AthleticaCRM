import type { QueryClient } from "@tanstack/react-query";
import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  Outlet,
  redirect,
  RouterProvider,
  useRouter,
  type RouterHistory,
} from "@tanstack/react-router";
import { useMemo, type ReactElement } from "react";
import type { ApiClient } from "@/api/client";
import {
  ClientIdSchema,
  EmployeeIdSchema,
  GroupIdSchema,
  type ClientId,
  type EmployeeId,
  type GroupId,
} from "@/api/generated/contracts";
import { ChangePasswordPage } from "@/account/ChangePasswordPage";
import { ProfilePage } from "@/account/ProfilePage";
import { SwitchBranchPage } from "@/account/SwitchBranchPage";
import { createAuthApi } from "@/auth/authApi";
import { ClientsPage } from "@/clients/ClientsPage";
import { ClientCreatePage } from "@/clients/ClientCreatePage";
import { ClientEditPage } from "@/clients/ClientEditPage";
import { ClientListSearchSchema, type ClientListSearch } from "@/clients/clientListSearch";
import { EmployeeCreatePage } from "@/employees/EmployeeCreatePage";
import { EmployeeDetailPage } from "@/employees/EmployeeDetailPage";
import { EmployeeEditPage } from "@/employees/EmployeeEditPage";
import { EmployeesPage } from "@/employees/EmployeesPage";
import { GroupsPage } from "@/groups/GroupsPage";
import { GroupCreatePage } from "@/groups/GroupCreatePage";
import { GroupEditPage } from "@/groups/GroupEditPage";
import { GroupDetailPage } from "@/groups/GroupDetailPage";
import { GroupListSearchSchema, type GroupListSearch } from "@/groups/groupListSearch";
import { SchedulePage } from "@/schedule/SchedulePage";
import { ScheduleSearchSchema, type ScheduleSearch } from "@/schedule/scheduleWeek";
import { branches, disciplines, halls, leadSources } from "@/settings/directories";
import { DirectoryPage } from "@/settings/directory/DirectoryPage";
import { TariffsPage } from "@/settings/tariffs/TariffsPage";
import { CustomFieldsPage } from "@/settings/customFields/CustomFieldsPage";
import { RolesPage } from "@/settings/roles/RolesPage";
import { SettingsPage } from "@/settings/SettingsPage";
import { LoginPage } from "@/auth/LoginPage";
import { LoginSearchSchema, postLoginTarget } from "@/auth/redirect";
import { SignUpPage } from "@/auth/SignUpPage";
import { BASE_PATH } from "@/config";
import { ApiFailure } from "@/query/apiFailure";
import { sessionQuery } from "@/query/queries";
import { AppLayout } from "./AppLayout";
import { HomePage } from "./HomePage";
import { KmpRedirect } from "./KmpRedirect";
import { NotFoundPage } from "./NotFoundPage";
import { SessionErrorPage } from "./SessionErrorPage";

/** Зависимости, доступные маршрутам через контекст роутера. */
export interface RouterContext {
  /** Клиент API. */
  readonly api: ApiClient;
  /** Кэш запросов. */
  readonly queryClient: QueryClient;
}

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: Outlet,
  notFoundComponent: NotFoundPage,
});

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  validateSearch: LoginSearchSchema,
  component: function LoginRoute() {
    const { api } = loginRoute.useRouteContext();
    const { redirect: target } = loginRoute.useSearch();
    const authApi = useMemo(() => createAuthApi(api), [api]);
    return (
      <LoginPage
        api={authApi}
        onAuthenticated={() => {
          window.location.assign(postLoginTarget(target));
        }}
      />
    );
  },
});

const signUpRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/sign-up",
  component: function SignUpRoute() {
    const { api } = signUpRoute.useRouteContext();
    const authApi = useMemo(() => createAuthApi(api), [api]);
    return (
      <SignUpPage
        api={authApi}
        onAuthenticated={() => {
          window.location.assign(postLoginTarget(undefined));
        }}
      />
    );
  },
});

/**
 * Раскладка страниц за логином. До показа проверяет сессию через `/auth/me`:
 * без сессии ведёт на вход с адресом возврата, при недоступности сервиса показывает ошибку.
 */
const appRoute = createRoute({
  getParentRoute: () => rootRoute,
  id: "app",
  beforeLoad: async ({ context: { api, queryClient }, location }) => {
    const session = sessionQuery(api);
    if (queryClient.getQueryData(session.queryKey) !== undefined) {
      return;
    }
    const result = await api.call("auth/me");
    if (result.ok) {
      queryClient.setQueryData(session.queryKey, result.value);
      return;
    }
    switch (result.error.kind) {
      case "unauthenticated":
        throw redirect({ to: "/login", search: { redirect: location.href }, replace: true });
      case "business":
      case "unavailable":
      case "contract":
        throw new ApiFailure(result.error);
    }
  },
  component: function AppRoute() {
    const { api, queryClient } = appRoute.useRouteContext();
    const navigate = appRoute.useNavigate();
    return (
      <AppLayout
        api={api}
        onLogout={async () => {
          await api.call("auth/logout");
          queryClient.clear();
          await navigate({ to: "/login", replace: true });
        }}
      />
    );
  },
  errorComponent: function SessionError() {
    const router = useRouter();
    return (
      <SessionErrorPage
        onRetry={() => {
          void router.invalidate();
        }}
      />
    );
  },
});

const homeRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/",
  component: HomePage,
});

/** Страница настроек со всеми пунктами. */
const settingsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings",
  component: SettingsPage,
});

/** Профиль текущего пользователя. */
const editProfileRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/edit-profile",
  component: function EditProfileRoute() {
    const { api } = editProfileRoute.useRouteContext();
    return <ProfilePage api={api} />;
  },
});

/** Смена пароля текущего пользователя. */
const changePasswordRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/change-password",
  component: function ChangePasswordRoute() {
    const { api } = changePasswordRoute.useRouteContext();
    return <ChangePasswordPage api={api} />;
  },
});

/** Смена филиала. */
const switchBranchRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/switch-branch",
  component: function SwitchBranchRoute() {
    const { api } = switchBranchRoute.useRouteContext();
    return <SwitchBranchPage api={api} />;
  },
});

/** Справочник залов. */
const hallsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/halls",
  component: function HallsRoute() {
    const { api } = hallsRoute.useRouteContext();
    return <DirectoryPage api={api} definition={halls} />;
  },
});

/** Справочник дисциплин. */
const disciplinesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/disciplines",
  component: function DisciplinesRoute() {
    const { api } = disciplinesRoute.useRouteContext();
    return <DirectoryPage api={api} definition={disciplines} />;
  },
});

/** Справочник источников клиентов. */
const clientSourcesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/client-sources",
  component: function ClientSourcesRoute() {
    const { api } = clientSourcesRoute.useRouteContext();
    return <DirectoryPage api={api} definition={leadSources} />;
  },
});

/** Филиалы организации. */
const branchesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/branches",
  component: function BranchesRoute() {
    const { api } = branchesRoute.useRouteContext();
    return <DirectoryPage api={api} definition={branches} />;
  },
});

/** Дополнительные атрибуты клиентов. */
const customFieldsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/client-additional-attributes",
  component: function CustomFieldsRoute() {
    const { api } = customFieldsRoute.useRouteContext();
    return <CustomFieldsPage api={api} />;
  },
});

/** Роли и права сотрудников. */
const rolesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/roles",
  component: function RolesRoute() {
    const { api } = rolesRoute.useRouteContext();
    return <RolesPage api={api} />;
  },
});

/** Тарифы абонементов. */
const tariffsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/settings/tariffs",
  component: function TariffsRoute() {
    const { api } = tariffsRoute.useRouteContext();
    return <TariffsPage api={api} />;
  },
});

/** Список сотрудников организации. */
const employeesRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/employees",
  component: function EmployeesRoute() {
    const { api } = employeesRoute.useRouteContext();
    return <EmployeesPage api={api} />;
  },
});

/** Создание нового сотрудника. */
const employeeCreateRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/employees/new",
  component: function EmployeeCreateRoute() {
    const { api } = employeeCreateRoute.useRouteContext();
    return <EmployeeCreatePage api={api} />;
  },
});

/** Параметр `employeeId` маршрутов карточки и редактирования сотрудника. */
const employeeIdParams = {
  parse: ({ employeeId }: { employeeId: string }): { employeeId: EmployeeId } | false => {
    const parsed = EmployeeIdSchema.safeParse(employeeId);
    return parsed.success ? { employeeId: parsed.data } : false;
  },
  stringify: ({ employeeId }: { employeeId: EmployeeId }) => ({ employeeId }),
};

/** Карточка сотрудника. */
const employeeDetailRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/employees/$employeeId",
  params: employeeIdParams,
  component: function EmployeeDetailRoute() {
    const { api } = employeeDetailRoute.useRouteContext();
    const { employeeId } = employeeDetailRoute.useParams();
    return <EmployeeDetailPage api={api} employeeId={employeeId} />;
  },
});

/** Редактирование сотрудника. */
const employeeEditRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/employees/$employeeId/edit",
  params: employeeIdParams,
  component: function EmployeeEditRoute() {
    const { api } = employeeEditRoute.useRouteContext();
    const { employeeId } = employeeEditRoute.useParams();
    return <EmployeeEditPage api={api} employeeId={employeeId} />;
  },
});

/** Расписание: неделя и фильтры в search-параметрах адреса. */
const scheduleRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/schedule",
  validateSearch: ScheduleSearchSchema,
  component: function ScheduleRoute() {
    const { api } = scheduleRoute.useRouteContext();
    const search = scheduleRoute.useSearch();
    const navigate = scheduleRoute.useNavigate();
    return (
      <SchedulePage
        api={api}
        search={search}
        onSearchChange={(next: ScheduleSearch) => {
          void navigate({ search: next });
        }}
      />
    );
  },
});

/** Список групп: фильтры в search-параметрах адреса. */
const groupsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/groups",
  validateSearch: GroupListSearchSchema,
  component: function GroupsRoute() {
    const { api } = groupsRoute.useRouteContext();
    const search = groupsRoute.useSearch();
    const navigate = groupsRoute.useNavigate();
    return (
      <GroupsPage
        api={api}
        search={search}
        onSearchChange={(next: GroupListSearch) => {
          void navigate({ search: next });
        }}
      />
    );
  },
});

/** Создание новой группы. */
const groupNewRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/groups/new",
  component: function GroupNewRoute() {
    const { api } = groupNewRoute.useRouteContext();
    return <GroupCreatePage api={api} />;
  },
});

/** Карточка группы [groupId]. */
const groupRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/groups/$groupId",
  params: {
    parse: ({ groupId }): { groupId: GroupId } | false => {
      const parsed = GroupIdSchema.safeParse(groupId);
      return parsed.success ? { groupId: parsed.data } : false;
    },
    stringify: ({ groupId }) => ({ groupId }),
  },
  component: function GroupRoute() {
    const { api } = groupRoute.useRouteContext();
    const { groupId } = groupRoute.useParams();
    return <GroupDetailPage api={api} groupId={groupId} />;
  },
});

/** Редактирование группы [groupId]. */
const groupEditRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/groups/$groupId/edit",
  params: {
    parse: ({ groupId }): { groupId: GroupId } | false => {
      const parsed = GroupIdSchema.safeParse(groupId);
      return parsed.success ? { groupId: parsed.data } : false;
    },
    stringify: ({ groupId }) => ({ groupId }),
  },
  component: function GroupEditRoute() {
    const { api } = groupEditRoute.useRouteContext();
    const { groupId } = groupEditRoute.useParams();
    return <GroupEditPage api={api} groupId={groupId} />;
  },
});

/** Список клиентов: фильтры и сортировка в search-параметрах адреса. */
const clientsRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/clients",
  validateSearch: ClientListSearchSchema,
  component: function ClientsRoute() {
    const { api } = clientsRoute.useRouteContext();
    const search = clientsRoute.useSearch();
    const navigate = clientsRoute.useNavigate();
    return (
      <ClientsPage
        api={api}
        search={search}
        onSearchChange={(next: ClientListSearch) => {
          void navigate({ search: next });
        }}
      />
    );
  },
});

/** Создание нового клиента. */
const clientNewRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/clients/new",
  component: function ClientNewRoute() {
    const { api } = clientNewRoute.useRouteContext();
    return <ClientCreatePage api={api} />;
  },
});

/**
 * Карточка клиента. Раздел ещё в KMP-клиенте, поэтому страница переводит туда же;
 * некорректный идентификатор не совпадает с маршрутом и даёт «не найдено» без запроса к API.
 */
const clientRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/clients/$clientId",
  params: {
    parse: ({ clientId }): { clientId: ClientId } | false => {
      const parsed = ClientIdSchema.safeParse(clientId);
      return parsed.success ? { clientId: parsed.data } : false;
    },
    stringify: ({ clientId }) => ({ clientId }),
  },
  component: function ClientRoute() {
    const { clientId } = clientRoute.useParams();
    return <KmpRedirect href={`/clients/${clientId}`} />;
  },
});

/** Редактирование клиента [clientId]. */
const clientEditRoute = createRoute({
  getParentRoute: () => appRoute,
  path: "/clients/$clientId/edit",
  params: {
    parse: ({ clientId }): { clientId: ClientId } | false => {
      const parsed = ClientIdSchema.safeParse(clientId);
      return parsed.success ? { clientId: parsed.data } : false;
    },
    stringify: ({ clientId }) => ({ clientId }),
  },
  component: function ClientEditRoute() {
    const { api } = clientEditRoute.useRouteContext();
    const { clientId } = clientEditRoute.useParams();
    return <ClientEditPage api={api} clientId={clientId} />;
  },
});

const routeTree = rootRoute.addChildren([
  loginRoute,
  signUpRoute,
  appRoute.addChildren([
    homeRoute,
    settingsRoute,
    editProfileRoute,
    changePasswordRoute,
    switchBranchRoute,
    hallsRoute,
    disciplinesRoute,
    clientSourcesRoute,
    tariffsRoute,
    branchesRoute,
    customFieldsRoute,
    rolesRoute,
    scheduleRoute,
    groupsRoute,
    groupNewRoute,
    groupRoute,
    groupEditRoute,
    employeesRoute,
    employeeCreateRoute,
    employeeDetailRoute,
    employeeEditRoute,
    clientsRoute,
    clientNewRoute,
    clientRoute,
    clientEditRoute,
  ]),
]);

/** Роутер приложения с зависимостями [context] и историей [history]. */
function createAppRouter(context: RouterContext, history: RouterHistory | undefined) {
  return createRouter({
    routeTree,
    context,
    basepath: BASE_PATH === "" ? "/" : BASE_PATH,
    defaultPreload: "intent",
    ...(history === undefined ? {} : { history }),
  });
}

/**
 * Роутер приложения. Его тип содержит `any` из объявлений TanStack Router (родитель корневого
 * маршрута), поэтому тип роутера не выходит за этот файл: наружу отдаются только
 * [createAppRouting], [StaticAppPath] и [isStaticAppPath].
 */
type AppRouter = ReturnType<typeof createAppRouter>;

declare module "@tanstack/react-router" {
  interface Register {
    router: AppRouter;
  }
}

/** Путь маршрута веб-клиента без параметров — на него можно сослаться типизированной ссылкой. */
export type StaticAppPath = Exclude<keyof AppRouter["routesByPath"], `${string}$${string}`>;

/** Все пути без параметров; `Record` не даёт забыть новый маршрут или оставить удалённый. */
const staticAppPaths: Readonly<Record<StaticAppPath, true>> = {
  "/": true,
  "/login": true,
  "/sign-up": true,
  "/settings": true,
  "/settings/edit-profile": true,
  "/settings/change-password": true,
  "/settings/switch-branch": true,
  "/settings/halls": true,
  "/settings/disciplines": true,
  "/settings/client-sources": true,
  "/settings/tariffs": true,
  "/settings/branches": true,
  "/settings/client-additional-attributes": true,
  "/settings/roles": true,
  "/schedule": true,
  "/groups": true,
  "/groups/new": true,
  "/employees": true,
  "/employees/new": true,
  "/clients": true,
  "/clients/new": true,
};

/** Истина, если [path] — маршрут веб-клиента без параметров. */
export function isStaticAppPath(path: string): path is StaticAppPath {
  return Object.hasOwn(staticAppPaths, path);
}

/** Маршрутизация приложения: элемент для рендера и переход на вход при потере сессии. */
export interface AppRouting {
  /** `RouterProvider` с роутером приложения. */
  readonly element: ReactElement;
  /** Открывает вход с адресом возврата на текущую страницу, если вход ещё не открыт. */
  readonly redirectToLogin: () => void;
}

/** Маршрутизация приложения с зависимостями [context]; [history] подменяется в тестах. */
export function createAppRouting(context: RouterContext, history?: RouterHistory): AppRouting {
  const router = createAppRouter(context, history);
  return {
    element: <RouterProvider router={router} />,
    redirectToLogin: () => {
      const { pathname, href } = router.latestLocation;
      if (pathname !== "/login") {
        void router.navigate({ to: "/login", search: { redirect: href }, replace: true });
      }
    },
  };
}
