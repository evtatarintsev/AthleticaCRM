import { useQuery, useQueryClient } from "@tanstack/react-query";
import { HandIcon } from "lucide-react";
import { useState } from "react";
import type { ApiClient, ApiResult } from "@/api/client";
import type { AuthSessionId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { SessionRow } from "./SessionRow";

/** Панель активных сеансов текущего пользователя, открытая при [open]. */
export function SessionsSheet({
  api,
  open,
  onOpenChange,
}: {
  api: ApiClient;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("sessions.title")}>
      <SessionsList api={api} />
    </EditSheet>
  );
}

/**
 * Сеансы пользователя: текущий в разделе «Это устройство» с действием «Завершить все другие
 * сеансы» (с подтверждением), остальные — ниже, каждый с кнопкой завершения без подтверждения.
 * Ошибка сервера показывается над списком, список при этом не меняется.
 */
function SessionsList({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const query = apiQuery(api, branchId, "auth/sessions");
  const sessions = useQuery(query);
  const [failure, setFailure] = useState<string | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const run = async (pending: Promise<ApiResult<unknown>>) => {
    setFailure(null);
    const result = await pending;
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    await queryClient.invalidateQueries({ queryKey: query.queryKey });
  };
  const terminate = (sessionId: AuthSessionId) =>
    run(api.call("auth/sessions/terminate", { sessionId }));
  const terminateOthers = () => run(api.call("auth/sessions/terminate-others"));

  if (sessions.isPending) {
    return (
      <EditSheetBody>
        <Skeleton className="h-14 w-full" />
        <Skeleton className="h-14 w-full" />
      </EditSheetBody>
    );
  }
  if (sessions.isError) {
    return (
      <EditSheetBody>
        <FormAlert message={t("directory.loadError")} />
      </EditSheetBody>
    );
  }
  const current = sessions.data.items.filter((session) => session.current);
  const others = sessions.data.items.filter((session) => !session.current);
  return (
    <EditSheetBody>
      {failure !== null && <FormAlert message={failure} />}
      <section className="rounded-lg border bg-card p-4">
        <h3 className="font-semibold">{t("sessions.thisDevice")}</h3>
        <ul>
          {current.map((session) => (
            <SessionRow key={session.id} session={session} />
          ))}
        </ul>
        {others.length > 0 && (
          <>
            <Button
              type="button"
              variant="ghost"
              className="mt-2 w-full justify-start text-destructive hover:text-destructive"
              onClick={() => {
                setConfirmOpen(true);
              }}
            >
              <HandIcon aria-hidden />
              {t("sessions.terminateOthers")}
            </Button>
            <p className="mt-1 text-xs text-muted-foreground">
              {t("sessions.terminateOthersHint")}
            </p>
          </>
        )}
      </section>
      {others.length > 0 && (
        <section className="rounded-lg border bg-card p-4">
          <h3 className="font-semibold">{t("sessions.otherSessions")}</h3>
          <ul className="divide-y">
            {others.map((session) => (
              <SessionRow
                key={session.id}
                session={session}
                onTerminate={() => {
                  void terminate(session.id);
                }}
              />
            ))}
          </ul>
        </section>
      )}
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t("sessions.terminateOthersConfirmTitle")}
        description={t("sessions.terminateOthersHint")}
        confirmLabel={t("sessions.terminateOthersConfirm")}
        onConfirm={terminateOthers}
      />
    </EditSheetBody>
  );
}
