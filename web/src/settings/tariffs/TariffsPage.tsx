import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArchiveIcon, ArchiveRestoreIcon, PencilIcon, PlusIcon } from "lucide-react";
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
import { PageHeader } from "@/ui/PageHeader";
import { TariffDialog, type TariffTerms } from "./TariffDialog";

/** Что редактируется сейчас: ничего, новый тариф или существующий. */
type Editing =
  | { readonly kind: "none" }
  | { readonly kind: "create" }
  | { readonly kind: "edit"; readonly tariff: TariffPlanSchema };

/**
 * Тарифы абонементов, включая архивные: плитки с условиями и стоимостью в валюте
 * организации, создание и изменение в диалоге, перенос в архив и восстановление.
 */
export function TariffsPage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const settings = useQuery(apiQuery(api, branchId, "org/settings"));
  const tariffsQuery = apiQuery(api, branchId, "tariffs/list", { includeArchived: true });
  const tariffs = useQuery(tariffsQuery);
  const [editing, setEditing] = useState<Editing>({ kind: "none" });

  const refresh = () => queryClient.invalidateQueries({ queryKey: tariffsQuery.queryKey });

  const save = async (terms: TariffTerms, tariff: TariffPlanSchema | null) => {
    const result =
      tariff === null
        ? await api.call("tariffs/create", { id: TariffPlanIdSchema.parse(uuidv7()), ...terms })
        : await api.call("tariffs/update", { id: tariff.id, ...terms });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    await refresh();
    return null;
  };

  const toggleArchive = async (tariff: TariffPlanSchema) => {
    const archived = !tariff.archived;
    const result = await api.call("tariffs/archive", { id: tariff.id, archived });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await refresh();
    toast.success(archived ? t("tariffs.archivedToast") : t("tariffs.restoredToast"));
  };

  const currency = settings.data?.currency;
  const failed = settings.isError || tariffs.isError;
  return (
    <section className="max-w-5xl">
      <PageHeader
        title={t("tariffs.title")}
        actions={
          <Button
            disabled={currency === undefined}
            onClick={() => {
              setEditing({ kind: "create" });
            }}
          >
            <PlusIcon aria-hidden />
            {t("tariffs.create")}
          </Button>
        }
      />
      {failed && <FormAlert message={t("directory.loadError")} />}
      {!failed && (tariffs.isPending || settings.isPending) && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
        </div>
      )}
      {tariffs.data?.tariffs.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">{t("tariffs.empty")}</p>
      )}
      {tariffs.data !== undefined && tariffs.data.tariffs.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tariffs.data.tariffs.map((tariff) => (
            <li key={tariff.id}>
              <TariffCard
                tariff={tariff}
                onEdit={() => {
                  setEditing({ kind: "edit", tariff });
                }}
                onArchiveToggle={() => {
                  void toggleArchive(tariff);
                }}
              />
            </li>
          ))}
        </ul>
      )}
      {editing.kind !== "none" && currency !== undefined && (
        <TariffDialog
          title={editing.kind === "create" ? t("tariffs.create") : t("tariffs.edit")}
          initial={editing.kind === "edit" ? editing.tariff : null}
          currency={currency}
          onSave={(terms) => save(terms, editing.kind === "edit" ? editing.tariff : null)}
          onClose={() => {
            setEditing({ kind: "none" });
          }}
        />
      )}
    </section>
  );
}

/** Плитка тарифа [tariff]: условия, стоимость и действия; архивный тариф приглушён. */
function TariffCard({
  tariff,
  onEdit,
  onArchiveToggle,
}: {
  tariff: TariffPlanSchema;
  onEdit: () => void;
  onArchiveToggle: () => void;
}) {
  const { t, format } = useI18n();
  const sessions =
    tariff.sessions === null
      ? t("tariffs.sessionsUnlimited")
      : t("tariffs.sessionsCount", { count: tariff.sessions });
  const duration = durationText(t, tariff);
  return (
    <article
      className={cn(
        "flex h-full flex-col gap-3 rounded-lg border bg-card p-4",
        tariff.archived && "opacity-60",
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <h2 className="min-w-0 font-semibold break-words">{tariff.name}</h2>
        {tariff.archived && (
          <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            {t("tariffs.archived")}
          </span>
        )}
      </div>
      <p className="text-sm text-muted-foreground">
        {sessions} · {duration}
      </p>
      <p className="mt-auto text-xl font-semibold tabular-nums">{format.money(tariff.price)}</p>
      <div className="flex gap-1">
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={
            tariff.archived
              ? t("tariffs.restoreTariff", { name: tariff.name })
              : t("tariffs.archiveTariff", { name: tariff.name })
          }
          onClick={onArchiveToggle}
        >
          {tariff.archived ? <ArchiveRestoreIcon aria-hidden /> : <ArchiveIcon aria-hidden />}
        </Button>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t("tariffs.editTariff", { name: tariff.name })}
          onClick={onEdit}
        >
          <PencilIcon aria-hidden />
        </Button>
      </div>
    </article>
  );
}

/** Срок действия тарифа [tariff] на языке [t]. */
function durationText(t: ReturnType<typeof useI18n>["t"], tariff: TariffPlanSchema): string {
  switch (tariff.durationUnit) {
    case "DAYS":
      return t("tariffs.durationDays", { count: tariff.durationValue });
    case "MONTHS":
      return t("tariffs.durationMonths", { count: tariff.durationValue });
  }
}
