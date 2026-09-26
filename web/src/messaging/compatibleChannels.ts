import type { ChannelType, ContactType } from "@/api/generated/contracts";

/**
 * Каналы доставки, для которых пригоден контакт данного типа (буквальный порт
 * `ContactType.compatibleChannels` из `shared`): один телефон годится для нескольких каналов,
 * профиль в Facebook пока без канала доставки.
 */
const COMPATIBLE_CHANNELS: Readonly<Record<ContactType, readonly ChannelType[]>> = {
  PHONE: ["SMS", "WHATSAPP", "TELEGRAM", "MAX"],
  EMAIL: ["EMAIL"],
  TELEGRAM: ["TELEGRAM"],
  VK: ["VK"],
  FACEBOOK: [],
};

/** Истина, если контакт типа [contactType] пригоден для канала доставки [channelType]. */
export function isContactCompatible(contactType: ContactType, channelType: ChannelType): boolean {
  return COMPATIBLE_CHANNELS[contactType].includes(channelType);
}
