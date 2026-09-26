import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { AuditLogItem } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/native-select";
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
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";

/** Размер страницы журнала действий. */
const PAGE_SIZE = 50;

/** Подпись каждого типа действия журнала — ключи в порядке показа в фильтре. */
const ACTION_LABEL_KEYS: Readonly<Record<string, PlainMessageKey>> = {
  create: "audit.action.create",
  update: "audit.action.update",
  delete: "audit.action.delete",
  auth_login: "audit.action.auth_login",
  auth_logout: "audit.action.auth_logout",
  auth_signup: "audit.action.auth_signup",
  message_send: "audit.action.message_send",
  export: "audit.action.export",
  import: "audit.action.import",
  auth_change_password: "audit.action.auth_change_password",
  balance_adjust: "audit.action.balance_adjust",
};

/** Типы действий журнала в порядке показа в фильтре. */
const ACTION_TYPES: readonly string[] = Object.keys(ACTION_LABEL_KEYS);

/** Ключ подписи типа действия [actionType]; неизвестный код показывается как есть. */
function actionLabelKey(actionType: string): PlainMessageKey | null {
  return ACTION_LABEL_KEYS[actionType] ?? null;
}

/**
 * Журнал действий пользователей организации: постраничный список с фильтром по типу
 * действия. У этого экрана нет прообраза в KMP-клиенте — журнал там не был доведён до UI.
 */
export function AuditLogPage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const [page, setPage] = useState(0);
  const [actionType, setActionType] = useState<string>("");

  const query = apiQuery(api, branchId, "audit/log", {
    page,
    pageSize: PAGE_SIZE,
    actionType: actionType === "" ? null : actionType,
  });
  const log = useQuery(query);
  const hasNextPage = log.data !== undefined && (page + 1) * PAGE_SIZE < log.data.total;

  return (
    <section className="max-w-4xl">
      <PageHeader title={t("audit.title")} />
      <div className="mb-4 flex items-center gap-2">
        <NativeSelect
          value={actionType}
          aria-label={t("audit.filterAction")}
          className="w-auto"
          onChange={(event) => {
            setActionType(event.target.value);
            setPage(0);
          }}
        >
          <option value="">{t("audit.filterActionAll")}</option>
          {ACTION_TYPES.map((type) => (
            <option key={type} value={type}>
              {t(ACTION_LABEL_KEYS[type] ?? "audit.filterActionAll")}
            </option>
          ))}
        </NativeSelect>
      </div>
      {log.isError && <FormAlert message={t("audit.loadError")} />}
      {log.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      )}
      {log.data?.items.length === 0 && (
        <p className="py-12 text-center text-muted-foreground">{t("audit.empty")}</p>
      )}
      {log.data !== undefined && log.data.items.length > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("audit.columnTime")}</TableHead>
                <TableHead>{t("audit.columnUser")}</TableHead>
                <TableHead>{t("audit.columnAction")}</TableHead>
                <TableHead>{t("audit.columnEntity")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {log.data.items.map((item) => (
                <AuditRow key={item.id} item={item} />
              ))}
            </TableBody>
          </Table>
          <div className="mt-3 flex items-center justify-between">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => {
                setPage((p) => Math.max(0, p - 1));
              }}
            >
              {t("audit.prevPage")}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t("audit.page", { page: page + 1 })}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={!hasNextPage}
              onClick={() => {
                setPage((p) => p + 1);
              }}
            >
              {t("audit.nextPage")}
            </Button>
          </div>
        </>
      )}
    </section>
  );
}

/** Строка журнала действий. */
function AuditRow({ item }: { readonly item: AuditLogItem }) {
  const { t, locale } = useI18n();
  const labelKey = actionLabelKey(item.actionType);
  const createdAt = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(item.createdAt));
  return (
    <TableRow>
      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">{createdAt}</TableCell>
      <TableCell>{item.username}</TableCell>
      <TableCell>{labelKey === null ? item.actionType : t(labelKey)}</TableCell>
      <TableCell className="text-sm text-muted-foreground">{item.entityType ?? "—"}</TableCell>
    </TableRow>
  );
}
