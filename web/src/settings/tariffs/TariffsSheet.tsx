import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDownIcon, ChevronRightIcon, PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import { TariffPlanIdSchema, type TariffPlanSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { uuidv7 } from "@/lib/uuid";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { TariffRow } from "./TariffRow";
import { TariffSheet, type TariffTerms } from "./TariffSheet";

/**
 * Что редактируется во вложенной панели: новый тариф или существующий. Значение остаётся
 * после закрытия панели, чтобы её содержимое не пропадало во время анимации закрытия.
 */
type Editing =
  { readonly kind: "create" } | { readonly kind: "edit"; readonly tariff: TariffPlanSchema };

/** Панель тарифов абонементов организации, открытая при [open]. */
export function TariffsSheet({
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
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("tariffs.title")} size="lg">
      <TariffsPanel api={api} />
    </EditSheet>
  );
}

/**
 * Тарифы строками: сначала активные, архивные — в свёрнутом блоке под ними. Создание,
 * изменение, перенос в архив и восстановление открываются панелью тарифа поверх.
 */
function TariffsPanel({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const settings = useQuery(apiQuery(api, branchId, "org/settings"));
  const tariffsQuery = apiQuery(api, branchId, "tariffs/list", { includeArchived: true });
  const tariffs = useQuery(tariffsQuery);
  const [editing, setEditing] = useState<Editing>({ kind: "create" });
  const [editorOpen, setEditorOpen] = useState(false);
  const [archiveExpanded, setArchiveExpanded] = useState(false);

  const all = tariffs.data?.tariffs ?? [];
  const active = all.filter((tariff) => !tariff.archived);
  const archived = all.filter((tariff) => tariff.archived);
  const edited =
    editing.kind === "edit"
      ? (all.find((tariff) => tariff.id === editing.tariff.id) ?? editing.tariff)
      : null;

  const refresh = () => queryClient.invalidateQueries({ queryKey: tariffsQuery.queryKey });

  const save = async (terms: TariffTerms): Promise<string | null> => {
    const result =
      edited === null
        ? await api.call("tariffs/create", { id: TariffPlanIdSchema.parse(uuidv7()), ...terms })
        : await api.call("tariffs/update", { id: edited.id, ...terms });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    return null;
  };

  const toggleArchive = async (): Promise<string | null> => {
    if (edited === null) {
      return null;
    }
    const nowArchived = !edited.archived;
    const result = await api.call("tariffs/archive", { id: edited.id, archived: nowArchived });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    toast.success(nowArchived ? t("tariffs.archivedToast") : t("tariffs.restoredToast"));
    return null;
  };

  const openEditor = (next: Editing) => {
    setEditing(next);
    setEditorOpen(true);
  };

  const currency = settings.data?.currency;
  const failed = settings.isError || tariffs.isError;
  return (
    <>
      <EditSheetBody>
        <div className="flex justify-end">
          <Button
            disabled={currency === undefined}
            onClick={() => {
              openEditor({ kind: "create" });
            }}
          >
            <PlusIcon aria-hidden />
            {t("tariffs.create")}
          </Button>
        </div>
        {failed && <FormAlert message={t("directory.loadError")} />}
        {!failed && (tariffs.isPending || settings.isPending) && (
          <Skeleton className="h-32 w-full" />
        )}
        {tariffs.data !== undefined && all.length === 0 && (
          <p className="py-12 text-center text-muted-foreground">{t("tariffs.empty")}</p>
        )}
        {active.length > 0 && (
          <TariffList
            tariffs={active}
            onOpen={(tariff) => {
              openEditor({ kind: "edit", tariff });
            }}
          />
        )}
        {archived.length > 0 && (
          <div className="space-y-2">
            <button
              type="button"
              aria-expanded={archiveExpanded}
              onClick={() => {
                setArchiveExpanded(!archiveExpanded);
              }}
              className="flex items-center gap-1 rounded-md py-1 text-sm font-medium text-muted-foreground outline-none hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50"
            >
              {archiveExpanded ? (
                <ChevronDownIcon aria-hidden className="size-4" />
              ) : (
                <ChevronRightIcon aria-hidden className="size-4" />
              )}
              {t("tariffs.archivedGroup", { count: archived.length })}
            </button>
            {archiveExpanded && (
              <TariffList
                tariffs={archived}
                muted
                onOpen={(tariff) => {
                  openEditor({ kind: "edit", tariff });
                }}
              />
            )}
          </div>
        )}
      </EditSheetBody>
      {currency !== undefined && (
        <TariffSheet
          open={editorOpen}
          onOpenChange={setEditorOpen}
          initial={edited}
          currency={currency}
          onSave={save}
          onArchiveToggle={toggleArchive}
        />
      )}
    </>
  );
}

/** Тарифы [tariffs] строками-кнопками; нажатие открывает тариф через [onOpen]. */
function TariffList({
  tariffs,
  muted = false,
  onOpen,
}: {
  tariffs: readonly TariffPlanSchema[];
  /** Приглушить строки — для архивных тарифов. */
  muted?: boolean;
  onOpen: (tariff: TariffPlanSchema) => void;
}) {
  const { t } = useI18n();
  return (
    <ul className="space-y-2">
      {tariffs.map((tariff) => (
        <li key={tariff.id}>
          <button
            type="button"
            aria-label={t("directory.edit", { name: tariff.name })}
            onClick={() => {
              onOpen(tariff);
            }}
            className={cn(
              "w-full rounded-lg border bg-card p-4 text-left outline-none hover:bg-accent/40 focus-visible:ring-[3px] focus-visible:ring-ring/50",
              muted && "opacity-60",
            )}
          >
            <TariffRow tariff={tariff} />
          </button>
        </li>
      ))}
    </ul>
  );
}
