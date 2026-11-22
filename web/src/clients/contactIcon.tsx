import { AtSignIcon, MailIcon, PhoneIcon, SendIcon } from "lucide-react";
import type { ReactNode } from "react";
import type { ContactType } from "@/api/generated/contracts";

/** Иконка типа контакта [type]: общая для формы контактов и списка контактов клиента. */
export function contactIcon(type: ContactType): ReactNode {
  switch (type) {
    case "PHONE":
      return <PhoneIcon aria-hidden />;
    case "EMAIL":
      return <MailIcon aria-hidden />;
    case "TELEGRAM":
      return <SendIcon aria-hidden />;
    case "VK":
      return <AtSignIcon aria-hidden />;
    case "FACEBOOK":
      return <AtSignIcon aria-hidden />;
  }
}
