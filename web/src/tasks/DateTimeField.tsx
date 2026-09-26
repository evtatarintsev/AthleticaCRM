import { useId } from "react";
import { InstantSchema, type Instant } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { useI18n } from "@/i18n/context";

/** Значение `<input type="datetime-local">` для момента [instant] в часовом поясе браузера. */
function toLocalInputValue(instant: Instant): string {
  const date = new Date(instant);
  const pad = (n: number) => n.toString().padStart(2, "0");
  const year = date.getFullYear().toString();
  return `${year}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Момент из значения `<input type="datetime-local">` [value] в часовом поясе браузера. */
function fromLocalInputValue(value: string): Instant {
  return InstantSchema.parse(new Date(value).toISOString());
}

/**
 * Поле даты и времени срока выполнения задачи: `<input type="datetime-local">` с кнопкой
 * очистки, когда значение задано. Паритет с `TaskDateField` KMP-клиента.
 */
export function DateTimeField({
  label,
  value,
  onChange,
  disabled = false,
}: {
  readonly label: string;
  readonly value: Instant | null;
  readonly onChange: (value: Instant | null) => void;
  readonly disabled?: boolean;
}) {
  const { t } = useI18n();
  const id = useId();
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex items-center gap-2">
        <input
          id={id}
          type="datetime-local"
          disabled={disabled}
          value={value === null ? "" : toLocalInputValue(value)}
          onChange={(event) => {
            onChange(event.target.value === "" ? null : fromLocalInputValue(event.target.value));
          }}
          className="h-9 w-full min-w-0 rounded-md border border-input bg-transparent px-3 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
        />
        {value !== null && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={disabled}
            onClick={() => {
              onChange(null);
            }}
          >
            {t("action.clear")}
          </Button>
        )}
      </div>
    </div>
  );
}
