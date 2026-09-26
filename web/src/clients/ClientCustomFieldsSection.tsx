import type { CustomFieldDefinition, CustomFieldValue } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { customFieldDisplay } from "./clientCells";
import { customFieldValueOf } from "./customFieldValues";

/** Дополнительные поля клиента: по одной строке на каждое определение филиала. */
export function ClientCustomFieldsSection({
  definitions,
  values,
}: {
  readonly definitions: readonly CustomFieldDefinition[];
  readonly values: readonly CustomFieldValue[];
}) {
  const { t } = useI18n();
  if (definitions.length === 0) {
    return null;
  }
  return (
    <div className="space-y-1 rounded-lg border bg-card p-4">
      <h2 className="pb-2 text-base font-semibold">{t("clients.additionalFields")}</h2>
      {definitions.map((def) => {
        const value = customFieldValueOf(values, def.fieldKey);
        return (
          <div key={def.fieldKey} className="flex justify-between gap-4 py-1 text-sm">
            <span className="text-muted-foreground">{def.label}</span>
            <span className="text-right">
              {value === undefined ? (
                <span className="text-muted-foreground">{t("clients.detail.notSpecified")}</span>
              ) : (
                customFieldDisplay(value)
              )}
            </span>
          </div>
        );
      })}
    </div>
  );
}
