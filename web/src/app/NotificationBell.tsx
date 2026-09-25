import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BellIcon, CheckCheckIcon, CheckIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BranchId, NotificationItem } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";

/** Как часто перезапрашивать уведомления — как в KMP-клиенте. */
const REFRESH_INTERVAL_MS = 60 * 1000;

/** Больше этого числа бейдж показывает «99+». */
const BADGE_LIMIT = 99;

/**
 * Колокольчик уведомлений с числом непрочитанных. Список открывается выезжающей панелью;
 * уведомление можно отметить прочитанным по одному или все сразу. Отметка сразу видна
 * в интерфейсе, затем список перечитывается с сервера.
 */
export function NotificationBell({ api, branchId }: { api: ApiClient; branchId: BranchId }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const query = apiQuery(api, branchId, "notifications", {});
  const { data, isPending, isError } = useQuery({
    ...query,
    refetchInterval: REFRESH_INTERVAL_MS,
  });
  const unread = data?.unreadCount ?? 0;

  const markRead = async (ids: readonly string[] | "all") => {
    queryClient.setQueryData(query.queryKey, (old) =>
      old === undefined
        ? old
        : {
            notifications: old.notifications.map((n) =>
              ids === "all" || ids.includes(n.id) ? { ...n, isRead: true } : n,
            ),
            unreadCount:
              ids === "all"
                ? 0
                : old.notifications.filter((n) => !n.isRead && !ids.includes(n.id)).length,
          },
    );
    await (ids === "all"
      ? api.call("notifications/mark-all-read")
      : api.call("notifications/mark-as-read", { ids }));
    await queryClient.invalidateQueries({ queryKey: query.queryKey });
  };

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          aria-label={
            unread > 0 ? t("notifications.open", { count: unread }) : t("notifications.title")
          }
        >
          <BellIcon aria-hidden />
          {unread > 0 && (
            <span
              aria-hidden
              className="absolute top-1 right-1 min-w-4 rounded-full bg-destructive px-1 text-[10px] leading-4 font-semibold text-white"
            >
              {unread > BADGE_LIMIT ? `${String(BADGE_LIMIT)}+` : unread}
            </span>
          )}
        </Button>
      </SheetTrigger>
      <SheetContent
        side="right"
        closeLabel={t("action.close")}
        aria-describedby={undefined}
        className="w-full gap-0 sm:max-w-sm"
      >
        <SheetHeader className="flex-row items-center justify-between gap-2 border-b pr-12">
          <SheetTitle>{t("notifications.title")}</SheetTitle>
          {unread > 0 && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                void markRead("all");
              }}
            >
              <CheckCheckIcon aria-hidden />
              {t("notifications.markAllRead")}
            </Button>
          )}
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto">
          {isPending && (
            <div className="space-y-3 p-4">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          )}
          {isError && data === undefined && (
            <p className="p-4 text-sm text-destructive">{t("notifications.loadError")}</p>
          )}
          {data?.notifications.length === 0 && (
            <p className="p-8 text-center text-sm text-muted-foreground">
              {t("notifications.empty")}
            </p>
          )}
          {data !== undefined && data.notifications.length > 0 && (
            <ul className="divide-y">
              {data.notifications.map((notification) => (
                <NotificationRow
                  key={notification.id}
                  notification={notification}
                  onMarkRead={() => {
                    void markRead([notification.id]);
                  }}
                />
              ))}
            </ul>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

/** Строка уведомления [notification]; у непрочитанного — отметка и кнопка [onMarkRead]. */
function NotificationRow({
  notification,
  onMarkRead,
}: {
  notification: NotificationItem;
  onMarkRead: () => void;
}) {
  const { t, format } = useI18n();
  return (
    <li className={cn("flex gap-3 px-4 py-3", !notification.isRead && "bg-accent/40")}>
      <span
        className={cn(
          "mt-1.5 size-2 shrink-0 rounded-full",
          notification.isRead ? "bg-transparent" : "bg-primary",
        )}
      >
        {!notification.isRead && <span className="sr-only">{t("notifications.unread")}</span>}
      </span>
      <div className="min-w-0 flex-1 space-y-0.5">
        <p className={cn("text-sm break-words", !notification.isRead && "font-semibold")}>
          {notification.title}
        </p>
        <p className="text-sm break-words text-muted-foreground">{notification.body}</p>
        <time dateTime={notification.createdAt} className="text-xs text-muted-foreground">
          {format.ago(notification.createdAt, new Date())}
        </time>
      </div>
      {!notification.isRead && (
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t("notifications.markRead")}
          onClick={onMarkRead}
        >
          <CheckIcon aria-hidden className="text-primary" />
        </Button>
      )}
    </li>
  );
}
