import { useQueryClient } from "@tanstack/react-query";
import { ArrowDownIcon, ArrowUpIcon } from "lucide-react";
import type { ApiClient } from "@/api/client";
import type { DashboardSettings, DashboardWidget } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useI18n } from "@/i18n/context";
import {
  defaultTitleKey,
  hiddenWidgets,
  hideWidget,
  moveVisibleWidget,
  orderedVisible,
  showWidget,
  updateDashboardSettings,
} from "./dashboardSettings";

/**
 * Диалог настроек главной (паритет с `DashboardSettingsDialog` KMP-клиента): видимые
 * виджеты переставляются кнопками порядка, включаются и скрываются. Названия типов
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
  const visible = orderedVisible(settings);
  const hidden = hiddenWidgets(settings);

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
          {visible.map((widget, index) => (
            <VisibleWidgetRow
              key={widget.id}
              widget={widget}
              onMoveUp={
                index === 0
                  ? null
                  : () => {
                      save((current) => moveVisibleWidget(current, index, -1));
                    }
              }
              onMoveDown={
                index === visible.length - 1
                  ? null
                  : () => {
                      save((current) => moveVisibleWidget(current, index, 1));
                    }
              }
              onToggleVisibility={() => {
                save((current) => hideWidget(current, widget.id));
              }}
            />
          ))}
          {hidden.length > 0 && (
            <div className="space-y-1 border-t pt-3">
              <h3 className="px-1 text-sm font-medium">{t("home.hiddenWidgets")}</h3>
              {hidden.map((widget) => (
                <HiddenWidgetRow
                  key={widget.id}
                  widget={widget}
                  onShow={() => {
                    save((current) => showWidget(current, widget.id));
                  }}
                />
              ))}
            </div>
          )}
        </div>
        <DialogFooter closeLabel={t("action.close")} />
      </DialogContent>
    </Dialog>
  );
}

/** Строка видимого виджета: название, переключатель видимости и настройки типа. */
function VisibleWidgetRow({
  widget,
  onMoveUp,
  onMoveDown,
  onToggleVisibility,
}: {
  readonly widget: DashboardWidget;
  readonly onMoveUp: (() => void) | null;
  readonly onMoveDown: (() => void) | null;
  readonly onToggleVisibility: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="space-y-2 rounded-md border p-2">
      <div className="flex items-center gap-2">
        <span className="flex shrink-0 flex-col">
          <Button
            variant="ghost"
            size="icon-sm"
            disabled={onMoveUp === null}
            aria-label={t("home.moveWidgetUp", { name: t(defaultTitleKey(widget.type)) })}
            onClick={onMoveUp ?? (() => undefined)}
          >
            <ArrowUpIcon aria-hidden />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            disabled={onMoveDown === null}
            aria-label={t("home.moveWidgetDown", { name: t(defaultTitleKey(widget.type)) })}
            onClick={onMoveDown ?? (() => undefined)}
          >
            <ArrowDownIcon aria-hidden />
          </Button>
        </span>
        <span className="min-w-0 flex-1 truncate text-sm font-medium">
          {t(defaultTitleKey(widget.type))}
        </span>
        <input
          type="checkbox"
          checked
          aria-label={t("home.widgetVisible", { name: t(defaultTitleKey(widget.type)) })}
          onChange={onToggleVisibility}
          className="size-4 shrink-0 accent-primary"
        />
      </div>
    </div>
  );
}

/** Строка скрытого виджета с фиксированным названием типа и включающим переключателем. */
function HiddenWidgetRow({
  widget,
  onShow,
}: {
  readonly widget: DashboardWidget;
  readonly onShow: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="flex items-center gap-2 px-1 py-1 text-muted-foreground">
      <span className="min-w-0 flex-1 truncate text-sm">{t(defaultTitleKey(widget.type))}</span>
      <input
        type="checkbox"
        checked={false}
        aria-label={t("home.widgetVisible", { name: t(defaultTitleKey(widget.type)) })}
        onChange={onShow}
        className="size-4 accent-primary"
      />
    </div>
  );
}
