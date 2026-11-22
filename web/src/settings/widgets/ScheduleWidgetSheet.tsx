import { useForm } from "@tanstack/react-form";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CopyIcon, KeyRoundIcon, TrashIcon } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";
import { toast } from "sonner";
import { z } from "zod";
import type { ApiClient } from "@/api/client";
import {
  ScheduleWidgetIdSchema,
  type GroupId,
  type ScheduleWidgetDetailResponse,
  type ScheduleWidgetId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { CheckboxField, TextField } from "@/forms/fields";
import { GroupChips, GroupChipsSection } from "@/groups/GroupChips";
import { useI18n, type I18n } from "@/i18n/context";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ChecklistSheet } from "@/ui/ChecklistSheet";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheet, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { widgetIframeCode, widgetUrl } from "./widgetEmbed";

/** Значения формы виджета; горизонт — как введён, число проверяется при отправке. */
interface WidgetValues {
  readonly name: string;
  readonly enabled: boolean;
  readonly horizon: string;
  readonly showCoaches: boolean;
  readonly showHall: boolean;
  readonly showDisciplines: boolean;
  readonly excludedGroupIds: readonly GroupId[];
}

/** Настройки нового виджета — те же, что сервер подставляет по умолчанию. */
const NEW_WIDGET: WidgetValues = {
  name: "",
  enabled: true,
  horizon: "30",
  showCoaches: false,
  showHall: true,
  showDisciplines: true,
  excludedGroupIds: [],
};

/** Значения формы для сохранённого виджета [widget]. */
function valuesOf(widget: ScheduleWidgetDetailResponse): WidgetValues {
  return { ...widget, horizon: String(widget.horizon) };
}

/** Схема горизонта показа: целое число дней от 7 до 90 с сообщением на языке [t]. */
function horizonSchema(t: I18n["t"]) {
  return z
    .string()
    .trim()
    .regex(/^\d+$/, t("widgets.horizonInvalid"))
    .transform(Number)
    .pipe(z.int().min(7, t("widgets.horizonInvalid")).max(90, t("widgets.horizonInvalid")));
}

/**
 * Панель виджета [widgetId]: `null` — создание. После создания панель остаётся открытой
 * карточкой нового виджета ([onCreated]), чтобы сразу показать ссылку и код встраивания.
 */
export function ScheduleWidgetSheet({
  api,
  open,
  onOpenChange,
  widgetId,
  onCreated,
}: {
  readonly api: ApiClient;
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly widgetId: ScheduleWidgetId | null;
  readonly onCreated: (id: ScheduleWidgetId) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={open}
      onOpenChange={onOpenChange}
      title={t(widgetId === null ? "widgets.createTitle" : "widgets.editTitle")}
      size="lg"
    >
      {widgetId === null ? (
        <WidgetForm
          api={api}
          initial={null}
          onSaved={(widget) => {
            onCreated(widget.id);
          }}
        />
      ) : (
        <ExistingWidget key={widgetId} api={api} id={widgetId} />
      )}
    </EditSheet>
  );
}

/**
 * Карточка сохранённого виджета [id]: форма настроек и блок встраивания. После сохранения
 * форма пересоздаётся из ответа сервера, а предпросмотр перезагружается.
 */
function ExistingWidget({ api, id }: { readonly api: ApiClient; readonly id: ScheduleWidgetId }) {
  const branchId = useSession(api).currentBranch.id;
  const detail = useQuery(apiQuery(api, branchId, "schedule-widgets/detail", { id }));
  const [version, setVersion] = useState(0);
  if (detail.data === undefined) {
    return <Skeleton className="m-4 h-64" />;
  }
  return (
    <WidgetForm
      key={version}
      api={api}
      initial={detail.data}
      onSaved={() => {
        setVersion((current) => current + 1);
      }}
    >
      <WidgetEmbed api={api} widget={detail.data} version={version} />
    </WidgetForm>
  );
}

/**
 * Форма настроек виджета [initial] (`null` — новый): название, включённость, горизонт,
 * показываемые поля и исключённые группы филиала. Ответ сервера кладётся в кэш карточки
 * и передаётся в [onSaved]; [children] — содержимое под полями.
 */
