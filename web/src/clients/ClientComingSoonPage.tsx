import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon } from "lucide-react";
import type { ClientId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { PageHeader } from "@/ui/PageHeader";

/**
 * Заглушечный экран раздела карточки клиента, который ещё не реализован ни в одном
 * из клиентов (история посещений, история платежей): паритет с текущим поведением
 * KMP-клиента, где эти экраны — тоже заглушки без реальных данных.
 */
export function ClientComingSoonPage({
  clientId,
  title,
}: {
  readonly clientId: ClientId;
  readonly title: string;
}) {
  const { t } = useI18n();
  return (
    <section className="max-w-3xl space-y-4">
      <PageHeader title={title} />
      <Button variant="outline" size="sm" asChild>
        <Link to="/clients/$clientId" params={{ clientId }}>
          <ArrowLeftIcon aria-hidden />
          {t("action.back")}
        </Link>
      </Button>
      <p className="py-12 text-center text-muted-foreground">{t("common.comingSoon")}</p>
    </section>
  );
}
