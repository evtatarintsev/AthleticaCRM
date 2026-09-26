import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArrowLeftIcon, PencilIcon, SendIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { EmployeeId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { Avatar } from "@/ui/Avatar";
import { PageHeader } from "@/ui/PageHeader";
import { OwnerBadge, RoleChip, StatusBadge } from "./EmployeeBadges";
import { SendAccessDialog } from "./SendAccessDialog";

/**
 * Карточка сотрудника: аватар, имя, статус активности, контактная информация и роли.
 * Для неактивного сотрудника доступна отправка доступа; для всех — переход к редактированию.
 */
export function EmployeeDetailPage({
  api,
  employeeId,
}: {
  api: ApiClient;
  employeeId: EmployeeId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const detailQuery = apiQuery(api, branchId, "employees/detail", { id: employeeId });
  const detail = useQuery(detailQuery);
  const [sendAccessOpen, setSendAccessOpen] = useState(false);

  return (
    <section className="max-w-2xl pb-10">
      <Button variant="ghost" size="sm" asChild className="-ml-2 mb-4">
        <Link to="/employees">
          <ArrowLeftIcon aria-hidden />
          {t("action.back")}
        </Link>
      </Button>
      {detail.isPending && (
        <div className="space-y-4">
          <Skeleton className="mx-auto size-20 rounded-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      )}
      {detail.isError && <FormAlert message={t("directory.loadError")} />}
      {detail.data !== undefined && (
        <>
          <PageHeader
            title={detail.data.name}
            actions={
              <div className="flex flex-wrap gap-2">
                {!detail.data.isActive && (
                  <Button
                    variant="outline"
                    onClick={() => {
                      setSendAccessOpen(true);
                    }}
                  >
                    <SendIcon aria-hidden />
                    {t("employees.sendAccess")}
                  </Button>
                )}
                <Button asChild>
                  <Link to="/employees/$employeeId/edit" params={{ employeeId }}>
                    <PencilIcon aria-hidden />
                    {t("action.edit")}
                  </Link>
                </Button>
              </div>
            }
          />
          <div className="mb-6 flex flex-col items-center gap-3 text-center">
            <Avatar
              api={api}
              uploadId={detail.data.avatarId}
              name={detail.data.name}
              className="size-20 text-2xl"
            />
            <div className="flex flex-wrap items-center justify-center gap-2">
              {detail.data.isOwner && <OwnerBadge />}
              <StatusBadge active={detail.data.isActive} />
            </div>
          </div>
          <div className="space-y-4">
            <section className="rounded-lg border bg-card p-4">
              <h2 className="mb-3 font-semibold">{t("employees.sectionContactInfo")}</h2>
              {detail.data.email === null && detail.data.phoneNo === null ? (
                <p className="text-sm text-muted-foreground">{t("employees.noContactInfo")}</p>
              ) : (
                <dl className="space-y-2 text-sm">
                  {detail.data.email !== null && (
                    <div className="flex gap-2">
                      <dt className="w-24 shrink-0 text-muted-foreground">
                        {t("employees.email")}
                      </dt>
                      <dd className="min-w-0 break-words">{detail.data.email}</dd>
                    </div>
                  )}
                  {detail.data.phoneNo !== null && (
                    <div className="flex gap-2">
                      <dt className="w-24 shrink-0 text-muted-foreground">
                        {t("employees.phone")}
                      </dt>
                      <dd className="min-w-0 break-words">{detail.data.phoneNo}</dd>
                    </div>
                  )}
                </dl>
              )}
            </section>
            {detail.data.roles.length > 0 && (
              <section className="rounded-lg border bg-card p-4">
                <h2 className="mb-3 font-semibold">{t("employees.columnRoles")}</h2>
                <div className="flex flex-wrap gap-1.5">
                  {detail.data.roles.map((role) => (
                    <RoleChip key={role.id} name={role.name} />
                  ))}
                </div>
              </section>
            )}
          </div>
          {sendAccessOpen && (
            <SendAccessDialog
              api={api}
              employeeId={employeeId}
              defaultEmail={detail.data.email}
              onSuccess={() => {
                setSendAccessOpen(false);
                void queryClient.invalidateQueries({ queryKey: detailQuery.queryKey });
              }}
              onClose={() => {
                setSendAccessOpen(false);
              }}
            />
          )}
        </>
      )}
    </section>
  );
}
