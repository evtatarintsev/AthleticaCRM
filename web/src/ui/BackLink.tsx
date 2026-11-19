import { Link, type RegisteredRouter, type ValidateLinkOptions } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";

/** Возврат из карточки по фиксированному адресу [link] независимо от истории браузера. */
export function BackLink<TOptions>({
  link,
}: {
  readonly link: ValidateLinkOptions<RegisteredRouter, TOptions>;
}): ReactNode;
export function BackLink({ link }: { readonly link: ValidateLinkOptions }): ReactNode {
  const { t } = useI18n();
  return (
    <Button variant="ghost" size="sm" asChild className="-ml-2">
      <Link {...link}>
        <ArrowLeftIcon aria-hidden />
        {t("action.back")}
      </Link>
    </Button>
  );
}
