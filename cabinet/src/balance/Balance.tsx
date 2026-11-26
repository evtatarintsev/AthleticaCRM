import { useCallback } from "react";
import type { CabinetApi } from "../api/cabinetApi";
import type {
  ClientAppBalanceEntrySchema,
  ClientAppBalanceResponse,
  ClientAppProfileSchema,
} from "../api/generated/contracts";
import { useI18n } from "../i18n/context";
import { Failure } from "../ui/Failure";
import { formatMoney } from "../ui/money";
import { useLoaded } from "../ui/useLoaded";

/** Баланс профиля [profile] через [api]: загрузка и показ данных функцией [children]. */
function LoadedBalance({
  api,
  profile,
  children,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  children: (balance: ClientAppBalanceResponse) => React.ReactNode;
}) {
  const { t } = useI18n();
  const { orgId, clientId } = profile;
  const { state, reload } = useLoaded<ClientAppBalanceResponse>(
    useCallback(() => api.call("client-app/balance", { orgId, clientId }), [api, orgId, clientId]),
  );
  switch (state.kind) {
    case "loading":
      return <p className="muted">{t("app.loading")}</p>;
    case "failed":
      return <Failure message={state.message} onRetry={reload} />;
    case "ready":
      return children(state.value);
  }
}

/** Сумма баланса и число неоплаченных занятий [balance] с подсказкой, если они есть. */
function Summary({ balance }: { balance: ClientAppBalanceResponse }) {
  const { t, locale } = useI18n();
  return (
    <>
      <p className="amount">{formatMoney(balance.balance, locale)}</p>
      {balance.unpaidSessions > 0 && (
        <p>
          {t("balance.unpaid", { count: balance.unpaidSessions })}
          <br />
          <span className="muted">{t("balance.unpaidHint")}</span>
        </p>
      )}
    </>
  );
}

/** Блок баланса профиля [profile] на главной; [onOpen] — открыть историю баланса. */
export function BalanceBlock({
  api,
  profile,
  onOpen,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onOpen: () => void;
}) {
  const { t } = useI18n();
  return (
    <LoadedBalance api={api} profile={profile}>
      {(balance) => (
        <section className="card">
          <h2>{t("balance.title")}</h2>
          <Summary balance={balance} />
          <button type="button" className="link" onClick={onOpen}>
            {t("balance.history")}
          </button>
        </section>
      )}
    </LoadedBalance>
  );
}

/** Экран баланса профиля [profile]: сумма, неоплаченные занятия и история движений; [onBack] — назад. */
export function Balance({
  api,
  profile,
  onBack,
}: {
  api: CabinetApi;
  profile: ClientAppProfileSchema;
  onBack: () => void;
}) {
  const { t, locale } = useI18n();
  const dates = new Intl.DateTimeFormat(locale, { day: "numeric", month: "long", year: "numeric" });
  return (
    <LoadedBalance api={api} profile={profile}>
      {(balance) => (
        <section className="card">
          <h1>{t("balance.title")}</h1>
          <Summary balance={balance} />
          <h2>{t("balance.history")}</h2>
          {balance.entries.length === 0 ? (
            <p className="muted">{t("balance.empty")}</p>
          ) : (
            <ul className="sessions">
              {balance.entries.map((it, index) => (
                <li key={index} className="session">
                  <div>
                    <span>{t(entryKey(it))}</span>
                    <span className="muted">{dates.format(new Date(it.occurredOn))}</span>
                  </div>
                  <strong>{formatMoney(it.amount, locale, true)}</strong>
                </li>
              ))}
            </ul>
          )}
          <button type="button" className="link" onClick={onBack}>
            {t("balance.back")}
          </button>
        </section>
      )}
    </LoadedBalance>
  );
}

/** Ключ словаря с типом операции [entry] понятными словами; корректировка — по знаку суммы. */
function entryKey(entry: ClientAppBalanceEntrySchema) {
  switch (entry.kind) {
    case "SALE":
      return "balance.kind.sale";
    case "SALE_RETURN":
      return "balance.kind.saleReturn";
    case "PAYMENT":
      return "balance.kind.payment";
    case "PAYOUT":
      return "balance.kind.payout";
    case "ADJUSTMENT":
      return entry.amount.minorUnits < 0 ? "balance.kind.debit" : "balance.kind.credit";
    case "INCOME":
      return "balance.kind.income";
    case "EXPENSE":
      return "balance.kind.expense";
    case "TRANSFER":
      return "balance.kind.transfer";
    case "OPENING":
      return "balance.kind.opening";
    case "REVERSAL":
      return "balance.kind.reversal";
  }
}
