import { useQuery, useQueryClient } from "@tanstack/react-query";
import { BanIcon, LockOpenIcon, PencilIcon, SendIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  EmployeeIdSchema,
  type AuthSessionId,
  type EmployeeDetailResponse,
  type EmployeeId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { SessionRow } from "@/account/SessionRow";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { Avatar } from "@/ui/Avatar";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { LastSeen, OwnerBadge, RoleChip, StatusBadge } from "./EmployeeBadges";
import { EmployeeSheet } from "./EmployeeSheet";
import { SendAccessDialog } from "./SendAccessDialog";

/**
 * Заглушка идентификатора для ключа запроса закрытой панели: запрос с ней не отправляется
 * (`enabled: false`), но ключ должен иметь тот же тип.
 */
const NO_EMPLOYEE = EmployeeIdSchema.parse("00000000-0000-0000-0000-000000000000");

/** Свойства панели карточки сотрудника. */
interface EmployeeCardSheetProps {
  /** Клиент API. */
  readonly api: ApiClient;
  /** Сотрудник, карточка которого открыта; `null` — панель закрыта. */
  readonly employeeId: EmployeeId | null;
  /** Открыта ли поверх карточки панель редактирования. */
  readonly editOpen: boolean;
  /** Вызывается, когда панель редактирования надо открыть или закрыть. */
  readonly onEditOpenChange: (open: boolean) => void;
  /** Закрытие карточки. */
  readonly onClose: () => void;
}

/**
 * Панель карточки сотрудника [EmployeeCardSheetProps.employeeId] поверх текущей страницы:
 * аватар, статус и последняя активность, контакты, роли и доступ к филиалам. Сотруднику без
 * доступа можно отправить доступ; сотрудника, кроме владельца и себя, можно заблокировать
 * (с подтверждением) и разблокировать, а его сеансы — завершить; «Редактировать» открывает поверх карточки панель формы сотрудника. От маршрута не зависит:
 * открытием карточки и панели редактирования управляет вызывающий — через search-параметры
 * адреса (раздел «Сотрудники») или локальное состояние (карточка занятия).
 */
export function EmployeeCardSheet({
  api,
  employeeId,
  editOpen,
  onEditOpenChange,
  onClose,
}: EmployeeCardSheetProps) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const detail = useQuery({
    ...apiQuery(api, branchId, "employees/detail", { id: employeeId ?? NO_EMPLOYEE }),
    enabled: employeeId !== null,
  });
  return (
    <EditSheet
      open={employeeId !== null}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
      title={detail.data?.name ?? t("employees.cardTitle")}
    >
      {detail.isPending && (
        <EditSheetBody>
          <Skeleton className="mx-auto size-20 rounded-full" />
          <Skeleton className="h-32 w-full" />
        </EditSheetBody>
      )}
      {detail.isError && (
        <EditSheetBody>
          <FormAlert message={t("directory.loadError")} />
        </EditSheetBody>
      )}
      {detail.data !== undefined && (
        <EmployeeCard
          api={api}
          employee={detail.data}
          editOpen={editOpen}
          onEditOpenChange={onEditOpenChange}
        />
      )}
    </EditSheet>
  );
}

