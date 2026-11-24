import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import type { ApiClient } from "@/api/client";
import { Button } from "@/components/ui/button";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { sessionQuery } from "@/query/queries";

export function EmailVerificationBanner({ api, email }: { api: ApiClient; email: string }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [queued, setQueued] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    const refresh = () => {
      void queryClient.invalidateQueries({ queryKey: sessionQuery(api).queryKey });
    };
    window.addEventListener("focus", refresh);
    return () => {
      window.removeEventListener("focus", refresh);
    };
  }, [api, queryClient]);

  useEffect(() => {
    if (seconds === 0) {
      return;
    }
    const timer = window.setTimeout(() => {
      setSeconds(seconds - 1);
    }, 1000);
    return () => {
      window.clearTimeout(timer);
    };
  }, [seconds]);

  const resend = async () => {
    setPending(true);
    setQueued(false);
    setFailure(null);
    const result = await api.call("auth/me/resend-email-confirmation");
    setPending(false);
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    setSeconds(result.value.retryAfterSeconds);
    if (result.value.status === "already_confirmed") {
      await queryClient.invalidateQueries({ queryKey: sessionQuery(api).queryKey });
    } else {
      setQueued(result.value.status === "queued");
    }
  };

  return (
    <section
      aria-label={t("emailVerification.reminderTitle")}
      className="border-b bg-muted/50 px-4 py-3 text-sm lg:px-8"
    >
      <p className="break-words">{t("emailVerification.reminder", { email })}</p>
      <div className="mt-2 flex flex-wrap items-center gap-3">
        <Button
          variant="outline"
          size="sm"
          disabled={pending || seconds > 0}
          onClick={() => {
            void resend();
          }}
        >
          {t("emailVerification.resend")}
        </Button>
        <p role="status" aria-live="polite">
          {queued && t("emailVerification.queued")}
          {seconds > 0 && ` ${t("emailVerification.wait", { seconds })}`}
        </p>
      </div>
      {failure !== null && (
        <div className="mt-2">
          <FormAlert message={failure} />
        </div>
      )}
    </section>
  );
}
