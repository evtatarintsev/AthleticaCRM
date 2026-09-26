import { useQueryClient } from "@tanstack/react-query";
import { ArrowDownIcon, ArrowUpIcon } from "lucide-react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { CustomFieldDefinition } from "@/api/generated/contracts";
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
import { apiErrorMessage } from "@/query/apiErrorMessage";
import {
  availableClientColumns,
  clientColumnKey,
  standardColumnLabelKey,
  type ClientColumn,
} from "./clientColumns";
import { displaySettingsKey } from "./clientsQueries";

/**
 * Диалог настроек колонок таблицы клиентов (паритет с `ClientsSettingsDialog`
 * KMP-клиента): колонка «Имя» всегда включена и не редактируется, включённые
 * колонки выключаются и меняются местами, выключенные — включаются в конец.
 * Каждое изменение сразу сохраняется в настройках отображения.
 */
export function ClientColumnsDialog({
  api,
  open,
  columns,
  customFields,
  onOpenChange,
}: {
  /** Клиент API для сохранения настроек. */
  readonly api: ApiClient;
  /** Диалог открыт. */
  readonly open: boolean;
  /** Включённые колонки из настроек отображения. */
  readonly columns: readonly ClientColumn[];
  /** Определения дополнительных полей для выключенных колонок. */
  readonly customFields: readonly CustomFieldDefinition[];
  /** Смена состояния диалога. */
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const available = availableClientColumns(columns, customFields);

  const save = async (keys: readonly string[]): Promise<void> => {
    const result = await api.call("display-settings/update", { clients: { columns: keys } });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    queryClient.setQueryData(displaySettingsKey, result.value);
  };

  const disable = (column: ClientColumn): void => {
    void save(
      columns
        .filter((candidate) => clientColumnKey(candidate) !== clientColumnKey(column))
        .map(clientColumnKey),
    );
  };

  const enable = (column: ClientColumn): void => {
    void save([...columns, column].map(clientColumnKey));
  };

  const move = (index: number, shift: -1 | 1): void => {
    const target = index + shift;
    if (target < 0 || target >= columns.length) {
      return;
    }
    const reordered = [...columns];
    const moved = reordered.splice(index, 1)[0];
    if (moved === undefined) {
      return;
    }
    reordered.splice(target, 0, moved);
    void save(reordered.map(clientColumnKey));
  };

  const labelOf = (column: ClientColumn): string => {
    switch (column.kind) {
      case "standard":
        return t(standardColumnLabelKey(column.field));
      case "contact":
        return t(`contactType.${column.type}`);
      case "custom":
        return column.label;
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{t("clients.displaySettings")}</DialogTitle>
          <DialogDescription>{t("clients.columns")}</DialogDescription>
        </DialogHeader>
        <div className="max-h-[60vh] space-y-1 overflow-y-auto">
          <ColumnRow
            label={t("clients.column.name")}
            checked
            disabled
            onToggle={() => undefined}
            onMoveUp={null}
            onMoveDown={null}
          />
          {columns.map((column, index) => (
            <ColumnRow
              key={clientColumnKey(column)}
              label={labelOf(column)}
              checked
              onToggle={() => {
                disable(column);
              }}
              onMoveUp={
                index === 0
                  ? null
                  : () => {
                      move(index, -1);
                    }
              }
              onMoveDown={
                index === columns.length - 1
                  ? null
                  : () => {
                      move(index, 1);
                    }
              }
            />
          ))}
          {available.length > 0 && (
            <div className="mt-3 border-t pt-3">
              {available.map((column) => (
                <ColumnRow
                  key={clientColumnKey(column)}
                  label={labelOf(column)}
                  checked={false}
                  onToggle={() => {
                    enable(column);
                  }}
                  onMoveUp={null}
                  onMoveDown={null}
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

/**
 * Строка колонки: чекбокс включения и название; включённая колонка дополнительно
 * с кнопками порядка. Колонка «Имя» — включена, выключить нельзя ([disabled]).
 */
function ColumnRow({
  label,
  checked,
  disabled = false,
  onToggle,
  onMoveUp,
  onMoveDown,
}: {
  readonly label: string;
  readonly checked: boolean;
  /** Чекбокс нельзя переключить (колонка «Имя»). */
  readonly disabled?: boolean;
  readonly onToggle: () => void;
  /** Кнопка «выше»; `null` — кнопки порядка не показываются. */
  readonly onMoveUp: (() => void) | null;
  /** Кнопка «ниже»; `null` — кнопки порядка не показываются. */
  readonly onMoveDown: (() => void) | null;
}) {
  const { t } = useI18n();
  const reorderable = onMoveUp !== null || onMoveDown !== null;
  return (
    <div className="flex items-center gap-2 py-1">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={onToggle}
        aria-label={label}
        className="size-4 accent-primary"
      />
      <span className="min-w-0 flex-1 truncate text-sm">{label}</span>
      {reorderable && (
        <span className="flex gap-1">
          <Button
            variant="ghost"
            size="icon"
            disabled={onMoveUp === null}
            aria-label={t("clients.columnUp", { name: label })}
            onClick={onMoveUp ?? (() => undefined)}
          >
            <ArrowUpIcon aria-hidden />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            disabled={onMoveDown === null}
            aria-label={t("clients.columnDown", { name: label })}
            onClick={onMoveDown ?? (() => undefined)}
          >
            <ArrowDownIcon aria-hidden />
          </Button>
        </span>
      )}
    </div>
  );
}
