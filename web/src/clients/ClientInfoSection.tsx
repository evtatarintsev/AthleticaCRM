import type { ClientDetailResponse } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";

/** Строка «подпись — значение» сведений о клиенте; без значения показывает плейсхолдер. */
function InfoRow({
  label,
  value,
  href,
}: {
  readonly label: string;
  readonly value: string | null;
  readonly href?: string;
}) {
  const { t } = useI18n();
  return (
    <div className="flex justify-between gap-4 py-1 text-sm">
      <span className="text-muted-foreground">{label}</span>
      {value === null ? (
        <span className="text-muted-foreground">{t("clients.detail.notSpecified")}</span>
      ) : href === undefined ? (
        <span className="text-right">{value}</span>
      ) : (
        <a href={href} className="text-right text-primary underline">
          {value}
        </a>
      )}
    </div>
  );
}

/** Основные сведения о клиенте: контакты, день рождения, группы. */
export function ClientInfoSection({ client }: { readonly client: ClientDetailResponse }) {
  const { t, format } = useI18n();
  const phones = client.contacts.filter((contact) => contact.type === "PHONE");
  const others = client.contacts.filter((contact) => contact.type !== "PHONE");

  return (
    <div className="space-y-1 rounded-lg border bg-card p-4">
      <h2 className="pb-2 text-base font-semibold">{t("clients.detail.basicInfo")}</h2>
      {phones.length === 0 ? (
        <InfoRow label={t("contactType.PHONE")} value={null} />
      ) : (
        phones.map((contact, index) => (
          <InfoRow
            key={index}
            label={t("contactType.PHONE")}
            value={contact.value}
            href={`tel:${contact.value}`}
          />
        ))
      )}
      {others.map((contact, index) =>
        contact.type === "EMAIL" ? (
          <InfoRow
            key={index}
            label={t(`contactType.${contact.type}`)}
            value={contact.value}
            href={`mailto:${contact.value}`}
          />
        ) : (
          <InfoRow key={index} label={t(`contactType.${contact.type}`)} value={contact.value} />
        ),
      )}
      <InfoRow
        label={t("clients.birthday")}
        value={client.birthday === null ? null : format.date(client.birthday)}
      />
      <div className="flex justify-between gap-4 py-1 text-sm">
        <span className="text-muted-foreground">{t("clients.column.groups")}</span>
        {client.groups.length === 0 ? (
          <span className="text-right text-muted-foreground">{t("clients.detail.noGroups")}</span>
        ) : (
          <div className="flex flex-wrap justify-end gap-1">
            {client.groups.map((group) => (
              <span
                key={group.id}
                className="rounded-full border bg-secondary px-2 py-0.5 text-xs text-secondary-foreground"
              >
                {group.name}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
