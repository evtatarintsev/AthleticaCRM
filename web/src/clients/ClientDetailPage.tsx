import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ArchiveIcon, ArchiveRestoreIcon, MessageSquareIcon, PencilIcon } from "lucide-react";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { ClientId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { hasPermission, useSession } from "@/query/session";
import { Avatar } from "@/ui/Avatar";
import { PageHeader } from "@/ui/PageHeader";
import { ClientBalanceSection } from "./ClientBalanceSection";
import { ClientCustomFieldsSection } from "./ClientCustomFieldsSection";
import { ClientDocumentsSection } from "./ClientDocumentsSection";
import { ClientGroupsSection } from "./ClientGroupsSection";
import { ClientInfoSection } from "./ClientInfoSection";
import { ClientNotesSection } from "./ClientNotesSection";
import { ClientSubscriptionsSection } from "./ClientSubscriptionsSection";
import { clientCustomFieldsQuery } from "./clientsQueries";

/** Карточка клиента: сведения, заметки, документы и дополнительные поля. */
export function ClientDetailPage({
  api,
  clientId,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const session = useSession(api);
  const branchId = session.currentBranch.id;
  const detailQuery = apiQuery(api, branchId, "clients/detail", { id: clientId });
  const client = useQuery(detailQuery);
  const customFieldDefs = useQuery(clientCustomFieldsQuery(api, branchId));

  const refreshDetail = async () => {
    await queryClient.invalidateQueries({ queryKey: detailQuery.queryKey });
  };

  const toggleArchived = async (archived: boolean) => {
    const result = await api.call(archived ? "clients/archive" : "clients/restore", {
      clientIds: [clientId],
    });
    if (!result.ok) {
      toast.error(apiErrorMessage(t, result.error));
      return;
    }
    await Promise.all([
      refreshDetail(),
      queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] }),
    ]);
    toast.success(t(archived ? "clients.archivedToast" : "clients.restoredToast"));
  };

  if (client.data === undefined) {
    return (
      <section className="max-w-3xl">
        <PageHeader title={t("clients.title")} />
        {client.isPending && (
          <div className="space-y-4">
            <Skeleton className="h-16 w-16 rounded-full" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        )}
        {client.isError && <FormAlert message={t("clients.loadClientError")} />}
      </section>
    );
  }

  const data = client.data;
  const archived = data.state === "ARCHIVED";

  return (
    <section className="max-w-3xl space-y-4 pb-16">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <Avatar
              api={api}
              uploadId={data.avatarId}
              name={data.name}
              className="size-10 text-sm"
            />
            {data.name}
          </span>
        }
        actions={
          <div className="flex gap-2">
            <Button variant="outline" size="sm" asChild>
              <Link to="/clients/$clientId/messages" params={{ clientId }}>
                <MessageSquareIcon aria-hidden />
                {t("messaging.open")}
              </Link>
            </Button>
            {!archived && (
              <Button variant="outline" size="sm" asChild>
                <Link to="/clients/$clientId/edit" params={{ clientId }}>
                  <PencilIcon aria-hidden />
                  {t("action.edit")}
                </Link>
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                void toggleArchived(!archived);
              }}
            >
              {archived ? <ArchiveRestoreIcon aria-hidden /> : <ArchiveIcon aria-hidden />}
              {t(archived ? "action.restore" : "action.archive")}
            </Button>
          </div>
        }
      />

      {archived && (
        <div className="rounded-lg border bg-secondary px-4 py-3 text-sm text-secondary-foreground">
          {t("clients.detail.archivedBanner")}
        </div>
      )}

      <ClientInfoSection client={data} />
      <ClientGroupsSection
        api={api}
        branchId={branchId}
        clientId={clientId}
        groups={data.groups}
        onChanged={() => {
          void refreshDetail();
        }}
      />
      {hasPermission(session, "CAN_VIEW_CLIENT_BALANCE") && (
        <ClientBalanceSection
          api={api}
          branchId={branchId}
          clientId={clientId}
          balance={data.balance}
          onChanged={(updated) => {
            queryClient.setQueryData(detailQuery.queryKey, updated);
          }}
        />
      )}
      <ClientSubscriptionsSection api={api} branchId={branchId} clientId={clientId} />
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" asChild>
          <Link to="/clients/$clientId/visits" params={{ clientId }}>
            {t("clients.detail.visitHistory")}
          </Link>
        </Button>
        <Button variant="outline" size="sm" asChild>
          <Link to="/clients/$clientId/payments" params={{ clientId }}>
            {t("clients.detail.paymentHistory")}
          </Link>
        </Button>
      </div>
      {customFieldDefs.data !== undefined && customFieldDefs.data.length > 0 && (
        <ClientCustomFieldsSection definitions={customFieldDefs.data} values={data.customFields} />
      )}
      <ClientNotesSection
        api={api}
        branchId={branchId}
        clientId={clientId}
        currentEmployeeId={session.employeeId}
      />
      <ClientDocumentsSection
        api={api}
        clientId={clientId}
        docs={data.docs}
        onChanged={refreshDetail}
      />
    </section>
  );
}
