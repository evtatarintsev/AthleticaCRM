import { Link } from "@tanstack/react-router";
import { buttonVariants } from "@/components/ui/button-variants";
import { useI18n } from "@/i18n/context";

/**
 * Страница возврата с оплаты ЮKassa: платёж обрабатывается вебхуком асинхронно,
 * поэтому здесь только ссылка назад на баланс — он перечитается с сервера при открытии.
 */
export function PaymentCompletePage() {
  const { t } = useI18n();
  return (
    <section className="max-w-prose space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">{t("orgBalance.completeTitle")}</h1>
      <p className="text-muted-foreground">{t("orgBalance.completeMessage")}</p>
      <Link to="/settings/org-balance" className={buttonVariants()}>
        {t("orgBalance.title")}
      </Link>
    </section>
  );
}