/** Содержимое карточки сотрудника [employee] и вложенная панель его редактирования. */
function EmployeeCard({
  api,
  employee,
  editOpen,
  onEditOpenChange,
}: {
  readonly api: ApiClient;
  readonly employee: EmployeeDetailResponse;
  readonly editOpen: boolean;
  readonly onEditOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const session = useSession(api);
  const branchId = session.currentBranch.id;
  const [sendAccessOpen, setSendAccessOpen] = useState(false);
  const [blockOpen, setBlockOpen] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const canBlock = !employee.isOwner && employee.id !== session.employeeId;
  const blocked = employee.status === "BLOCKED";

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: apiQuery(api, branchId, "employees/detail", { id: employee.id }).queryKey,
      }),
      queryClient.invalidateQueries({
        queryKey: apiQuery(api, branchId, "employees/list").queryKey,
      }),
      queryClient.invalidateQueries({
        queryKey: apiQuery(api, branchId, "employees/sessions", { employeeId: employee.id })
          .queryKey,
      }),
    ]);

  const setBlocked = async (block: boolean) => {
    setFailure(null);
    const result = await api.call(block ? "employees/block" : "employees/unblock", {
      employeeId: employee.id,
    });
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    await refresh();
  };

  return (
    <EditSheetBody>
      <div className="flex flex-col items-center gap-3 text-center">
        <Avatar
          api={api}
          uploadId={employee.avatarId}
          name={employee.name}
          className="size-20 text-2xl"
        />
        <div className="flex flex-wrap items-center justify-center gap-2">
          <StatusBadge status={employee.status} />
          <LastSeen online={employee.online} lastSeenAt={employee.lastSeenAt} withPrefix />
        </div>
        {failure !== null && <FormAlert message={failure} />}
        <div className="flex flex-wrap justify-center gap-2">
          {employee.status === "NO_ACCESS" && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setSendAccessOpen(true);
              }}
            >
              <SendIcon aria-hidden />
              {t("employees.sendAccess")}
            </Button>
          )}
          <Button
            type="button"
            size="sm"
            onClick={() => {
              onEditOpenChange(true);
            }}
          >
            <PencilIcon aria-hidden />
            {t("action.edit")}
          </Button>
          {canBlock && blocked && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                void setBlocked(false);
              }}
            >
              <LockOpenIcon aria-hidden />
              {t("employees.unblock")}
            </Button>
          )}
          {canBlock && !blocked && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                setBlockOpen(true);
              }}
            >
              <BanIcon aria-hidden />
              {t("employees.block")}
            </Button>
          )}
        </div>
      </div>
      <section className="rounded-lg border bg-card p-4">
        <h3 className="mb-3 font-semibold">{t("employees.sectionContactInfo")}</h3>
        {employee.email === null && employee.phoneNo === null ? (
          <p className="text-sm text-muted-foreground">{t("employees.noContactInfo")}</p>
        ) : (
          <dl className="space-y-2 text-sm">
            {employee.email !== null && (
              <div className="flex gap-2">
                <dt className="w-24 shrink-0 text-muted-foreground">{t("employees.email")}</dt>
                <dd className="min-w-0 break-words">{employee.email}</dd>
              </div>
            )}
            {employee.phoneNo !== null && (
              <div className="flex gap-2">
                <dt className="w-24 shrink-0 text-muted-foreground">{t("employees.phone")}</dt>
                <dd className="min-w-0 break-words">{employee.phoneNo}</dd>
              </div>
            )}
          </dl>
        )}
      </section>
      {(employee.isOwner || employee.roles.length > 0) && (
        <section className="rounded-lg border bg-card p-4">
          <h3 className="mb-3 font-semibold">{t("employees.columnRoles")}</h3>
          <div className="flex flex-wrap gap-1.5">
            {employee.isOwner ? (
              <OwnerBadge />
            ) : (
              employee.roles.map((role) => <RoleChip key={role.id} name={role.name} />)
            )}
          </div>
        </section>
      )}
      <BranchAccessSection api={api} employee={employee} />
      {employee.status !== "NO_ACCESS" && (
        <EmployeeSessionsSection api={api} employeeId={employee.id} canTerminate={canBlock} />
      )}
      <ConfirmDialog
        open={blockOpen}
        onOpenChange={setBlockOpen}
        title={t("employees.blockConfirmTitle")}
        description={t("employees.blockConfirmDescription", { name: employee.name })}
        confirmLabel={t("employees.block")}
        onConfirm={() => setBlocked(true)}
      />
      <EmployeeSheet
        api={api}
        open={editOpen}
        onOpenChange={onEditOpenChange}
        employee={employee}
      />
      {sendAccessOpen && (
        <SendAccessDialog
          api={api}
          employeeId={employee.id}
          defaultEmail={employee.email}
          onSuccess={() => {
            setSendAccessOpen(false);
            void refresh();
          }}
          onClose={() => {
            setSendAccessOpen(false);
          }}
        />
      )}
    </EditSheetBody>
  );
}

/**
 * Раздел «Доступ к филиалам» карточки [employee]: «Все филиалы» у владельца и при доступе ко
 * всем, иначе названия выбранных филиалов, без выбора — сообщение об отсутствии доступа.
 */
function BranchAccessSection({
  api,
  employee,
}: {
  readonly api: ApiClient;
  readonly employee: EmployeeDetailResponse;
}) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const all = employee.isOwner || employee.allBranchesAccess;
  const branches = useQuery({
    ...apiQuery(api, branchId, "branches/list"),
    select: (r) => r.branches.filter((branch) => employee.branchIds.includes(branch.id)),
    enabled: !all,
  });
  return (
    <section className="rounded-lg border bg-card p-4">
      <h3 className="mb-3 font-semibold">{t("employees.sectionBranchAccess")}</h3>
      {all ? (
        <p className="text-sm">{t("employees.allBranches")}</p>
      ) : employee.branchIds.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("employees.noBranchAccess")}</p>
      ) : branches.data === undefined ? (
        <Skeleton className="h-6 w-1/2" />
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {branches.data.map((branch) => (
            <RoleChip key={branch.id} name={branch.name} />
          ))}
        </div>
      )}
    </section>
  );
}

/**
 * Раздел «Активные сеансы» сотрудника [employeeId] в том же виде, что в профиле.
 * При [canTerminate] у каждого сеанса есть кнопка завершения; ошибка сервера показывается
 * над списком.
 */
function EmployeeSessionsSection({
  api,
  employeeId,
  canTerminate,
}: {
  readonly api: ApiClient;
  readonly employeeId: EmployeeId;
  readonly canTerminate: boolean;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const query = apiQuery(api, branchId, "employees/sessions", { employeeId });
  const sessions = useQuery(query);
  const [failure, setFailure] = useState<string | null>(null);

  const terminate = async (sessionId: AuthSessionId) => {
    setFailure(null);
    const result = await api.call("employees/sessions/terminate", { employeeId, sessionId });
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    await queryClient.invalidateQueries({ queryKey: query.queryKey });
  };

  return (
    <section className="rounded-lg border bg-card p-4">
      <h3 className="mb-1 font-semibold">{t("sessions.title")}</h3>
      {failure !== null && <FormAlert message={failure} />}
      {sessions.isPending ? (
        <Skeleton className="h-14 w-full" />
      ) : sessions.isError ? (
        <FormAlert message={t("directory.loadError")} />
      ) : sessions.data.items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("sessions.empty")}</p>
      ) : (
        <ul className="divide-y">
          {sessions.data.items.map((session) => (
            <SessionRow
              key={session.id}
              session={session}
              onTerminate={
                canTerminate
                  ? () => {
                      void terminate(session.id);
                    }
                  : undefined
              }
            />
          ))}
        </ul>
      )}
    </section>
  );
}
