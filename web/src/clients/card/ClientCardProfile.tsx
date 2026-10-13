import { Link } from "@tanstack/react-router";
import {
  AtSignIcon,
  ChevronDownIcon,
  MailIcon,
  PencilIcon,
  PhoneIcon,
  SendIcon,
} from "lucide-react";
import { useState, type ReactNode } from "react";
import type {
  ClientContactSchema,
  ClientDetailResponse,
  CustomFieldDefinition,
} from "@/api/generated/contracts";
import { Button } from "@/components/ui/button";
import { useI18n } from "@/i18n/context";
import { cn } from "@/lib/utils";
import { customFieldDisplay } from "../clientCells";
import { customFieldValueOf } from "../customFieldValues";

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
        {client.contacts.length === 0 ? (
          <p className="px-4 py-3 text-sm text-muted-foreground">{t("clientCard.noContacts")}</p>
        ) : (
          <ul className="divide-y">
            {client.contacts.map((contact) => (
              <ContactRow key={contact.id} contact={contact} />
            ))}
          </ul>
        )}
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

/** Строка контакта: иконка типа, значение-ссылка и подпись типа. */
function ContactRow({ contact }: { readonly contact: ClientContactSchema }) {
  const { t } = useI18n();
  const view = ((): { icon: ReactNode; href: string } => {
    switch (contact.type) {
      case "PHONE":
        return { icon: <PhoneIcon aria-hidden />, href: `tel:${contact.value}` };
      case "EMAIL":
        return { icon: <MailIcon aria-hidden />, href: `mailto:${contact.value}` };
      case "TELEGRAM":
        return {
          icon: <SendIcon aria-hidden />,
          href: `https://t.me/${contact.value.replace(/^@/, "")}`,
        };
      case "VK":
        return { icon: <AtSignIcon aria-hidden />, href: `https://vk.com/${contact.value}` };
      case "FACEBOOK":
        return { icon: <AtSignIcon aria-hidden />, href: `https://facebook.com/${contact.value}` };
    }
  })();
  return (
    <li className="flex items-center gap-3 px-4 py-2 text-sm">
      <span className="text-muted-foreground [&_svg]:size-4">{view.icon}</span>
      <a href={view.href} className="min-w-0 flex-1 truncate text-primary hover:underline">
        {contact.value}
      </a>
      <span className="shrink-0 text-xs text-muted-foreground">
        {t(`contactType.${contact.type}`)}
      </span>
    </li>
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
