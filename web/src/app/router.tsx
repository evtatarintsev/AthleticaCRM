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
import { ClientIdSchema, type ClientId } from "@/api/generated/contracts";
import { createAuthApi } from "@/auth/authApi";
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

const routeTree = rootRoute.addChildren([
  loginRoute,
  signUpRoute,
  appRoute.addChildren([homeRoute, clientRoute]),
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
