import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type {
  AssistantSettingsRequest,
  AssistantSettingsResponse,
} from "@/api/generated/contracts";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { FormAlert } from "@/forms/FormAlert";
import { useI18n } from "@/i18n/context";
import { apiErrorOf, unwrap } from "@/query/apiFailure";
import { useSession } from "@/query/session";
import { EditSheet, EditSheetBody, EditSheetForm } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import { assistantErrorMessage } from "./assistantQueries";

/** Настройки организации: сервер выдаёт их только владельцу. */
export function AssistantSettingsSheet({
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
    <EditSheet open={open} onOpenChange={onOpenChange} title={t("assistant.title")}>
      <SettingsPanel api={api} />
    </EditSheet>
  );
}

/** Ключ и модель отсутствуют в пользовательском API и интерфейсе. */
function SettingsPanel({ api }: { api: ApiClient }) {
  const { t } = useI18n();
  const employeeId = useSession(api).employeeId;
  const cache = useQueryClient();
  const key = ["assistant", employeeId, "settings"] as const;
  const query = useQuery({
    queryKey: key,
    queryFn: async () => unwrap(await api.call("assistant/settings")),
  });
  if (query.data === undefined) {
    return (
      <EditSheetBody>
        {query.isError ? (
          <FormAlert message={assistantErrorMessage(t, apiErrorOf(query.error))} />
        ) : (
          t("assistant.loading")
        )}
      </EditSheetBody>
    );
  }
  return (
    <SettingsForm
      settings={query.data}
      onSave={async (values) => {
        const result = await api.call("assistant/settings/update", values);
        if (!result.ok) {
          return assistantErrorMessage(t, result.error);
        }
        cache.setQueryData(key, result.value);
        await cache.invalidateQueries({ queryKey: ["assistant", employeeId] });
        return null;
      }}
    />
  );
}

/** Владелец явно включает передачу данных общему внешнему провайдеру. */
function SettingsForm({
  settings,
  onSave,
}: {
  settings: AssistantSettingsResponse;
  onSave: (request: AssistantSettingsRequest) => Promise<string | null>;
}) {
  const { t } = useI18n();
  const { close } = useEditSheet();
  const [enabled, setEnabled] = useState(settings.enabled);
  const [limit, setLimit] = useState(String(settings.dailyMessageLimit));
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const parsed = Number(limit);
  const valid = Number.isInteger(parsed) && parsed > 0 && parsed <= 2147483647;
  const dirty = enabled !== settings.enabled || limit !== String(settings.dailyMessageLimit);
  async function save() {
    if (saving || !valid) {
      return;
    }
    setSaving(true);
    try {
      const error = await onSave({ enabled, dailyMessageLimit: parsed });
      setFailure(error);
      if (error === null) {
        close();
      }
    } finally {
      setSaving(false);
    }
  }
  return (
    <EditSheetForm
      dirty={dirty}
      submitting={saving}
      submitDisabled={!valid || (enabled && !settings.available)}
      onSubmit={() => {
        void save();
      }}
    >
      <p>{settings.available ? t("assistant.connected") : t("assistant.error.unavailable")}</p>
      <p className="text-sm text-muted-foreground">{t("assistant.dataNotice")}</p>
      <div className="flex items-center gap-3">
        <Switch
          id="assistant-enabled"
          checked={enabled}
          disabled={!settings.available}
          onCheckedChange={setEnabled}
        />
        <Label htmlFor="assistant-enabled">{t("assistant.enabled")}</Label>
      </div>
      <div className="space-y-2">
        <Label htmlFor="assistant-limit">{t("assistant.dailyLimit")}</Label>
        <Input
          id="assistant-limit"
          type="number"
          min={1}
          max={2147483647}
          step={1}
          value={limit}
          onChange={(event) => {
            setLimit(event.target.value);
          }}
        />
      </div>
      <p>{t("assistant.usage", { count: settings.acceptedMessages })}</p>
      {!valid && <FormAlert message={t("assistant.error.validation")} />}
      {failure !== null && <FormAlert message={failure} />}
    </EditSheetForm>
  );
}
