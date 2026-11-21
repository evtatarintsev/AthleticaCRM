import { useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { EmailConfirmationStatus } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { sessionQuery } from "@/query/queries";
import { AuthLayout } from "@/ui/AuthLayout";

export function ConfirmEmailPage({ api, token }: { api: ApiClient; token: string }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<EmailConfirmationStatus | null>(null);
  const [pending, setPending] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const confirm = async () => {
    setPending(true);
    setFailure(null);
    const result = await api.call("auth/confirm-email", { token });
    setPending(false);
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    setStatus(result.value.status);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: sessionQuery(api).queryKey }),
      queryClient.invalidateQueries({
        predicate: (query) => query.queryKey[2] === "notifications",
      }),
    ]);
  };

  return (
    <AuthLayout
      title={t("emailVerification.title")}
      subtitle={t("emailVerification.instructions")}
      footer={<Link to="/">{t("emailVerification.openCrm")}</Link>}
    >
      <div className="space-y-4">
        {status !== null ? (
          <p role="status">
            {t(
              status === "confirmed"
                ? "emailVerification.confirmed"
                : "emailVerification.alreadyConfirmed",
            )}
          </p>
        ) : token === "" ? (
          <FormAlert message={t("emailVerification.invalidLink")} />
        ) : (
          <>
            {failure !== null && <FormAlert message={failure} />}
            <Button
              disabled={pending}
              onClick={() => {
                void confirm();
              }}
            >
              {t("emailVerification.confirm")}
            </Button>
          </>
        )}
      </div>
    </AuthLayout>
  );
}
