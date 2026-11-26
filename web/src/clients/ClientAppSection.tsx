import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient, ApiResult } from "@/api/client";
import type {
  BranchId,
  ClientAppAccessStatus,
  ClientAppPhoneAccessSchema,
  ClientId,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { useNow } from "@/lib/useNow";
import { apiQuery } from "@/query/queries";
import {
  BlockRow,
  BlockStatus,
  BlockSub,
  ClientCardBlock,
  Pill,
  type PillTone,
} from "./card/ClientCardBlock";

/** Тон плашки статуса доступа. */
const TONE: Readonly<Record<ClientAppAccessStatus, PillTone>> = {
  NOT_INVITED: "gray",
  INVITED: "blue",
  CONNECTED: "green",
  NOT_ME: "red",
  DISABLED: "amber",
};

/** Статусы, из которых «Пригласить» переводит номер в «приглашён». */
const INVITABLE: readonly ClientAppAccessStatus[] = ["NOT_INVITED", "NOT_ME", "DISABLED"];

/**
 * Блок «Клиентский кабинет» карточки клиента [clientId]: статус доступа по каждому телефону и действия
 * «Пригласить», «Напомнить», «Отключить». Пока кабинет выключен в организации, действий нет.
 */
export function ClientAppSection({
  api,
  branchId,
  clientId,
}: {
  readonly api: ApiClient;
  readonly branchId: BranchId;
  readonly clientId: ClientId;
}) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const query = apiQuery(api, branchId, "client-app-admin/client", { clientId });
  const access = useQuery(query);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const act = async (
    action: () => Promise<ApiResult<{ readonly notified: boolean } | undefined>>,
  ) => {
    setBusy(true);
    const result = await action();
    setBusy(false);
    if (result.ok) {
      setMessage(result.value?.notified === false ? t("clientApp.notNotified") : null);
    } else {
      setMessage(apiErrorMessage(t, result.error));
    }
    await queryClient.invalidateQueries(query);
  };

  const data = access.data;
  const canInvite =
    data !== undefined && data.enabled && data.phones.some((p) => INVITABLE.includes(p.status));
  return (
    <ClientCardBlock
      title={t("clientApp.title")}
      count={null}
      actions={
        canInvite && (
          <Button
            size="sm"
            variant="link"
            disabled={busy}
            onClick={() => {
              void act(() => api.call("client-app-admin/invite", { clientId }));
            }}
          >
            {t("clientApp.invite")}
          </Button>
        )
      }
    >
      <BlockStatus
        isPending={access.isPending}
        isError={access.isError}
        isEmpty={data?.phones.length === 0}
        empty={t("clientApp.noPhones")}
      />
      {data !== undefined && !data.enabled && (
        <p className="px-4 pt-3 text-sm text-muted-foreground">{t("clientApp.disabledHint")}</p>
      )}
      {message !== null && <p className="px-4 pt-3 text-sm">{message}</p>}
      {data !== undefined && data.phones.length > 0 && (
        <ul className="py-1">
          {data.phones.map((phone) => (
            <BlockRow
              key={phone.phone}
              aside={
                data.enabled && (
                  <PhoneActions
                    phone={phone}
                    busy={busy}
                    onRemind={() => {
                      void act(() =>
                        api.call("client-app-admin/remind", { clientId, phone: phone.phone }),
                      );
                    }}
                    onDisable={() => {
                      void act(() =>
                        api.call("client-app-admin/disable", { clientId, phone: phone.phone }),
                      );
                    }}
                  />
                )
              }
            >
              <div className="flex flex-wrap items-center gap-2">
                <span className="tabular-nums">+{phone.phone}</span>
                <Pill tone={TONE[phone.status]}>{t(`clientApp.status.${phone.status}`)}</Pill>
              </div>
              <PhoneDetails phone={phone} />
            </BlockRow>
          ))}
        </ul>
      )}
    </ClientCardBlock>
  );
}

/** Детали статуса номера [phone]: дата и канал приглашения, последняя активность или дата события. */
function PhoneDetails({ phone }: { readonly phone: ClientAppPhoneAccessSchema }) {
  const { t, format } = useI18n();
  switch (phone.status) {
    case "NOT_INVITED":
      return null;
    case "INVITED":
      return phone.invitedAt === null ? null : (
        <BlockSub>
          {phone.inviteChannel === null
            ? t("clientApp.invitedNotNotified", { at: format.dateTime(phone.invitedAt) })
            : t("clientApp.invitedVia", {
                at: format.dateTime(phone.invitedAt),
                channel: phone.inviteChannel,
              })}
        </BlockSub>
      );
    case "CONNECTED":
      return (
        <BlockSub>
          {phone.lastSeenAt === null
            ? t("clientApp.neverSeen")
            : t("clientApp.lastSeen", { at: format.dateTime(phone.lastSeenAt) })}
        </BlockSub>
      );
    case "NOT_ME":
    case "DISABLED":
      return phone.statusChangedAt === null ? null : (
        <BlockSub>{format.dateTime(phone.statusChangedAt)}</BlockSub>
      );
  }
}

/** Действия с номером [phone]: «Напомнить» для приглашённого, «Отключить» для приглашённого и подключённого. */
function PhoneActions({
  phone,
  busy,
  onRemind,
  onDisable,
}: {
  readonly phone: ClientAppPhoneAccessSchema;
  readonly busy: boolean;
  readonly onRemind: () => void;
  readonly onDisable: () => void;
}) {
  const { t, format } = useI18n();
  const now = useNow();
  const remindAt = phone.remindAvailableAt;
  const tooSoon = remindAt !== null && Date.parse(remindAt) > now.getTime();
  return (
    <div className="flex flex-col items-end gap-1">
      {phone.status === "INVITED" && (
        <Button size="sm" variant="link" disabled={busy || tooSoon} onClick={onRemind}>
          {t("clientApp.remind")}
        </Button>
      )}
      {tooSoon && <BlockSub>{t("clientApp.remindAt", { at: format.dateTime(remindAt) })}</BlockSub>}
      {(phone.status === "INVITED" || phone.status === "CONNECTED") && (
        <Button size="sm" variant="link" disabled={busy} onClick={onDisable}>
          {t("clientApp.disable")}
        </Button>
      )}
    </div>
  );
}
