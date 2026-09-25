import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type { ApiClient } from "@/api/client";
import type { BranchDetailResponse } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { apiErrorOf, unwrap } from "@/query/apiFailure";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { switchBranch } from "./switchBranch";

/** Смена филиала через [api] с уведомлением о результате; общая для меню аккаунта и страницы. */
export function useSwitchBranch(api: ApiClient) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (branch: BranchDetailResponse) => {
      unwrap(await switchBranch(api, queryClient, branch.id));
    },
    onSuccess: (_, branch) => {
      toast.success(t("branch.switched", { name: branch.name }));
    },
    onError: (error) => {
      toast.error(apiErrorMessage(t, apiErrorOf(error)));
    },
  });
}
