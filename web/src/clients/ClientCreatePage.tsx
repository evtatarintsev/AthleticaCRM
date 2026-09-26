import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import { ClientIdSchema } from "@/api/generated/contracts";
import { uuidv7 } from "@/lib/uuid";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useSession } from "@/query/session";
import { ClientFormPage } from "./ClientFormPage";

/** Экран создания нового клиента; после сохранения ведёт к списку клиентов. */
export function ClientCreatePage({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;

  return (
    <ClientFormPage
      api={api}
      title={t("clients.create")}
      submitLabel={t("action.create")}
      initial={null}
      onSave={async (input) => {
        const result = await api.call("clients/create", {
          id: ClientIdSchema.parse(uuidv7()),
          ...input,
        });
        if (!result.ok) {
          return apiErrorMessage(t, result.error);
        }
        await queryClient.invalidateQueries({ queryKey: ["api", branchId, "clients/list"] });
        toast.success(t("clients.createdToast"));
        await navigate({ to: "/clients" });
        return null;
      }}
    />
  );
}
