import { useQueryClient } from "@tanstack/react-query";
import { ArrowDownIcon, ArrowUpIcon, MinusIcon, PlusIcon } from "lucide-react";
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
import { displaySettingsKey } from "@/clients/clientsQueries";
import { useI18n } from "@/i18n/context";
import {
  clampWidgetLimit,
  defaultTitleKey,
  hiddenWidgets,
  hideWidget,
  moveVisibleWidget,
  orderedVisible,
  replaceWidget,
  showWidget,
} from "./dashboardSettings";

/**
 * Диалог настроек главной (паритет с `DashboardSettingsDialog` KMP-клиента): видимые
 * виджеты переставляются кнопками порядка (в веб-клиенте вместо перетаскивания — тот же
 * приём, что в настройках колонок клиентов), каждый переименовывается и скрывается,
 * скрытые виджеты показываются обратно. Каждое изменение сразу сохраняется.
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

  const save = (next: DashboardSettings): void => {
    queryClient.setQueryData(displaySettingsKey, (old) =>
      old === undefined ? old : { ...old, dashboard: next },
    );
    void api.call("display-settings/update", { dashboard: next });
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
                      save(moveVisibleWidget(settings, index, -1));
                    }
              }
              onMoveDown={
                index === visible.length - 1
                  ? null
                  : () => {
                      save(moveVisibleWidget(settings, index, 1));
                    }
              }
              onTitleChange={(title) => {
                save(replaceWidget(settings, { ...widget, title: title === "" ? null : title }));
              }}
              onHide={() => {
                save(hideWidget(settings, widget.id));
              }}
              onWidgetChange={(updated) => {
                save(replaceWidget(settings, updated));
              }}
            />
          ))}
          {hidden.length > 0 && (
            <div className="space-y-1 border-t pt-3">
              {hidden.map((widget) => (
                <HiddenWidgetRow
                  key={widget.id}
                  widget={widget}
                  onShow={() => {
                    save(showWidget(settings, widget.id));
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
  onTitleChange,
  onHide,
  onWidgetChange,
}: {
  readonly widget: DashboardWidget;
  readonly onMoveUp: (() => void) | null;
  readonly onMoveDown: (() => void) | null;
  readonly onTitleChange: (title: string) => void;
  readonly onHide: () => void;
  readonly onWidgetChange: (widget: DashboardWidget) => void;
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
            aria-label={t("home.moveWidgetUp")}
            onClick={onMoveUp ?? (() => undefined)}
          >
            <ArrowUpIcon aria-hidden />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            disabled={onMoveDown === null}
            aria-label={t("home.moveWidgetDown")}
            onClick={onMoveDown ?? (() => undefined)}
          >
            <ArrowDownIcon aria-hidden />
          </Button>
        </span>
        <Input
          value={widget.title ?? ""}
          placeholder={t(defaultTitleKey(widget.type))}
          aria-label={t("home.widgetTitle")}
          onChange={(event) => {
            onTitleChange(event.target.value);
          }}
          className="min-w-0 flex-1"
        />
        <label className="flex shrink-0 items-center gap-1.5 text-sm">
          <input
            type="checkbox"
            checked
            aria-label={t("home.widgetVisible")}
            onChange={onHide}
            className="size-4 accent-primary"
          />
        </label>
      </div>
      <WidgetSettingsControls widget={widget} onChange={onWidgetChange} />
    </div>
  );
}

/** Строка скрытого виджета: название по умолчанию и переключатель, включающий его обратно. */
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
      <span className="min-w-0 flex-1 truncate text-sm">
        {widget.title !== null && widget.title.trim() !== ""
          ? widget.title
          : t(defaultTitleKey(widget.type))}
      </span>
      <input
        type="checkbox"
        checked={false}
        aria-label={t("home.widgetVisible")}
        onChange={onShow}
        className="size-4 accent-primary"
      />
    </div>
  );
}

/** Дополнительные настройки виджета [widget]: лимит для «Должников», окно и лимит для «Дней рождения». */
function WidgetSettingsControls({
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

/** Выбор окна дней рождения для виджета «Дни рождения». */
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
