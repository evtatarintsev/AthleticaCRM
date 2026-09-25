import { useEffect } from "react";
import { Skeleton } from "@/components/ui/skeleton";

/** Переход на страницу [href] KMP-клиента для раздела, который ещё не перенесён. */
export function KmpRedirect({ href }: { href: string }) {
  useEffect(() => {
    window.location.replace(href);
  }, [href]);
  return <Skeleton className="h-8 w-48" />;
}
