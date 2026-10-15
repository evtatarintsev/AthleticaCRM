import { AtSignIcon, MailIcon, PhoneIcon, SendIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { ClientContactSchema } from "@/api/generated/contracts";
import { useI18n } from "@/i18n/context";

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
        <ContactRow key={contact.id} contact={contact} />
      ))}
    </ul>
  );
}

/** Иконка и ссылка контакта [contact] по его типу. */
function contactView(contact: ClientContactSchema): { icon: ReactNode; href: string } {
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
}

/** Строка контакта: иконка типа, значение-ссылка и подпись типа. */
function ContactRow({ contact }: { readonly contact: ClientContactSchema }) {
  const { t } = useI18n();
  const view = contactView(contact);
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
