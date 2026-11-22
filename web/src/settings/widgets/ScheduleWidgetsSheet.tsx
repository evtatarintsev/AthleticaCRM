import { useQuery } from "@tanstack/react-query";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { ScheduleWidgetId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { ScheduleWidgetSheet } from "./ScheduleWidgetSheet";

/** Панель виджетов расписания текущего филиала, открытая при [open]. */
export function ScheduleWidgetsSheet({
  api,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("widgets.title")} size="lg">
      <ScheduleWidgetsPanel api={api} />
    </EditSheet>
  );
}

/**
 * Виджеты филиала строками; создание и карточка виджета открываются панелью поверх.
 * [editing] — виджет в панели: `null` — новый. Значение остаётся после закрытия панели,
 * чтобы её содержимое не пропадало во время анимации закрытия.
 */
function ScheduleWidgetsPanel({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const widgets = useQuery(apiQuery(api, branchId, "schedule-widgets/list"));
  const [editing, setEditing] = useState<ScheduleWidgetId | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);

  const openEditor = (id: ScheduleWidgetId | null) => {
    setEditing(id);
    setEditorOpen(true);
  };

  return (
    <>
      <EditSheetBody>
        <p className="text-sm text-muted-foreground">{t("widgets.intro")}</p>
        <div className="flex justify-end">
          <Button
            onClick={() => {
              openEditor(null);
            }}
          >
            <PlusIcon aria-hidden />
            {t("widgets.create")}
          </Button>
        </div>
        {widgets.isError && <FormAlert message={t("directory.loadError")} />}
        {widgets.isPending && <Skeleton className="h-24 w-full" />}
        {widgets.data?.widgets.length === 0 && (
          <p className="py-12 text-center text-muted-foreground">{t("widgets.empty")}</p>
        )}
        {widgets.data !== undefined && widgets.data.widgets.length > 0 && (
          <ul className="divide-y rounded-lg border bg-card">
            {widgets.data.widgets.map((widget) => (
              <li key={widget.id}>
                <button
                  type="button"
                  aria-label={t("directory.edit", { name: widget.name })}
                  onClick={() => {
                    openEditor(widget.id);
                  }}
                  className="flex w-full items-center gap-3 px-4 py-3 text-left outline-none hover:bg-accent/40 focus-visible:ring-[3px] focus-visible:ring-ring/50"
                >
                  <span className="min-w-0 flex-1 truncate font-medium">{widget.name}</span>
                  <span className="text-sm text-muted-foreground">
                    {t(widget.enabled ? "widgets.stateEnabled" : "widgets.stateDisabled")}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </EditSheetBody>
      <ScheduleWidgetSheet
        api={api}
        open={editorOpen}
        onOpenChange={setEditorOpen}
        widgetId={editing}
        onCreated={setEditing}
      />
    </>
  );
}
