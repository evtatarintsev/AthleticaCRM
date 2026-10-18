import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { AuditLogItem, AuditLogSubject } from "@/api/generated/contracts";
import {
  ClientIdSchema,
  EmployeeIdSchema,
  GroupIdSchema,
  SessionIdSchema,
  UserIdSchema,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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

/**
 * Журнал действий пользователей организации: постраничный список с фильтром по типу
 * действия. У этого экрана нет прообраза в KMP-клиенте — журнал там не был доведён до UI.
 */
export function AuditLogPage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const [page, setPage] = useState(0);
  const [actionType, setActionType] = useState<string>("");
  const [userId, setUserId] = useState<string>("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const query = apiQuery(api, branchId, "audit/log", {
    page,
    pageSize: PAGE_SIZE,
    actionType: actionType === "" ? null : actionType,
    userId: userId === "" ? null : UserIdSchema.parse(userId),
    from: from === "" ? null : `${from}T00:00:00Z`,
    to: to === "" ? null : `${to}T23:59:59.999Z`,
  });
  const users = useQuery(apiQuery(api, branchId, "audit/log/users"));
  const log = useQuery(query);
  const hasNextPage = log.data !== undefined && (page + 1) * PAGE_SIZE < log.data.total;

  return (
    <section className="max-w-4xl">
      <PageHeader title={t("audit.title")} />
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <NativeSelect
          value={userId}
          aria-label={t("audit.filterUser")}
          className="w-auto"
          onChange={(event) => {
            setUserId(event.target.value);
            setPage(0);
          }}
        >
          <option value="">{t("audit.filterUserAll")}</option>
          {users.data?.items.map((user) => (
            <option key={user.id} value={user.id}>
              {user.username}
            </option>
          ))}
        </NativeSelect>
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
          {Object.entries(ACTION_LABEL_KEYS).map(([type, labelKey]) => (
            <option key={type} value={type}>
              {t(labelKey)}
            </option>
          ))}
        </NativeSelect>
        <Input
          type="date"
          value={from}
          aria-label={t("audit.filterFrom")}
          className="w-auto"
          onChange={(event) => {
            setFrom(event.target.value);
            setPage(0);
          }}
        />
        <Input
          type="date"
          value={to}
          aria-label={t("audit.filterTo")}
          className="w-auto"
          onChange={(event) => {
            setTo(event.target.value);
            setPage(0);
          }}
        />
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
  const { locale } = useI18n();
  const createdAt = new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(item.createdAt));
  return (
    <TableRow>
      <TableCell className="whitespace-nowrap text-sm text-muted-foreground">{createdAt}</TableCell>
      <TableCell>{item.username}</TableCell>
      <TableCell>{item.description}</TableCell>
      <TableCell className="text-sm text-muted-foreground">
        {item.subject === null ? "—" : <AuditSubjectLink subject={item.subject} />}
      </TableCell>
    </TableRow>
  );
}

/** Показывает название субъекта и создаёт ссылку только для поддерживаемого маршрута. */
function AuditSubjectLink({ subject }: { readonly subject: AuditLogSubject }) {
  if (!subject.isAvailable) {
    return <span>{subject.title}</span>;
  }

  switch (subject.type) {
    case "client":
      return (
        <Link
          to="/clients/$clientId"
          params={{ clientId: ClientIdSchema.parse(subject.id) }}
          className="hover:underline"
        >
          {subject.title}
        </Link>
      );
    case "client_doc":
      return subject.targetId === null ? (
        <span>{subject.title}</span>
      ) : (
        <Link
          to="/clients/$clientId"
          params={{ clientId: ClientIdSchema.parse(subject.targetId) }}
          className="hover:underline"
        >
          {subject.title}
        </Link>
      );
    case "group":
      return (
        <Link
          to="/groups/$groupId"
          params={{ groupId: GroupIdSchema.parse(subject.id) }}
          className="hover:underline"
        >
          {subject.title}
        </Link>
      );
    case "employee":
      return (
        <Link
          to="/employees"
          search={{ employee: EmployeeIdSchema.parse(subject.id) }}
          className="hover:underline"
        >
          {subject.title}
        </Link>
      );
    case "session":
      return (
        <Link
          to="/sessions/$sessionId"
          params={{ sessionId: SessionIdSchema.parse(subject.id) }}
          className="hover:underline"
        >
          {subject.title}
        </Link>
      );
    case "branch":
      return (
        <Link to="/settings" search={{ panel: "branches" }} className="hover:underline">
          {subject.title}
        </Link>
      );
    case "hall":
      return (
        <Link to="/settings" search={{ panel: "halls" }} className="hover:underline">
          {subject.title}
        </Link>
      );
    case "discipline":
      return (
        <Link to="/settings" search={{ panel: "disciplines" }} className="hover:underline">
          {subject.title}
        </Link>
      );
    case "lead_source":
      return (
        <Link to="/settings" search={{ panel: "client-sources" }} className="hover:underline">
          {subject.title}
        </Link>
      );
    case "subscription_tariff":
      return (
        <Link to="/settings" search={{ panel: "tariffs" }} className="hover:underline">
          {subject.title}
        </Link>
      );
    case "channel_integration":
      return (
        <Link to="/settings/channels" className="hover:underline">
          {subject.title}
        </Link>
      );
    default:
      return <span>{subject.title}</span>;
  }
}
