import { cn } from "@/lib/utils";
import type { ChangeTone, ReportChangeView } from "./reportFormat";

/** Классы плашки изменения по оттенку. */
const toneClasses: Readonly<Record<ChangeTone, string>> = {
  good: "bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300",
  bad: "bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300",
  neutral: "bg-muted text-muted-foreground",
};

/** Плашка изменения к прошлому периоду: зелёная — к лучшему, красная — к худшему. */
export function ReportChangeBadge({ change }: { readonly change: ReportChangeView }) {
  return (
    <span
      data-tone={change.tone}
      className={cn(
        "inline-flex h-6 items-center rounded-full px-2 text-xs font-semibold whitespace-nowrap",
        toneClasses[change.tone],
      )}
    >
      {change.text}
    </span>
  );
}
