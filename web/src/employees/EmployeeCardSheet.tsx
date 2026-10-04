import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PencilIcon, SendIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import {
  EmployeeIdSchema,
  type EmployeeDetailResponse,
  type EmployeeId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { Avatar } from "@/ui/Avatar";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { OwnerBadge, RoleChip, StatusBadge } from "./EmployeeBadges";
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
 * Панель карточки сотрудника [EmployeeCardSheetProps.employeeId] поверх списка: аватар,
 * статус, контакты и роли. Неактивному сотруднику можно отправить доступ; «Редактировать»
 * открывает поверх карточки панель формы сотрудника.
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
  const branchId = useSession(api).currentBranch.id;
  const [sendAccessOpen, setSendAccessOpen] = useState(false);

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
          {employee.isOwner && <OwnerBadge />}
          <StatusBadge active={employee.isActive} />
        </div>
        <div className="flex flex-wrap justify-center gap-2">
          {!employee.isActive && (
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
      {employee.roles.length > 0 && (
        <section className="rounded-lg border bg-card p-4">
          <h3 className="mb-3 font-semibold">{t("employees.columnRoles")}</h3>
          <div className="flex flex-wrap gap-1.5">
            {employee.roles.map((role) => (
              <RoleChip key={role.id} name={role.name} />
            ))}
          </div>
        </section>
      )}
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
            void Promise.all([
              queryClient.invalidateQueries({
                queryKey: apiQuery(api, branchId, "employees/detail", { id: employee.id }).queryKey,
              }),
              queryClient.invalidateQueries({
                queryKey: apiQuery(api, branchId, "employees/list").queryKey,
              }),
            ]);
          }}
          onClose={() => {
            setSendAccessOpen(false);
          }}
        />
      )}
    </EditSheetBody>
  );
}
