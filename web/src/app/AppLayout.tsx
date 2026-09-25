import { useSuspenseQuery } from "@tanstack/react-query";
import { Outlet } from "@tanstack/react-router";
import { MenuIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { useI18n } from "@/i18n/context";
import { sessionQuery } from "@/query/queries";
import { Logo } from "@/ui/AuthLayout";
import { AccountMenu } from "./AccountMenu";
import { Navigation } from "./Navigation";

/**
 * Раскладка страниц за логином. На экранах от 1024 px навигация — боковая панель,
 * на узких — выезжающее меню. [api] загружает сессию, [onLogout] завершает её.
 */
export function AppLayout({ api, onLogout }: { api: ApiClient; onLogout: () => Promise<void> }) {
  const { t } = useI18n();
  const { data: me } = useSuspenseQuery(sessionQuery(api));
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <aside className="sticky top-0 hidden h-dvh flex-col gap-6 border-r bg-card px-3 py-4 lg:flex">
        <Brand name={me.orgInfo.name} />
        <Navigation />
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
                <Brand name={me.orgInfo.name} />
              </div>
              <Navigation
                onNavigate={() => {
                  setMenuOpen(false);
                }}
              />
            </SheetContent>
          </Sheet>
          <div className="min-w-0 lg:hidden">
            <Brand name={me.orgInfo.name} />
          </div>
          <div className="ml-auto">
            <AccountMenu
              me={me}
              onLogout={() => {
                void onLogout();
              }}
            />
          </div>
        </header>
        <main className="min-w-0 flex-1 px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** Знак приложения и название организации [name]. */
function Brand({ name }: { name: string }) {
  return (
    <div className="flex min-w-0 items-center gap-2 px-1">
      <Logo />
      <span className="truncate font-semibold tracking-tight">{name}</span>
    </div>
  );
}
