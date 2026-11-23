import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  TelegramGatewayCodeOutcome,
  TelegramGatewayIntegrationSchema,
  TelegramGatewaySettingsResponse,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Switch } from "@/components/ui/switch";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n, type PlainMessageKey } from "@/i18n/context";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { apiQuery } from "@/query/queries";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";

/** Панель настроек «Telegram Gateway», открытая при [open]. */
export function TelegramGatewaySheet({
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
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("telegramGateway.title")}>
      <TelegramGatewayPanel api={api} />
    </EditSheet>
  );
}

/**
 * Загружает настройки Gateway организации и показывает токен маской, включённость, остаток
 * и тестовую отправку. Ответ каждого изменения заменяет настройки в кэше запроса.
 */
function TelegramGatewayPanel({ api }: { readonly api: ApiClient }) {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const branchId = useSession(api).currentBranch.id;
  const query = apiQuery(api, branchId, "telegram-gateway/settings");
  const settings = useQuery(query);
  const [failure, setFailure] = useState<string | null>(null);

  if (settings.data === undefined) {
    return (
      <EditSheetBody>
        {settings.isError && <FormAlert message={t("telegramGateway.loadError")} />}
        {settings.isPending && <Skeleton className="h-24 w-full" />}
      </EditSheetBody>
    );
  }
  const integration = settings.data.integration;
  const applied = (value: TelegramGatewaySettingsResponse) => {
    setFailure(null);
    queryClient.setQueryData(query.queryKey, value);
  };
  return (
    <EditSheetBody>
      <p className="text-sm text-muted-foreground">{t("telegramGateway.hint")}</p>
      {failure !== null && <FormAlert message={failure} />}
      <TokenForm
        integration={integration}
        onSave={async (token) => {
          const result = await api.call("telegram-gateway/save-token", { token });
          if (!result.ok) {
            setFailure(apiErrorMessage(t, result.error));
            return false;
          }
          applied(result.value);
          return true;
        }}
      />
      {integration !== null && (
        <>
          <div className="flex items-center gap-3">
            <Switch
              id="telegram-gateway-enabled"
              checked={integration.enabled}
              onCheckedChange={(enabled) => {
                void api.call("telegram-gateway/set-enabled", { enabled }).then((result) => {
                  if (result.ok) {
                    applied(result.value);
                  } else {
                    setFailure(apiErrorMessage(t, result.error));
                  }
                });
              }}
            />
            <Label htmlFor="telegram-gateway-enabled">{t("telegramGateway.enabled")}</Label>
          </div>
          <Balance integration={integration} />
          <TestCodeForm
            api={api}
            onSent={() => {
              void queryClient.invalidateQueries(query);
            }}
          />
        </>
      )}
    </EditSheetBody>
  );
}

/**
 * Токен: без интеграции — поле и «Подключить», с интеграцией — маска и «Изменить».
 * [onSave] возвращает, сохранён ли токен; при отказе поле остаётся открытым.
 */
function TokenForm({
  integration,
  onSave,
}: {
  readonly integration: TelegramGatewayIntegrationSchema | null;
  readonly onSave: (token: string) => Promise<boolean>;
}) {
  const { t } = useI18n();
  const [editing, setEditing] = useState(integration === null);
  const [token, setToken] = useState("");
  const [saving, setSaving] = useState(false);

  if (integration !== null && !editing) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-lg border bg-card p-3">
        <div>
          <p className="text-sm text-muted-foreground">{t("telegramGateway.token")}</p>
          <p className="font-mono">{integration.tokenMask}</p>
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setEditing(true);
          }}
        >
          {t("telegramGateway.changeToken")}
        </Button>
      </div>
    );
  }
  const submit = async () => {
    if (saving || token.trim() === "") {
      return;
    }
    setSaving(true);
    try {
      if (await onSave(token)) {
        setToken("");
        setEditing(false);
      }
    } finally {
      setSaving(false);
    }
  };
  return (
    <form
      className="space-y-2"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <Label htmlFor="telegram-gateway-token">{t("telegramGateway.token")}</Label>
      <Input
        id="telegram-gateway-token"
        type="password"
        autoComplete="off"
        value={token}
        onChange={(event) => {
          setToken(event.target.value);
        }}
      />
      <div className="flex gap-2">
        <Button type="submit" disabled={saving || token.trim() === ""}>
          {integration === null ? t("telegramGateway.connect") : t("action.save")}
        </Button>
        {integration !== null && (
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setToken("");
              setEditing(false);
            }}
          >
            {t("action.cancel")}
          </Button>
        )}
      </div>
    </form>
  );
}

/** Остаток баланса Gateway со временем ответа; пока ответа со списанием не было — ничего. */
function Balance({ integration }: { readonly integration: TelegramGatewayIntegrationSchema }) {
  const { t, format } = useI18n();
  if (integration.balance === null) {
    return null;
  }
  return (
    <p>
      {t("telegramGateway.balance", {
        amount: format.money(integration.balance.amount),
        at: format.dateTime(integration.balance.at),
      })}
    </p>
  );
}

/** Подпись исхода тестовой отправки. */
function outcomeKey(outcome: TelegramGatewayCodeOutcome): PlainMessageKey {
  switch (outcome) {
    case "SENT":
      return "telegramGateway.outcome.sent";
    case "UNDELIVERABLE":
      return "telegramGateway.outcome.undeliverable";
    case "NOT_CONFIGURED":
      return "telegramGateway.outcome.notConfigured";
    case "FAILED":
      return "telegramGateway.outcome.failed";
  }
}

/** Отправка тестового кода на номер; исход показывается под формой, после ответа вызывается [onSent]. */
function TestCodeForm({ api, onSent }: { readonly api: ApiClient; readonly onSent: () => void }) {
  const { t } = useI18n();
  const [phone, setPhone] = useState("");
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const submit = async () => {
    if (sending || phone.trim() === "") {
      return;
    }
    setSending(true);
    try {
      const response = await api.call("telegram-gateway/test-code", { phone });
      if (response.ok) {
        const message = t(outcomeKey(response.value.outcome));
        setResult(
          response.value.reason === null ? message : `${message}: ${response.value.reason}`,
        );
        onSent();
      } else {
        setResult(apiErrorMessage(t, response.error));
      }
    } finally {
      setSending(false);
    }
  };
  return (
    <form
      className="space-y-2 border-t pt-4"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <Label htmlFor="telegram-gateway-phone">{t("telegramGateway.testPhone")}</Label>
      <Input
        id="telegram-gateway-phone"
        type="tel"
        value={phone}
        onChange={(event) => {
          setPhone(event.target.value);
        }}
      />
      <Button type="submit" variant="outline" disabled={sending || phone.trim() === ""}>
        {t("telegramGateway.sendTestCode")}
      </Button>
      {result !== null && <p role="status">{result}</p>}
    </form>
  );
}
