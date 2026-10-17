import { useQueryClient } from "@tanstack/react-query";
import { MinusIcon, PlusIcon, SettingsIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { BirthdayWindow, DashboardSettings, DashboardWidget } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { useI18n } from "@/i18n/context";
import {
  clampWidgetLimit,
  defaultTitleKey,
  hideWidget,
  replaceWidget,
  updateDashboardSettings,
} from "./dashboardSettings";

/** Кнопка и компактный диалог настроек карточки [widget]. */
export function WidgetSettingsButton({
  api,
  settings,
  widget,
}: {
  readonly api: ApiClient;
  readonly settings: DashboardSettings;
  readonly widget: DashboardWidget;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const title = t(defaultTitleKey(widget.type));

  const updateWidget = (update: (widget: DashboardWidget) => DashboardWidget): void => {
    updateDashboardSettings(api, queryClient, settings, (current) => {
      const existing = current.widgets.find((candidate) => candidate.id === widget.id);
      return existing === undefined ? current : replaceWidget(current, update(existing));
    });
  };

  return (
    <>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={t("home.widgetSettingsTitle", { name: title })}
        onClick={() => {
          setOpen(true);
        }}
      >
        <SettingsIcon aria-hidden />
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className="max-h-[85vh] max-w-sm overflow-y-auto"
          closeLabel={t("action.close")}
        >
          <DialogHeader>
            <DialogTitle>{t("home.widgetSettingsTitle", { name: title })}</DialogTitle>
            <DialogDescription>{t("home.widgetSettingsDescription")}</DialogDescription>
          </DialogHeader>
          <label className="space-y-1.5 text-sm font-medium">
            {t("home.widgetTitle")}
            <Input
              value={widget.title ?? ""}
              aria-label={t("home.widgetTitle")}
              onChange={(event) => {
                const value = event.target.value;
                updateWidget((current) => ({
                  ...current,
                  title: value.trim() === "" ? null : value,
                }));
              }}
            />
          </label>
          <WidgetSettingsControls
            widget={widget}
            onChange={(updated) => {
              updateWidget((current) => mergeWidgetSettings(current, updated));
            }}
          />
          <DialogFooter className="gap-2 sm:justify-between">
            <Button
              type="button"
              variant="destructive"
              onClick={() => {
                updateDashboardSettings(api, queryClient, settings, (current) =>
                  hideWidget(current, widget.id),
                );
                setOpen(false);
              }}
            >
              {t("home.widgetHide")}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setOpen(false);
              }}
            >
              {t("action.close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

/** Настройки, доступные для типа виджета [widget]. */
export function WidgetSettingsControls({
  widget,
  onChange,
}: {
  readonly widget: DashboardWidget;
  readonly onChange: (widget: DashboardWidget) => void;
}) {
  switch (widget.type) {
    case "sessions":
      return null;
    case "debtors":
    case "unclosedSessions":
      return (
        <LimitStepper
          limit={widget.limit}
          onChange={(limit) => {
            onChange({ ...widget, limit });
          }}
        />
      );
    case "birthdays":
      return (
        <div className="flex flex-wrap items-center gap-2">
          <WindowSelector
            window={widget.window}
            onChange={(window) => {
              onChange({ ...widget, window });
            }}
          />
          <LimitStepper
            limit={widget.limit}
            onChange={(limit) => {
              onChange({ ...widget, limit });
            }}
          />
        </div>
      );
  }
}

/** Обновляет только параметры [current], не затирая название актуального виджета. */
function mergeWidgetSettings(current: DashboardWidget, updated: DashboardWidget): DashboardWidget {
  if (current.type !== updated.type) {
    return current;
  }
  switch (current.type) {
    case "sessions":
      return current;
    case "debtors":
      return updated.type === "debtors" ? { ...current, limit: updated.limit } : current;
    case "unclosedSessions":
      return updated.type === "unclosedSessions" ? { ...current, limit: updated.limit } : current;
    case "birthdays":
      return updated.type === "birthdays"
        ? { ...current, window: updated.window, limit: updated.limit }
        : current;
  }
}

/** Выбор периода дней рождения. */
function WindowSelector({
  window,
  onChange,
}: {
  readonly window: BirthdayWindow;
  readonly onChange: (window: BirthdayWindow) => void;
}) {
  const { t } = useI18n();
  return (
    <NativeSelect
      value={window}
      aria-label={t("clients.filter.birthday")}
      className="w-auto"
      onChange={(event) => {
        const value = event.target.value;
        if (value === "TODAY" || value === "TOMORROW" || value === "WEEK") {
          onChange(value);
        }
      }}
    >
      <option value="TODAY">{t("clients.filter.birthdayToday")}</option>
      <option value="TOMORROW">{t("clients.filter.birthdayTomorrow")}</option>
      <option value="WEEK">{t("clients.filter.birthdayWeek")}</option>
    </NativeSelect>
  );
}

/** Степпер лимита виджета в диапазоне `[MIN_WIDGET_LIMIT, MAX_WIDGET_LIMIT]`. */
function LimitStepper({
  limit,
  onChange,
}: {
  readonly limit: number;
  readonly onChange: (limit: number) => void;
}) {
  const { t } = useI18n();
  return (
    <div className="flex items-center gap-1 text-sm">
      <span className="text-muted-foreground">{t("home.widgetLimit", { limit })}</span>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={t("home.widgetLimitDecrease")}
        onClick={() => {
          onChange(clampWidgetLimit(limit - 1));
        }}
      >
        <MinusIcon aria-hidden />
      </Button>
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={t("home.widgetLimitIncrease")}
        onClick={() => {
          onChange(clampWidgetLimit(limit + 1));
        }}
      >
        <PlusIcon aria-hidden />
      </Button>
    </div>
  );
}
