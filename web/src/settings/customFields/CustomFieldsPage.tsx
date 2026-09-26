import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PlusIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { CustomFieldDefinition, CustomFieldKey } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { PageHeader } from "@/ui/PageHeader";
import { SelectionBar } from "@/ui/SelectionBar";
import { CustomFieldDialog } from "./CustomFieldDialog";

/** Сущность, к которой относятся дополнительные поля этой страницы. */
const ENTITY_TYPE = "CLIENT";

/** Что редактируется сейчас: ничего, новое поле или существующее. */
type Editing =
  | { readonly kind: "none" }
  | { readonly kind: "create" }
  | { readonly kind: "edit"; readonly definition: CustomFieldDefinition };

/**
 * Дополнительные атрибуты клиентов: таблица полей, создание и изменение в диалоге,
 * удаление выбранных. Сервер принимает набор полей целиком, поэтому каждое изменение
 * отправляет весь список, а ответ сразу кладётся в кэш.
 */
export function CustomFieldsPage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const listQuery = apiQuery(api, branchId, "custom-fields/list", { entityType: ENTITY_TYPE });
  const fields = useQuery(listQuery);
  const [editing, setEditing] = useState<Editing>({ kind: "none" });
  const [selected, setSelected] = useState<ReadonlySet<CustomFieldKey>>(new Set());
  const [confirmDelete, setConfirmDelete] = useState(false);
  const all = fields.data ?? [];

  const saveAll = async (next: readonly CustomFieldDefinition[]): Promise<string | null> => {
    const result = await api.call("custom-fields/save", { entityType: ENTITY_TYPE, fields: next });
    if (!result.ok) {
      return apiErrorMessage(t, result.error);
    }
    queryClient.setQueryData(listQuery.queryKey, result.value);
    return null;
  };

  const save = (definition: CustomFieldDefinition, isNew: boolean) =>
    saveAll(
      isNew
        ? [...all, definition]
        : all.map((f) => (f.fieldKey === definition.fieldKey ? definition : f)),
    );

  const removeSelected = async () => {
    const error = await saveAll(all.filter((f) => !selected.has(f.fieldKey)));
    if (error === null) {
      setSelected(new Set());
      toast.success(t("directory.deleted"));
    } else {
      toast.error(error);
    }
  };

  const toggle = (key: CustomFieldKey, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(key);
      } else {
        next.delete(key);
      }
      return next;
    });
  };

  const otherKeys = (except: CustomFieldKey | null): ReadonlySet<string> =>
    new Set(all.map((f) => f.fieldKey).filter((key) => key !== except));

  return (
    <section className="max-w-3xl pb-20">
      <PageHeader
        title={t("customFields.title")}
        actions={
          <Button
            disabled={fields.data === undefined}
            onClick={() => {
              setEditing({ kind: "create" });
            }}
          >
            <PlusIcon aria-hidden />
            {t("customFields.add")}
          </Button>
        }
      />
      {fields.isPending && <Skeleton className="h-40 w-full" />}
      {fields.isError && <FormAlert message={t("directory.loadError")} />}
      {fields.data?.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">{t("customFields.empty")}</p>
      )}
      {all.length > 0 && (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <input
                    type="checkbox"
                    aria-label={t("directory.selectAll")}
                    checked={selected.size === all.length}
                    onChange={(event) => {
                      setSelected(new Set(event.target.checked ? all.map((f) => f.fieldKey) : []));
                    }}
                    className="size-4 accent-primary"
                  />
                </TableHead>
                <TableHead>{t("customFields.columnLabel")}</TableHead>
                <TableHead>{t("customFields.columnType")}</TableHead>
                <TableHead>{t("customFields.columnRequired")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {all.map((definition) => (
                <TableRow key={definition.fieldKey}>
                  <TableCell>
                    <input
                      type="checkbox"
                      aria-label={t("directory.select", { name: definition.label })}
                      checked={selected.has(definition.fieldKey)}
                      onChange={(event) => {
                        toggle(definition.fieldKey, event.target.checked);
                      }}
                      className="size-4 accent-primary"
                    />
                  </TableCell>
                  <TableCell className="max-w-48 whitespace-normal">
                    <button
                      type="button"
                      aria-label={t("directory.edit", { name: definition.label })}
                      onClick={() => {
                        setEditing({ kind: "edit", definition });
                      }}
                      className="text-left font-medium break-words outline-none hover:underline focus-visible:underline"
                    >
                      {definition.label}
                    </button>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {t(`customFields.type.${definition.fieldType}`)}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {definition.isRequired ? t("common.yes") : t("common.no")}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      <SelectionBar
        count={selected.size}
        actions={
          <Button
            variant="destructive"
            onClick={() => {
              setConfirmDelete(true);
            }}
          >
            {t("directory.deleteSelected")}
          </Button>
        }
      />
      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={t("directory.deleteTitle", { count: selected.size })}
        description={t("directory.deleteText")}
        confirmLabel={t("action.delete")}
        onConfirm={removeSelected}
      />
      {editing.kind !== "none" && (
        <CustomFieldDialog
          initial={editing.kind === "edit" ? editing.definition : null}
          takenKeys={otherKeys(editing.kind === "edit" ? editing.definition.fieldKey : null)}
          onSave={(definition) => save(definition, editing.kind === "create")}
          onClose={() => {
            setEditing({ kind: "none" });
          }}
        />
      )}
    </section>
  );
}
