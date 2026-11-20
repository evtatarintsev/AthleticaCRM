import { Link, type RegisteredRouter, type ValidateLinkOptions } from "@tanstack/react-router";
import type { MouseEvent, ReactNode } from "react";
import { ArrowLeftIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";

/**
 * Возврат из карточки. Адрес ссылки всегда [link], поэтому её можно открыть в новой вкладке.
 * Если задан [onBack], обычный клик без клавиш-модификаторов вызывает его вместо перехода
 * по [link] — например, чтобы вернуться на предыдущую страницу истории.
 */
export function BackLink<TOptions>({
  link,
  onBack,
}: {
  readonly link: ValidateLinkOptions<RegisteredRouter, TOptions>;
  readonly onBack?: (() => void) | undefined;
}): ReactNode;
export function BackLink({
  link,
  onBack,
}: {
  readonly link: ValidateLinkOptions;
  readonly onBack?: (() => void) | undefined;
}): ReactNode {
  const { t } = useI18n();
  const handleClick = (event: MouseEvent) => {
    if (onBack === undefined || isModifiedClick(event)) {
      return;
    }
    event.preventDefault();
    onBack();
  };
  return (
    <Button variant="ghost" size="sm" asChild className="-ml-2">
      <Link {...link} onClick={handleClick}>
        <ArrowLeftIcon aria-hidden />
        {t("action.back")}
      </Link>
    </Button>
  );
}

/** Клик, которым браузер открывает ссылку иначе: в новой вкладке, окне или для скачивания. */
function isModifiedClick(event: MouseEvent): boolean {
  return event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey;
}
