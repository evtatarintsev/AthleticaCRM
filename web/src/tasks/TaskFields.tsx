import { ChevronRightIcon, XIcon } from "lucide-react";
import { useId, useMemo, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { TextAreaField, TextField } from "@/forms/fields";
import { useI18n } from "@/i18n/context";
import { DateTimeField } from "./DateTimeField";
import { titleSchema, type TaskForm } from "./taskFormValues";

/**
 * Общие поля создания и редактирования задачи: заголовок, описание, клиент и сроки.
 * Клиент выбирается панелью, которую открывает [onPickClient]; сама панель рендерится
 * экраном формы среди вложенных. При [onPickClient] `null` клиент задан контекстом и
 * показывается только для чтения.
 */
export function TaskFields({
  form,
  onPickClient,
}: {
  readonly form: TaskForm;
  readonly onPickClient: (() => void) | null;
}) {
  const { t } = useI18n();
  const schema = useMemo(() => titleSchema(t), [t]);
  return (
    <>
      <form.Field name="title" validators={{ onSubmit: schema }}>
        {(field) => <TextField field={field} label={t("tasks.field.title")} required />}
      </form.Field>
      <form.Field name="description">
        {(field) => <TextAreaField field={field} label={t("tasks.field.description")} rows={3} />}
      </form.Field>
      <form.Field name="client">
        {(field) =>
          onPickClient === null ? (
            <ReadOnlyField
              label={t("tasks.field.client")}
              value={field.state.value?.name ?? t("tasks.noClient")}
            />
          ) : (
            <PickerField
              label={t("tasks.field.client")}
              value={field.state.value?.name ?? null}
              emptyText={t("tasks.noClient")}
              onOpen={onPickClient}
              action={
                field.state.value !== null && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={t("tasks.removeClient")}
                    onClick={() => {
                      field.handleChange(null);
                    }}
                  >
                    <XIcon aria-hidden />
                  </Button>
                )
              }
            />
          )
        }
      </form.Field>
      <form.Field name="dueDate">
        {(field) => (
          <DateTimeField
            label={t("tasks.field.dueDate")}
            value={field.state.value}
            onChange={(value) => {
              field.handleChange(value);
            }}
          />
        )}
      </form.Field>
      <form.Field name="dueDateEnd">
        {(field) => (
          <DateTimeField
            label={t("tasks.field.dueDateEnd")}
            value={field.state.value}
            onChange={(value) => {
              field.handleChange(value);
            }}
          />
        )}
      </form.Field>
    </>
  );
}

/**
 * Поле, значение которого выбирается в отдельной панели: подпись [label], текущее
 * значение [value] (или [emptyText]) кнопкой, открывающей панель [onOpen], и
 * необязательное действие [action] справа — например, очистка.
 */
export function PickerField({
  label,
  value,
  emptyText,
  onOpen,
  disabled = false,
  action,
}: {
  readonly label: string;
  readonly value: string | null;
  readonly emptyText: string;
  readonly onOpen: () => void;
  readonly disabled?: boolean;
  readonly action?: ReactNode;
}) {
  const id = useId();
  const labelId = `${id}-label`;
  const valueId = `${id}-value`;
  return (
    <div className="space-y-1.5">
      <span id={labelId} className="text-sm leading-none font-medium">
        {label}
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={disabled}
          aria-labelledby={`${labelId} ${valueId}`}
          onClick={onOpen}
          className="flex h-9 min-w-0 flex-1 items-center gap-2 rounded-md border border-input px-3 text-left text-base shadow-xs outline-none hover:bg-accent focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50 md:text-sm dark:bg-input/30"
        >
          <span
            id={valueId}
            className={value === null ? "flex-1 truncate text-muted-foreground" : "flex-1 truncate"}
          >
            {value ?? emptyText}
          </span>
          <ChevronRightIcon aria-hidden className="size-4 shrink-0 text-muted-foreground" />
        </button>
        {action}
      </div>
    </div>
  );
}

/**
 * Поле, значение которого задано контекстом и не меняется: подпись [label] и значение
 * [value] в рамке тех же размеров, что у [PickerField], но без кнопки выбора.
 */
export function ReadOnlyField({
  label,
  value,
}: {
  readonly label: string;
  readonly value: string;
}) {
  const labelId = useId();
  return (
    <div className="space-y-1.5">
      <span id={labelId} className="text-sm leading-none font-medium">
        {label}
      </span>
      <p
        aria-labelledby={labelId}
        className="flex h-9 items-center truncate rounded-md border border-input bg-muted/50 px-3 text-base md:text-sm"
      >
        {value}
      </p>
    </div>
  );
}
