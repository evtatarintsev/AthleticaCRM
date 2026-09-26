import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { PlusIcon, SearchIcon, SendIcon } from "lucide-react";
import { useMemo, useState } from "react";
import type { ApiClient } from "@/api/client";
import type { EmployeeListItem } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { Avatar } from "@/ui/Avatar";
import { PageHeader } from "@/ui/PageHeader";
import { OwnerBadge, RoleChip, StatusBadge } from "./EmployeeBadges";
import { SendAccessDialog } from "./SendAccessDialog";

/** Истина, если [employee] подходит под текст поиска [query]: по имени, email или телефону. */
function matchesSearch(employee: EmployeeListItem, query: string): boolean {
  if (query === "") {
    return true;
  }
  return (
    employee.name.toLocaleLowerCase().includes(query) ||
    (employee.email?.toLocaleLowerCase().includes(query) ?? false) ||
    (employee.phoneNo?.toLocaleLowerCase().includes(query) ?? false)
  );
}

/**
 * Список сотрудников организации: поиск, быстрый фильтр «только активные», таблица на широких
 * экранах и карточки на узких. API отдаёт список целиком без серверной фильтрации, поэтому поиск
 * и фильтр применяются на клиенте. Переход к карточке сотрудника и созданию — ссылками роутера;
 * отправка доступа неактивному сотруднику — диалогом прямо из строки.
 */
