import { useI18n } from "../i18n/context";

/** Ошибка загрузки [message] с повтором [onRetry]. */
export function Failure({ message, onRetry }: { message: string; onRetry: () => void }) {
  const { t } = useI18n();
  return (
    <div className="card">
      <p role="alert">{message}</p>
      <button type="button" className="secondary" onClick={onRetry}>
        {t("app.retry")}
      </button>
    </div>
  );
}
