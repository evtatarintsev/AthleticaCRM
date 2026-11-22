import type { ClientContactSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";
import { contactIcon } from "../contactIcon";

/**
 * Список контактов клиента [contacts] с кликабельными ссылками: телефон — `tel:`,
 * почта — `mailto:`, мессенджеры — на профиль. Без контактов — подпись «Контактов нет».
 * Общий для карточки клиента и её упрощённого варианта в боковой панели.
 */
export function ClientContactList({
  contacts,
}: {
  readonly contacts: readonly ClientContactSchema[];
}) {
  const { t } = useI18n();
  if (contacts.length === 0) {
    return <p className="px-4 py-3 text-sm text-muted-foreground">{t("clientCard.noContacts")}</p>;
  }
  return (
    <ul className="divide-y">
      {contacts.map((contact) => (
        <ContactRow
          key={contact.id}
          contact={contact}
          markPrimary={
            contact.isPrimary && contacts.filter((other) => other.type === contact.type).length > 1
          }
        />
      ))}
    </ul>
  );
}

/** Ссылка контакта [contact] по его типу. */
function contactHref(contact: ClientContactSchema): string {
  switch (contact.type) {
    case "PHONE":
      return `tel:${contact.value}`;
    case "EMAIL":
      return `mailto:${contact.value}`;
    case "TELEGRAM":
      return `https://t.me/${contact.value.replace(/^@/, "")}`;
    case "VK":
      return `https://vk.com/${contact.value}`;
    case "FACEBOOK":
      return `https://facebook.com/${contact.value}`;
  }
}

/**
 * Строка контакта: иконка типа, значение-ссылка и подпись — комментарий или название типа.
 * [markPrimary] — показать пометку «основной» (только когда контактов этого типа несколько).
 */
function ContactRow({
  contact,
  markPrimary,
}: {
  readonly contact: ClientContactSchema;
  readonly markPrimary: boolean;
}) {
  const { t } = useI18n();
  return (
    <li className="flex items-center gap-3 px-4 py-2.5 text-sm">
      <span className="inline-flex size-7.5 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground [&_svg]:size-4">
        {contactIcon(contact.type)}
      </span>
      <span className="min-w-0 flex-1">
        <a
          href={contactHref(contact)}
          className="block truncate font-medium text-primary hover:underline"
        >
          {contact.value}
        </a>
        <span className="block text-xs text-muted-foreground">
          {contact.label ?? t(`contactType.${contact.type}`)}
          {markPrimary && ` · ${t("clients.contactPrimary")}`}
        </span>
      </span>
    </li>
  );
}
