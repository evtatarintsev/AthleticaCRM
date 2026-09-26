import { useState } from "react";
import type { ApiClient } from "@/api/client";
import type { CustomFieldDefinition } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { useI18n } from "@/i18n/context";
import { downloadBlob } from "@/lib/download";
import { apiErrorMessage } from "@/query/apiErrorMessage";
import { CLIENT_FIELD_KEYS, standardColumnLabelKey } from "./clientColumns";

/**
 * Диалог экспорта клиентов: выбор полей для CSV-файла. Экспортируются клиенты
 * организации целиком (сервер не фильтрует по выбранным строкам списка) —
 * паритет с `ExportScreen` KMP-клиента, где выбор строк тоже не влияет на выгрузку.
 */
export function ClientExportDialog({
  api,
  customFields,
  open,
  onOpenChange,
}: {
  readonly api: ApiClient;
  readonly customFields: readonly CustomFieldDefinition[];
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
}) {
  const { t } = useI18n();
  const orderedKeys: readonly string[] = [
    ...CLIENT_FIELD_KEYS,
    ...customFields.map((field) => field.fieldKey),
  ];
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set(orderedKeys));
  const [exporting, setExporting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  if (!open) {
    return null;
  }

  const toggle = (key: string, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(key);
      } else {
        next.delete(key);
      }
      return next;
    });
  };

  const download = async () => {
    setExporting(true);
    setFailure(null);
    const result = await api.call("clients/export", {
      fields: orderedKeys.filter((key) => selected.has(key)),
    });
    setExporting(false);
    if (!result.ok) {
      setFailure(apiErrorMessage(t, result.error));
      return;
    }
    downloadBlob("clients.csv", result.value);
    onOpenChange(false);
  };

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent aria-describedby={undefined} closeLabel={t("action.close")}>
        <DialogHeader>
          <DialogTitle>{t("clients.export.title")}</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          {failure !== null && <p className="text-sm text-destructive">{failure}</p>}
          {CLIENT_FIELD_KEYS.map((field) => (
            <ExportFieldRow
              key={field}
              fieldKey={field}
              label={t(standardColumnLabelKey(field))}
              checked={selected.has(field)}
              onCheckedChange={toggle}
            />
          ))}
          {customFields.map((field) => (
            <ExportFieldRow
              key={field.fieldKey}
              fieldKey={field.fieldKey}
              label={field.label}
              checked={selected.has(field.fieldKey)}
              onCheckedChange={toggle}
            />
          ))}
        </div>
        <DialogFooter closeLabel={t("action.cancel")}>
          <Button
            disabled={exporting || selected.size === 0}
            aria-busy={exporting}
            onClick={() => {
              void download();
            }}
          >
            {t("clients.export.download")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ExportFieldRow({
  fieldKey,
  label,
  checked,
  onCheckedChange,
}: {
  readonly fieldKey: string;
  readonly label: string;
  readonly checked: boolean;
  readonly onCheckedChange: (key: string, checked: boolean) => void;
}) {
  const id = `export-field-${fieldKey}`;
  return (
    <div className="flex items-center gap-2">
      <Checkbox
        id={id}
        checked={checked}
        onCheckedChange={(value) => {
          onCheckedChange(fieldKey, value === true);
        }}
      />
      <Label htmlFor={id}>{label}</Label>
    </div>
  );
}
