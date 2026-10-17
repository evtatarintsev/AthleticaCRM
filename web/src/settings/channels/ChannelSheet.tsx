import { ArrowLeftIcon, ChevronRightIcon } from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import type { ChannelIntegrationSchema } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { ConfirmDialog } from "@/ui/ConfirmDialog";
import { EditSheet, EditSheetBody } from "@/ui/EditSheet";
import { useEditSheet } from "@/ui/editSheetContext";
import {
  SmscChannelForm,
  TelegramChannelForm,
  TwilioChannelForm,
  type ChannelSaveHandler,
} from "./ChannelForms";

/** Провайдер канала — значение поля `type` настроек канала. */
export type ChannelProvider = ChannelIntegrationSchema["settings"]["type"];

/** Провайдеры в порядке показа на шаге выбора. */
const PROVIDERS: readonly ChannelProvider[] = ["smsc_sms", "telegram_bot", "twilio_sms"];

/** Шаг панели создания: выбор провайдера или форма выбранного провайдера. */
type Step =
  { readonly kind: "provider" } | { readonly kind: "form"; readonly provider: ChannelProvider };

/**
 * Правая панель создания или изменения канала связи. Создание ([channel] `null`) начинается
 * с выбора провайдера, затем показывает его форму, из которой можно вернуться к выбору.
 * Изменение сразу открывает форму провайдера канала: провайдер не меняется.
 */
export function ChannelSheet({
  open,
  onOpenChange,
  channel,
  onSave,
}: {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly channel: ChannelIntegrationSchema | null;
  readonly onSave: ChannelSaveHandler;
}) {
  const { t } = useI18n();
  return (
    <EditSheet
      open={open}
      onOpenChange={onOpenChange}
      title={channel === null ? t("channels.create") : t("channels.edit")}
    >
      {channel === null ? (
        <NewChannelContent onSave={onSave} />
      ) : (
        <ChannelForm
          channel={channel}
          onSave={onSave}
          header={<ProviderLabel provider={channel.settings.type} />}
        />
      )}
    </EditSheet>
  );
}

/** Содержимое панели создания: шаги «провайдер → форма» с возвратом к выбору. */
function NewChannelContent({ onSave }: { readonly onSave: ChannelSaveHandler }) {
  const { t } = useI18n();
  const [step, setStep] = useState<Step>({ kind: "provider" });
  const [confirmingBack, setConfirmingBack] = useState(false);

  const back = () => {
    setStep({ kind: "provider" });
  };

  switch (step.kind) {
    case "provider":
      return (
        <ProviderStep
          onChoose={(provider) => {
            setStep({ kind: "form", provider });
          }}
        />
      );
    case "form":
      return (
        <>
          <NewProviderForm
            provider={step.provider}
            onSave={onSave}
            header={(dirty) => (
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    if (dirty) {
                      setConfirmingBack(true);
                    } else {
                      back();
                    }
                  }}
                >
                  <ArrowLeftIcon aria-hidden />
                  {t("action.back")}
                </Button>
                <ProviderLabel provider={step.provider} />
              </div>
            )}
          />
          <ConfirmDialog
            open={confirmingBack}
            onOpenChange={setConfirmingBack}
            title={t("editSheet.discardTitle")}
            description={t("editSheet.discardDescription")}
            confirmLabel={t("editSheet.discardConfirm")}
            onConfirm={() => {
              back();
              return Promise.resolve();
            }}
          />
        </>
      );
  }
}

/** Шаг выбора провайдера: нажатие на провайдера открывает его форму. */
function ProviderStep({ onChoose }: { readonly onChoose: (provider: ChannelProvider) => void }) {
  const { t } = useI18n();
  const { reportGuard } = useEditSheet();

  useEffect(() => {
    reportGuard({ dirty: false, submitting: false });
  }, [reportGuard]);

  return (
    <EditSheetBody>
      <p className="text-sm text-muted-foreground">{t("channels.chooseProvider")}</p>
      <ul className="divide-y rounded-lg border">
        {PROVIDERS.map((provider) => (
          <li key={provider}>
            <button
              type="button"
              onClick={() => {
                onChoose(provider);
              }}
              className="flex w-full items-center gap-3 px-4 py-3 text-left text-sm font-medium hover:bg-accent"
            >
              <span className="flex-1">{t(`channels.provider.${provider}`)}</span>
              <ChevronRightIcon aria-hidden className="size-4 text-muted-foreground" />
            </button>
          </li>
        ))}
      </ul>
    </EditSheetBody>
  );
}

/** Название провайдера [provider] над полями формы. */
function ProviderLabel({ provider }: { readonly provider: ChannelProvider }) {
  const { t } = useI18n();
  return (
    <p className="text-sm font-medium text-muted-foreground">
      {t(`channels.provider.${provider}`)}
    </p>
  );
}

/** Пустая форма создания канала провайдера [provider]. */
function NewProviderForm({
  provider,
  onSave,
  header,
}: {
  readonly provider: ChannelProvider;
  readonly onSave: ChannelSaveHandler;
  readonly header: (dirty: boolean) => ReactNode;
}) {
  switch (provider) {
    case "smsc_sms":
      return <SmscChannelForm initial={null} onSave={onSave} header={header} />;
    case "telegram_bot":
      return <TelegramChannelForm initial={null} onSave={onSave} header={header} />;
    case "twilio_sms":
      return <TwilioChannelForm initial={null} onSave={onSave} header={header} />;
  }
}

/** Форма изменения канала [channel] его провайдера с шапкой [header]. */
function ChannelForm({
  channel,
  onSave,
  header,
}: {
  readonly channel: ChannelIntegrationSchema;
  readonly onSave: ChannelSaveHandler;
  readonly header: ReactNode;
}) {
  const { id, name, settings } = channel;
  const renderHeader = () => header;
  switch (settings.type) {
    case "smsc_sms":
      return (
        <SmscChannelForm initial={{ id, name, settings }} onSave={onSave} header={renderHeader} />
      );
    case "telegram_bot":
      return (
        <TelegramChannelForm
          initial={{ id, name, settings }}
          onSave={onSave}
          header={renderHeader}
        />
      );
    case "twilio_sms":
      return (
        <TwilioChannelForm initial={{ id, name, settings }} onSave={onSave} header={renderHeader} />
      );
  }
}
