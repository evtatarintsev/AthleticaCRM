import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";

/** Возврат из карточки в список независимо от истории браузера. */
export function BackLink({ to }: { readonly to: "/clients" | "/groups" | "/schedule" }) {
  const { t } = useI18n();
  return (
    <Button variant="ghost" size="sm" asChild className="-ml-2">
      <Link to={to} search={{}}>
        <ArrowLeftIcon aria-hidden />
        {t("action.back")}
      </Link>
    </Button>
  );
}
