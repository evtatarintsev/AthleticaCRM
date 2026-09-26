import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { ClientId } from "@/api/generated/contracts";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { PageHeader } from "@/ui/PageHeader";
import { ClientFormPage } from "./ClientFormPage";

/** Экран редактирования клиента [clientId]; после сохранения ведёт к списку клиентов. */
export function ClientEditPage({
  api,
  clientId,
}: {
  readonly api: ApiClient;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const detailQuery = apiQuery(api, branchId, "clients/detail", { id: clientId });
  const client = useQuery(detailQuery);

  if (client.data === undefined) {
    return (
      <section className="max-w-2xl">
        <PageHeader title={t("clients.edit")} />
        {client.isPending && (
          <div className="space-y-4">
            <Skeleton className="h-24 w-24 rounded-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        )}
        {client.isError && <FormAlert message={t("clients.loadClientError")} />}
      </section>
    );
  }

  return (
    <ClientFormPage
      api={api}
      title={t("clients.edit")}
      submitLabel={t("action.save")}
      initial={client.data}
      onSave={async (input) => {
        const result = await api.call("clients/edit", { id: clientId, ...input });
        if (!result.ok) {
          return apiErrorMessage(t, result.error);
        }
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: detailQuery.queryKey }),
          queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] }),
        ]);
        toast.success(t("clients.savedToast"));
        await navigate({ to: "/clients" });
        return null;
      }}
    />
  );
}