function WidgetForm({
  api,
  initial,
  onSaved,
  children,
}: {
  readonly api: ApiClient;
  readonly initial: ScheduleWidgetDetailResponse | null;
  readonly onSaved: (widget: ScheduleWidgetDetailResponse) => void;
  readonly children?: ReactNode;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const groups = useQuery(apiQuery(api, branchId, "groups/list-for-select"));
  const nameSchema = useMemo(() => z.string().trim().min(1, t("error.required")), [t]);
  const horizon = useMemo(() => horizonSchema(t), [t]);
  const [failure, setFailure] = useState<string | null>(null);
  const [picker, setPicker] = useState(false);

  const form = useForm({
    defaultValues: initial === null ? NEW_WIDGET : valuesOf(initial),
    onSubmit: async ({ value }) => {
      const name = nameSchema.safeParse(value.name);
      const days = horizon.safeParse(value.horizon);
      if (!name.success || !days.success) {
        return;
      }
      setFailure(null);
      const settings = {
        name: name.data,
        enabled: value.enabled,
        horizon: days.data,
        showCoaches: value.showCoaches,
        showHall: value.showHall,
        showDisciplines: value.showDisciplines,
        excludedGroupIds: value.excludedGroupIds,
      };
      const result =
        initial === null
          ? await api.call("schedule-widgets/create", {
              id: ScheduleWidgetIdSchema.parse(uuidv7()),
              ...settings,
            })
          : await api.call("schedule-widgets/edit", { id: initial.id, ...settings });
      if (!result.ok) {
        setFailure(apiErrorMessage(t, result.error));
        return;
      }
      queryClient.setQueryData(
        apiQuery(api, branchId, "schedule-widgets/detail", { id: result.value.id }).queryKey,
        result.value,
      );
      await queryClient.invalidateQueries({
        queryKey: ["api", branchId, "schedule-widgets/list"],
      });
      toast.success(t("widgets.saved"));
      onSaved(result.value);
    },
  });

  const groupItems = groups.data ?? [];
  return (
    <form.Subscribe
      selector={(state) => ({ dirty: state.isDirty, submitting: state.isSubmitting })}
    >
      {({ dirty, submitting }) => (
        <EditSheetForm
          dirty={dirty}
          submitting={submitting}
          {...(initial === null ? { submitLabel: t("action.create") } : {})}
          onSubmit={() => {
            void form.handleSubmit();
          }}
          nested={
            <form.Subscribe selector={(state) => state.values.excludedGroupIds}>
              {(excluded) => (
                <ChecklistSheet
                  open={picker}
                  onOpenChange={setPicker}
                  title={t("widgets.excludedGroupsPicker")}
                  items={groupItems}
                  selected={excluded}
                  emptyText={t("groups.picker.empty")}
                  submitLabel={t("action.done")}
                  onSubmit={(ids) => {
                    form.setFieldValue("excludedGroupIds", ids);
                    return Promise.resolve(null);
                  }}
                />
              )}
            </form.Subscribe>
          }
        >
          {failure !== null && <FormAlert message={failure} />}
          <form.Field name="name" validators={{ onSubmit: nameSchema }}>
            {(field) => (
              <TextField field={field} label={t("widgets.name")} autoComplete="off" required />
            )}
          </form.Field>
          <form.Field name="enabled">
            {(field) => (
              <CheckboxField
                field={field}
                label={t("widgets.enabled")}
                hint={t("widgets.enabledHint")}
              />
            )}
          </form.Field>
          <form.Field name="horizon" validators={{ onSubmit: horizon }}>
            {(field) => (
              <TextField
                field={field}
                label={t("widgets.horizon")}
                hint={t("widgets.horizonHint")}
                inputMode="numeric"
                autoComplete="off"
                required
              />
            )}
          </form.Field>
          <fieldset className="space-y-2">
            <legend className="mb-1 text-sm font-medium">{t("widgets.fields")}</legend>
            <form.Field name="showDisciplines">
              {(field) => <CheckboxField field={field} label={t("widgets.showDisciplines")} />}
            </form.Field>
            <form.Field name="showHall">
              {(field) => <CheckboxField field={field} label={t("widgets.showHall")} />}
            </form.Field>
            <form.Field name="showCoaches">
              {(field) => <CheckboxField field={field} label={t("widgets.showCoaches")} />}
            </form.Field>
          </fieldset>
          <form.Subscribe selector={(state) => state.values.excludedGroupIds}>
            {(excluded) => (
              <GroupChipsSection
                title={t("widgets.excludedGroups")}
                onEdit={() => {
                  setPicker(true);
                }}
              >
                <GroupChips
                  items={groupItems.filter((g) => excluded.includes(g.id))}
                  emptyText={t("widgets.excludedGroupsEmpty")}
                />
              </GroupChipsSection>
            )}
          </form.Subscribe>
          {children}
        </EditSheetForm>
      )}
    </form.Subscribe>
  );
}

/**
 * Встраивание сохранённого виджета [widget]: предпросмотр (перезагружается при смене
 * [version]), ссылка и код iframe с копированием, инструкция для ВКонтакте, перевыпуск
 * ключа и удаление с подтверждением.
 */
function WidgetEmbed({
  api,
  widget,
  version,
}: {
  readonly api: ApiClient;
  readonly widget: ScheduleWidgetDetailResponse;
  readonly version: number;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const { close } = useEditSheet();
  const branchId = useSession(api).currentBranch.id;
  const [confirm, setConfirm] = useState<"regenerate" | "delete" | null>(null);
  const url = widgetUrl(window.location.origin, widget.publicKey);
  const code = widgetIframeCode(url, t("widgets.iframeTitle"));

  const copy = async (text: string): Promise<void> => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(t("widgets.copied"));
    } catch {
      toast.error(t("widgets.copyFailed"));
    }
  };

  const regenerate = async (): Promise<void> => {
    const result = await api.call("schedule-widgets/regenerate-key", { id: widget.id });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    queryClient.setQueryData(
      apiQuery(api, branchId, "schedule-widgets/detail", { id: widget.id }).queryKey,
      result.value,
    );
    toast.success(t("widgets.regenerated"));
  };

  const remove = async (): Promise<void> => {
    const result = await api.call("schedule-widgets/delete", { id: widget.id });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await queryClient.invalidateQueries({ queryKey: ["api", branchId, "schedule-widgets/list"] });
    toast.success(t("widgets.deleted"));
    close();
  };

  return (
    <section aria-label={t("widgets.embedTitle")} className="space-y-4 border-t pt-4">
      <div className="space-y-2">
        <h3 className="text-sm font-medium">{t("widgets.preview")}</h3>
        <iframe
          key={`${widget.publicKey}-${String(version)}`}
          src={url}
          title={t("widgets.iframeTitle")}
          className="h-96 w-full rounded-md border"
        />
      </div>
      <CopyBlock
        label={t("widgets.link")}
        value={url}
        onCopy={() => {
          void copy(url);
        }}
      />
      <CopyBlock
        label={t("widgets.iframeCode")}
        value={code}
        onCopy={() => {
          void copy(code);
        }}
      />
      <div className="space-y-1">
        <h3 className="text-sm font-medium">{t("widgets.vkTitle")}</h3>
        <p className="text-sm whitespace-pre-line text-muted-foreground">{t("widgets.vkText")}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setConfirm("regenerate");
          }}
        >
          <KeyRoundIcon aria-hidden />
          {t("widgets.regenerateKey")}
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setConfirm("delete");
          }}
        >
          <TrashIcon aria-hidden className="text-destructive" />
          {t("widgets.delete")}
        </Button>
      </div>
      <ConfirmDialog
        open={confirm !== null}
        onOpenChange={(open) => {
          if (!open) {
            setConfirm(null);
          }
        }}
        title={
          confirm === "delete"
            ? t("widgets.deleteTitle", { name: widget.name })
            : t("widgets.regenerateTitle")
        }
        description={t(confirm === "delete" ? "widgets.deleteText" : "widgets.regenerateText")}
        confirmLabel={t(confirm === "delete" ? "action.delete" : "widgets.regenerateKey")}
        onConfirm={async () => {
          await (confirm === "delete" ? remove() : regenerate());
          setConfirm(null);
        }}
      />
    </section>
  );
}

/** Значение [value] только для чтения с подписью [label] и кнопкой копирования. */
function CopyBlock({
  label,
  value,
  onCopy,
}: {
  readonly label: string;
  readonly value: string;
  readonly onCopy: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-medium">{label}</h3>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          aria-label={t("widgets.copyLabel", { label })}
          onClick={onCopy}
        >
          <CopyIcon aria-hidden />
          {t("widgets.copy")}
        </Button>
      </div>
      <pre className="overflow-x-auto rounded-md bg-muted p-2 text-xs break-all whitespace-pre-wrap">
        {value}
      </pre>
    </div>
  );
}
