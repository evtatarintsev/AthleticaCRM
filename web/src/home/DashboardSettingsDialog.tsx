import { useQueryClient } from "@tanstack/react-query";
import { GripVerticalIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { DashboardSettings, DashboardWidgetId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Switch } from "@/components/ui/switch";
import { useI18n } from "@/i18n/context";
import {
  defaultTitleKey,
  hideWidget,
  moveWidget,
  showWidget,
  updateDashboardSettings,
} from "./dashboardSettings";

/**
 * Диалог настроек главной (паритет с `DashboardSettingsDialog` KMP-клиента): видимые
 * виджеты переставляются перетаскиванием или стрелками клавиатуры, включаются и скрываются. Названия типов
 * фиксированы, индивидуальные настройки доступны из карточек. Каждое изменение сразу сохраняется.
 */
export function DashboardSettingsDialog({
  api,
  open,
  settings,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly open: boolean;
  readonly settings: DashboardSettings;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const widgets = settings.widgets;
  const [drag, setDrag] = useState<{
    id: DashboardWidgetId;
    targetId: DashboardWidgetId;
  } | null>(null);

  const save = (update: (current: DashboardSettings) => DashboardSettings): void => {
    updateDashboardSettings(api, queryClient, settings, update);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{t("home.dashboardSettingsTitle")}</DialogTitle>
          <DialogDescription>{t("home.dashboardSettingsDescription")}</DialogDescription>
        </DialogHeader>
        <div className="max-h-[60vh] space-y-3 overflow-y-auto">
          {widgets.map((widget, index) => (
            <div
              key={widget.id}
              data-widget-id={widget.id}
              className={`flex items-center gap-2 rounded-md border p-2 ${
                drag?.id === widget.id ? "opacity-50" : ""
              } ${drag?.targetId === widget.id ? "ring-2 ring-primary" : ""}`}
            >
              <Button
                variant="ghost"
                size="icon-sm"
                className="touch-none cursor-grab active:cursor-grabbing"
                aria-label={t("home.moveWidget", { name: t(defaultTitleKey(widget.type)) })}
                onKeyDown={(event) => {
                  if (event.key === "ArrowUp" || event.key === "ArrowDown") {
                    event.preventDefault();
                    const shift = event.key === "ArrowUp" ? -1 : 1;
                    save((current) => moveWidget(current, index, shift));
                  }
                }}
                onPointerDown={(event) => {
                  if (event.button !== 0) return;
                  event.currentTarget.setPointerCapture(event.pointerId);
                  setDrag({ id: widget.id, targetId: widget.id });
                }}
                onPointerMove={(event) => {
                  if (drag === null) return;
                  const row = document
                    .elementFromPoint(event.clientX, event.clientY)
                    ?.closest("[data-widget-id]");
                  const target = widgets.find(
                    (item) => item.id === row?.getAttribute("data-widget-id"),
                  );
                  if (target !== undefined && target.id !== drag.targetId) {
                    setDrag({ ...drag, targetId: target.id });
                  }
                }}
                onPointerUp={() => {
                  if (drag !== null && drag.id !== drag.targetId) {
                    save((current) => {
                      const from = current.widgets.findIndex((item) => item.id === drag.id);
                      const to = current.widgets.findIndex((item) => item.id === drag.targetId);
                      return from < 0 || to < 0 ? current : moveWidget(current, from, to - from);
                    });
                  }
                  setDrag(null);
                }}
                onPointerCancel={() => {
                  setDrag(null);
                }}
                onLostPointerCapture={() => {
                  setDrag(null);
                }}
              >
                <GripVerticalIcon aria-hidden />
              </Button>
              <span
                className={`min-w-0 flex-1 truncate text-sm font-medium ${settings.layout.includes(widget.id) ? "" : "text-muted-foreground"}`}
              >
                {t(defaultTitleKey(widget.type))}
              </span>
              <Switch
                checked={settings.layout.includes(widget.id)}
                aria-label={t("home.widgetVisible", { name: t(defaultTitleKey(widget.type)) })}
                onCheckedChange={(checked) => {
                  save((current) =>
                    checked ? showWidget(current, widget.id) : hideWidget(current, widget.id),
                  );
                }}
              />
            </div>
          ))}
        </div>
        <DialogFooter closeLabel={t("action.close")} />
      </DialogContent>
    </Dialog>
  );
}