export function EmployeesPage({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const employeesQuery = apiQuery(api, branchId, "employees/list");
  const employees = useQuery(employeesQuery);
  const [search, setSearch] = useState("");
  const [onlyActive, setOnlyActive] = useState(false);
  const [sendAccessFor, setSendAccessFor] = useState<EmployeeListItem | null>(null);

  const all = employees.data?.employees ?? [];
  const visible = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    const list = employees.data?.employees ?? [];
    return list
      .filter((employee) => (!onlyActive || employee.isActive) && matchesSearch(employee, query))
      .toSorted((a, b) => a.name.localeCompare(b.name));
  }, [employees.data, search, onlyActive]);

  return (
    <section className="max-w-5xl pb-6">
      <PageHeader
        title={t("employees.title")}
        actions={
          <Button asChild>
            <Link to="/employees/new">
              <PlusIcon aria-hidden />
              {t("employees.add")}
            </Link>
          </Button>
        }
      />
      {employees.isPending && (
        <div className="space-y-2">
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      )}
      {employees.isError && <FormAlert message={t("directory.loadError")} />}
      {employees.data !== undefined && (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative min-w-0 flex-1">
              <SearchIcon
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
              />
              <Input
                type="search"
                value={search}
                onChange={(event) => {
                  setSearch(event.target.value);
                }}
                placeholder={t("employees.searchPlaceholder")}
                aria-label={t("employees.searchPlaceholder")}
                className="pl-9"
              />
            </div>
            <button
              type="button"
              aria-pressed={onlyActive}
              onClick={() => {
                setOnlyActive((v) => !v);
              }}
              className={cn(
                "shrink-0 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors",
                onlyActive
                  ? "border-primary bg-primary/10 text-primary"
                  : "text-muted-foreground hover:bg-accent/50",
              )}
            >
              {t("employees.filterOnlyActive")}
            </button>
          </div>
          {all.length === 0 && (
            <p className="py-12 text-center text-muted-foreground">{t("employees.empty")}</p>
          )}
          {all.length > 0 && visible.length === 0 && (
            <p className="py-12 text-center text-muted-foreground">{t("directory.noResults")}</p>
          )}
          {visible.length > 0 && (
            <>
              <ul className="space-y-2 md:hidden">
                {visible.map((employee) => (
                  <li key={employee.id}>
                    <EmployeeCard
                      api={api}
                      employee={employee}
                      onSendAccess={() => {
                        setSendAccessFor(employee);
                      }}
                    />
                  </li>
                ))}
              </ul>
              <div className="hidden overflow-hidden rounded-md border md:block">
                <table className="w-full text-sm">
                  <thead className="border-b bg-muted/40 text-left text-muted-foreground">
                    <tr>
                      <th className="px-4 py-2 font-medium">{t("employees.name")}</th>
                      <th className="px-4 py-2 font-medium">{t("employees.columnStatus")}</th>
                      <th className="px-4 py-2 font-medium">{t("employees.columnRoles")}</th>
                      <th className="px-4 py-2 font-medium">{t("employees.columnContact")}</th>
                      <th className="px-4 py-2">
                        <span className="sr-only">{t("employees.sendAccess")}</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y">
                    {visible.map((employee) => (
                      <EmployeeRow
                        key={employee.id}
                        api={api}
                        employee={employee}
                        onSendAccess={() => {
                          setSendAccessFor(employee);
                        }}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      )}
      {sendAccessFor !== null && (
        <SendAccessDialog
          api={api}
          employeeId={sendAccessFor.id}
          defaultEmail={sendAccessFor.email}
          onSuccess={() => {
            setSendAccessFor(null);
            void queryClient.invalidateQueries({ queryKey: employeesQuery.queryKey });
          }}
          onClose={() => {
            setSendAccessFor(null);
          }}
        />
      )}
    </section>
  );
}

/** Кнопка отправки доступа для неактивного сотрудника [employee]; для активного — `null`. */
function SendAccessButton({
  employee,
  onSendAccess,
}: {
  employee: EmployeeListItem;
  onSendAccess: () => void;
}) {
  const { t } = useI18n();
  if (employee.isActive) {
    return null;
  }
  return (
    <Button
      variant="ghost"
      size="icon-sm"
      aria-label={t("employees.sendAccessFor", { name: employee.name })}
      onClick={onSendAccess}
    >
      <SendIcon aria-hidden />
    </Button>
  );
}

/** Строка таблицы сотрудников на широких экранах. */
function EmployeeRow({
  api,
  employee,
  onSendAccess,
}: {
  api: ApiClient;
  employee: EmployeeListItem;
  onSendAccess: () => void;
}) {
  return (
    <tr className="hover:bg-muted/30">
      <td className="px-4 py-2">
        <Link
          to="/employees/$employeeId"
          params={{ employeeId: employee.id }}
          className="flex items-center gap-3 rounded-md outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
        >
          <Avatar
            api={api}
            uploadId={employee.avatarId}
            name={employee.name}
            className="size-8 text-xs"
          />
          <span className="min-w-0 truncate font-medium">{employee.name}</span>
          {employee.isOwner && <OwnerBadge />}
        </Link>
      </td>
      <td className="px-4 py-2">
        <StatusBadge active={employee.isActive} />
      </td>
      <td className="px-4 py-2">
        {employee.roles.length === 0 ? (
          <span className="text-muted-foreground">—</span>
        ) : (
          <div className="flex flex-wrap gap-1">
            {employee.roles.map((role) => (
              <RoleChip key={role.id} name={role.name} />
            ))}
          </div>
        )}
      </td>
      <td className="px-4 py-2 text-muted-foreground">
        {employee.email ?? employee.phoneNo ?? "—"}
      </td>
      <td className="px-4 py-2 text-right">
        <SendAccessButton employee={employee} onSendAccess={onSendAccess} />
      </td>
    </tr>
  );
}

/** Карточка сотрудника на узких экранах. */
function EmployeeCard({
  api,
  employee,
  onSendAccess,
}: {
  api: ApiClient;
  employee: EmployeeListItem;
  onSendAccess: () => void;
}) {
  const contact = employee.email ?? employee.phoneNo;
  return (
    <div className="flex items-center gap-3 rounded-lg border bg-card p-3">
      <Link
        to="/employees/$employeeId"
        params={{ employeeId: employee.id }}
        className="flex min-w-0 flex-1 items-center gap-3 rounded-md outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50"
      >
        <Avatar
          api={api}
          uploadId={employee.avatarId}
          name={employee.name}
          className="size-10 text-sm"
        />
        <span className="min-w-0 flex-1 space-y-1">
          <span className="flex flex-wrap items-center gap-1.5">
            <span className="truncate font-medium">{employee.name}</span>
            {employee.isOwner && <OwnerBadge />}
          </span>
          <span className="flex flex-wrap items-center gap-1.5">
            <StatusBadge active={employee.isActive} />
            {employee.roles.map((role) => (
              <RoleChip key={role.id} name={role.name} />
            ))}
          </span>
          {contact !== null && (
            <span className="block truncate text-xs text-muted-foreground">{contact}</span>
          )}
        </span>
      </Link>
      <SendAccessButton employee={employee} onSendAccess={onSendAccess} />
    </div>
  );
}
