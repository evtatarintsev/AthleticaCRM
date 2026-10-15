import type { ChannelConfig, ContactType } from "@/api/generated/contracts";

/** Вид канала — дискриминатор `type` в `ChannelConfig`. */
type ChannelKind = ChannelConfig["type"];

/**
 * Типы контактов, которые умеет адресовать вид канала (буквальный порт
 * `ChannelConfig.acceptedContactTypes` из `shared`).
 */
const ACCEPTED_CONTACT_TYPES: Readonly<Record<ChannelKind, readonly ContactType[]>> = {
  smsc_sms: ["PHONE"],
  twilio_sms: ["PHONE"],
  telegram_bot: ["TELEGRAM"],
};

/** Истина, если контакт типа [contactType] пригоден для канала вида [kind]. */
export function isContactCompatible(contactType: ContactType, kind: ChannelKind): boolean {
  return ACCEPTED_CONTACT_TYPES[kind].includes(contactType);
}
