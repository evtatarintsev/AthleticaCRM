import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";

/** Ошибка загрузки сессии: сервис недоступен или ответил не по контракту. [onRetry] перезапускает проверку. */
export function SessionErrorPage({ onRetry }: { onRetry: () => void }) {
  const { t } = useI18n();
  return (
    <main className="mx-auto flex min-h-dvh max-w-sm flex-col justify-center gap-4 px-4">
      <FormAlert message={t("session.loadError")} />
      <Button onClick={onRetry}>{t("action.retry")}</Button>
    </main>
  );
}
