import { useMemo, useState } from "react";
import { createCabinetApi, type ApiClientOptions } from "./api/client";
import { Cabinet } from "./profiles/Cabinet";
import { SignIn } from "./signIn/SignIn";

/**
 * Корневой экран кабинета клиента. Сеанс живёт в HttpOnly-cookie, поэтому кабинет сразу
 * загружает профили: если сеанс обновить не удалось, клиент API сообщает об этом и кабинет
 * показывает вход по номеру. [fetch] — реализация запросов; в тестах подменяется.
 */
export function App({ fetch }: { fetch: ApiClientOptions["fetch"] }) {
  const [signedIn, setSignedIn] = useState(true);
  const api = useMemo(
    () =>
      createCabinetApi({
        fetch,
        language: () => document.documentElement.lang,
        onSessionExpired: () => {
          setSignedIn(false);
        },
        reportContractViolation: (endpoint, issues) => {
          console.error(`Нарушение контракта ${endpoint}`, issues);
        },
      }),
    [fetch],
  );

  return (
    <main className="app">
      {signedIn ? (
        <Cabinet
          api={api}
          onSignedOut={() => {
            setSignedIn(false);
          }}
        />
      ) : (
        <SignIn
          api={api}
          onSignedIn={() => {
            setSignedIn(true);
          }}
        />
      )}
    </main>
  );
}
