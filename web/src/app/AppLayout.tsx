import { useSuspenseQuery } from "@tanstack/react-query";
import { Outlet } from "@tanstack/react-router";
import { MenuIcon } from "lucide-react";
import { useState } from "react";
import { AssistantHost } from "@/assistant/AssistantHost";
import type { ApiClient } from "@/api/client";
import { DemoBanner } from "@/auth/DemoBanner";
import { EmailVerificationBanner } from "@/auth/EmailVerificationBanner";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { useI18n } from "@/i18n/context";
import { useInboxCounter } from "@/messaging/inboxQueries";
import { sessionQuery } from "@/query/queries";
import { Logo } from "@/ui/Logo";
import { AccountMenu } from "./AccountMenu";
import { ClubCard } from "./ClubCard";
import { Navigation } from "./Navigation";
import { NotificationBell } from "./NotificationBell";

/**
 * Раскладка страниц за логином. На экранах от 1024 px навигация — боковая панель с карточкой
 * клуба, на узких — выезжающее меню, а карточка клуба стоит в верхней полосе.
 * [api] загружает сессию, [onLogout] завершает её.
 */
export function AppLayout({ api, onLogout }: { api: ApiClient; onLogout: () => Promise<void> }) {
  const { t } = useI18n();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const [menuOpen, setMenuOpen] = useState(false);
  const [assistantOpen, setAssistantOpen] = useState(false);
  const badges = { inbox: useInboxCounter(api, me.currentBranch.id) };
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <aside className="sticky top-0 hidden h-dvh flex-col gap-6 border-r bg-card px-3 py-4 lg:flex">
        <ClubCard api={api} me={me} variant="menu" className="w-full" />
        <Navigation badges={badges} canViewFinance={me.permissions.includes("CAN_VIEW_FINANCE")} />
        <ProductMark />
      </aside>
      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-40 flex h-14 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur lg:px-8">
          <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
            <SheetTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="lg:hidden"
                aria-label={t("nav.openMenu")}
              >
                <MenuIcon aria-hidden />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" closeLabel={t("action.close")} className="w-72 px-3 py-4">
              <SheetTitle className="sr-only">{t("nav.label")}</SheetTitle>
              <div className="pr-10">
                <ClubCard api={api} me={me} variant="sheet" className="w-full" />
              </div>
              <Navigation
                canViewFinance={me.permissions.includes("CAN_VIEW_FINANCE")}
                badges={badges}
                onNavigate={() => {
                  setMenuOpen(false);
                }}
              />
              <ProductMark />
            </SheetContent>
          </Sheet>
          <ClubCard api={api} me={me} variant="sheet" className="h-11 flex-1 py-1 lg:hidden" />
          <div className="ml-auto flex shrink-0 items-center gap-1">
            <AssistantHost
              key={me.employeeId}
              api={api}
              employeeId={me.employeeId}
              open={assistantOpen}
              onOpenChange={setAssistantOpen}
            />
            <NotificationBell api={api} branchId={me.currentBranch.id} />
            <AccountMenu
              api={api}
              me={me}
              onLogout={() => {
                void onLogout();
              }}
              onOpenAssistant={() => {
                setAssistantOpen(true);
              }}
            />
          </div>
        </header>
        {me.demo && <DemoBanner />}
        {me.emailVerificationPending && <EmailVerificationBanner api={api} email={me.username} />}
        <main className="min-w-0 flex-1 px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** Логотип продукта внизу меню, не ссылка. */
function ProductMark() {
  return (
    <div className="mt-auto border-t px-2 pt-3">
      <Logo className="w-44 max-w-full" />
    </div>
  );
}
