import { LaptopIcon, MonitorIcon, SmartphoneIcon, XIcon } from "lucide-react";
import type { DevicePlatform, SessionItem } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";

/** Иконка платформы [platform]: ноутбук для настольных ОС, телефон для мобильных. */
function PlatformIcon({ platform }: { readonly platform: DevicePlatform }) {
  switch (platform) {
    case "MACOS":
    case "WINDOWS":
    case "LINUX":
      return <LaptopIcon aria-hidden className="size-5" />;
    case "IOS":
    case "ANDROID":
      return <SmartphoneIcon aria-hidden className="size-5" />;
    case "OTHER":
      return <MonitorIcon aria-hidden className="size-5" />;
  }
}

/**
 * Строка сеанса [session]: иконка платформы, «браузер · ОС» и строка «IP · последняя
 * активность» (у текущего сеанса — без времени). [onTerminate] показывает кнопку завершения.
 */
export function SessionRow({
  session,
  onTerminate,
}: {
  readonly session: SessionItem;
  readonly onTerminate?: (() => void) | undefined;
}) {
  const { t, format } = useI18n();
  const device = [session.browser, session.os].filter((part) => part !== null).join(" · ");
  const details = [
    session.ip,
    session.current ? null : format.ago(session.lastSeenAt, new Date()),
  ].filter((part) => part !== null);
  return (
    <li className="flex items-center gap-3 py-2">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <PlatformIcon platform={session.platform} />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">
          {device === "" ? t("sessions.unknownDevice") : device}
        </p>
        {details.length > 0 && (
          <p className="truncate text-xs text-muted-foreground">{details.join(" · ")}</p>
        )}
      </div>
      {onTerminate !== undefined && (
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={t("sessions.terminate")}
          onClick={onTerminate}
        >
          <XIcon aria-hidden />
        </Button>
      )}
    </li>
  );
}
