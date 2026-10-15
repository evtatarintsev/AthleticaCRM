import { Link } from "@tanstack/react-router";
import { ChevronDownIcon, PencilIcon } from "lucide-react";
import { useState } from "react";
import type { ClientDetailResponse, CustomFieldDefinition } from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { customFieldDisplay } from "../clientCells";
import { customFieldValueOf } from "../customFieldValues";
import { ClientContactList } from "./ClientContactList";

/** Поле анкеты: подпись и значение; `null` — значение не указано. */
interface ProfileField {
  readonly label: string;
  readonly value: string | null;
}

/**
 * Профиль клиента — левая колонка карточки: контакты и анкета, разбитая на сворачиваемые
 * разделы («Основное», «Дополнительные поля» организации).
 */
export function ClientCardProfile({
  client,
  leadSourceName,
  customFieldDefs,
  editable,
}: {
  readonly client: ClientDetailResponse;
  /** Название источника клиента; `null` — не указан или справочник не загружен. */
  readonly leadSourceName: string | null;
  readonly customFieldDefs: readonly CustomFieldDefinition[];
  /** Можно ли перейти к редактированию анкеты (клиент не в архиве). */
  readonly editable: boolean;
}) {
  const { t, format } = useI18n();
  const main: readonly ProfileField[] = [
    { label: t("clients.gender"), value: t(`gender.${client.gender}`) },
    {
      label: t("clients.birthday"),
      value: client.birthday === null ? null : format.date(client.birthday),
    },
    { label: t("clients.leadSource"), value: leadSourceName },
  ];
  const custom: readonly ProfileField[] = customFieldDefs.map((def) => {
    const value = customFieldValueOf(client.customFields, def.fieldKey);
    return { label: def.label, value: value === undefined ? null : customFieldDisplay(value) };
  });

  return (
    <div className="space-y-4">
      <div className="rounded-lg border bg-card">
        <h2 className="border-b px-4 py-3 font-medium">{t("clients.contacts")}</h2>
        <ClientContactList contacts={client.contacts} />
      </div>

      <div className="rounded-lg border bg-card">
        <div className="flex items-center justify-between border-b py-2 pr-2 pl-4">
          <h2 className="font-medium">{t("clientCard.profile")}</h2>
          {editable && (
            <Button variant="ghost" size="icon-sm" asChild>
              <Link
                to="/clients/$clientId/edit"
                params={{ clientId: client.id }}
                aria-label={t("clientCard.action.editProfile")}
                title={t("clientCard.action.editProfile")}
              >
                <PencilIcon aria-hidden />
              </Link>
            </Button>
          )}
        </div>
        <div className="divide-y">
          <ProfileSection title={t("clients.detail.basicInfo")} fields={main} />
          {custom.length > 0 && (
            <ProfileSection title={t("clients.additionalFields")} fields={custom} />
          )}
        </div>
      </div>
    </div>
  );
}

/** Сворачиваемый раздел анкеты со счётчиком заполненных полей. */
function ProfileSection({
  title,
  fields,
}: {
  readonly title: string;
  readonly fields: readonly ProfileField[];
}) {
  const { t } = useI18n();
  const [expanded, setExpanded] = useState(true);
  const filled = fields.filter((field) => field.value !== null).length;
  return (
    <section>
      <button
        type="button"
        aria-expanded={expanded}
        onClick={() => {
          setExpanded(!expanded);
        }}
        className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm font-medium hover:bg-accent"
      >
        <ChevronDownIcon
          aria-hidden
          className={cn(
            "size-4 text-muted-foreground transition-transform",
            !expanded && "-rotate-90",
          )}
        />
        <span className="flex-1">{title}</span>
        <span className="text-xs font-normal text-muted-foreground">
          {filled}/{fields.length}
        </span>
      </button>
      {expanded && (
        <dl className="space-y-2 px-4 pb-3">
          {fields.map((field, index) => (
            <div key={index} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-2 text-sm">
              <dt className="hyphens-auto break-words text-muted-foreground">{field.label}</dt>
              <dd className="min-w-0 break-words">
                {field.value ?? (
                  <span className="text-muted-foreground">{t("clients.detail.notSpecified")}</span>
                )}
              </dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}
