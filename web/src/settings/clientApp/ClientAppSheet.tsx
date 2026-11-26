import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { GroupId } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";

/** Панель настроек «Клиентский кабинет», открытая при [open]. */
export function ClientAppSheet({
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
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("clientApp.title")}>
      <ClientAppPanel api={api} />
    </EditSheet>
  );
}

/**
 * Переключатель кабинета, предупреждение об отсутствии способа доставки кодов и массовое
 * приглашение. Ошибка `CLIENT_APP_NO_CODE_TRANSPORT` показывается со ссылками на настройки
 * Telegram Gateway и каналов.
 */
function ClientAppPanel({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const query = apiQuery(api, branchId, "client-app-admin/settings");
  const settings = useQuery(query);
  const [failure, setFailure] = useState<{ code: string; message: string } | null>(null);

  if (settings.data === undefined) {
    return (
      <EditSheetBody>
        {settings.isError && <FormAlert message={t("clientApp.loadError")} />}
        {settings.isPending && <Skeleton className="h-24 w-full" />}
      </EditSheetBody>
    );
  }
  const { enabled, codeTransportAvailable } = settings.data;
  const transportLinks = (
    <p className="text-sm">
      <Link to="/settings" search={{ panel: "telegram-gateway" }} className="underline">
        {t("settings.itemTelegramGateway")}
      </Link>
      {" · "}
      <Link to="/settings/channels" className="underline">
        {t("settings.itemChannels")}
      </Link>
    </p>
  );
  return (
    <EditSheetBody>
      <p className="text-sm text-muted-foreground">{t("clientApp.hint")}</p>
      {failure !== null && (
        <div className="space-y-2">
          <FormAlert message={failure.message} />
          {failure.code === "CLIENT_APP_NO_CODE_TRANSPORT" && transportLinks}
        </div>
      )}
      <div className="flex items-center gap-3">
        <Switch
          id="client-app-enabled"
          checked={enabled}
          onCheckedChange={(value) => {
            void api.call("client-app-admin/set-enabled", { enabled: value }).then((result) => {
              if (result.ok) {
                setFailure(null);
                queryClient.setQueryData(query.queryKey, result.value);
              } else {
                setFailure({
                  code: result.error.kind === "business" ? result.error.code : "",
                  message: apiErrorMessage(t, result.error),
                });
              }
            });
          }}
        />
        <Label htmlFor="client-app-enabled">{t("clientApp.enabled")}</Label>
      </div>
      {enabled && !codeTransportAvailable && (
        <div className="space-y-2">
          <FormAlert message={t("clientApp.noCodeTransport")} />
          {transportLinks}
        </div>
      )}
      {enabled && <BulkInvite api={api} />}
    </EditSheetBody>
  );
}

/** Массовое приглашение всем активным клиентам или участникам выбранных групп со счётчиками перед отправкой. */
function BulkInvite({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const branchId = useSession(api).currentBranch.id;
  const [byGroups, setByGroups] = useState(false);
  const [groupIds, setGroupIds] = useState<readonly GroupId[]>([]);
  const [outcome, setOutcome] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const request = { groupIds: byGroups ? groupIds : null };
  const groups = useQuery({ ...apiQuery(api, branchId, "groups/list", {}), enabled: byGroups });
  const preview = useQuery(apiQuery(api, branchId, "client-app-admin/invite-preview", request));

  return (
    <section className="space-y-3 rounded-lg border bg-card p-3">
      <h3 className="font-medium">{t("clientApp.bulkTitle")}</h3>
      <div className="flex items-center gap-2">
        <Checkbox
          id="client-app-by-groups"
          checked={byGroups}
          onCheckedChange={(value) => {
            setByGroups(value === true);
          }}
        />
        <Label htmlFor="client-app-by-groups">{t("clientApp.byGroups")}</Label>
      </div>
      {byGroups && (
        <ul className="max-h-48 space-y-1 overflow-y-auto">
          {(groups.data?.groups ?? []).map((group) => (
            <li key={group.id} className="flex items-center gap-2">
              <Checkbox
                id={`client-app-group-${group.id}`}
                checked={groupIds.includes(group.id)}
                onCheckedChange={(value) => {
                  setGroupIds(
                    value === true
                      ? [...groupIds, group.id]
                      : groupIds.filter((id) => id !== group.id),
                  );
                }}
              />
              <Label htmlFor={`client-app-group-${group.id}`}>{group.name}</Label>
            </li>
          ))}
        </ul>
      )}
      {preview.data !== undefined && (
        <p className="text-sm">
          {t("clientApp.ready", { count: preview.data.ready })} ·{" "}
          {t("clientApp.withoutPhone", { count: preview.data.withoutPhone })}
        </p>
      )}
      {outcome !== null && <p className="text-sm">{outcome}</p>}
      <Button
        type="button"
        disabled={sending || preview.data === undefined || preview.data.ready === 0}
        onClick={() => {
          setSending(true);
          void api.call("client-app-admin/bulk-invite", request).then(async (result) => {
            setSending(false);
            if (result.ok) {
              setOutcome(
                result.value.notified
                  ? t("clientApp.bulkDone", { count: result.value.invited })
                  : t("clientApp.bulkDoneNotNotified", { count: result.value.invited }),
              );
              await preview.refetch();
            } else {
              setOutcome(apiErrorMessage(t, result.error));
            }
          });
        }}
      >
        {t("clientApp.bulkInvite")}
      </Button>
    </section>
  );
}
