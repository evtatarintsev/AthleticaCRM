import type { Messages } from "./messages";
import type { ru } from "./ru";

/** Английский словарь кабинета клиента. */
export const en: Messages<typeof ru> = {
  "app.title": "Client portal",
  "app.loading": "Loading…",
  "app.retry": "Try again",
  "error.unavailable": "The server is unavailable. Check your connection and try again",
  "error.contract": "Something went wrong. Reload the page",
  "signIn.phoneTitle": "Sign in",
  "signIn.phoneHint": "Enter the phone number you gave to the club",
  "signIn.phoneLabel": "Phone number",
  "signIn.getCode": "Get a code",
  "signIn.codeTitle": "Enter the code",
  "signIn.codeSent": "We sent a code to {phone}",
  "signIn.codeLabel": "Code from the message",
  "signIn.login": "Sign in",
  "signIn.retryIn": "You can request a new code in {time}",
  "signIn.resend": "Send again",
  "signIn.viaSms": "Send by SMS",
  "signIn.changePhone": "Change number",
  "signIn.termsTitle": "Terms of use",
  "signIn.termsText":
    "To sign in, accept the terms of use of the portal and the personal data processing policy.",
  "signIn.termsAccept": "Accept and sign in",
  "invitations.title": "You are invited",
  "invitations.hint":
    "Your number is listed for clients of these organizations. Connect to see their data. Don't recognize an organization? Tap “Not me” and we will let the administrator know.",
  "invitations.invitedAt": "Invited on {date}",
  "invitations.clients": {
    one: "{count} client with your number",
    other: "{count} clients with your number",
  },
  "invitations.connect": "Connect",
  "invitations.notMe": "Not me",
  "invitations.back": "Back",
  "profiles.empty": "No profiles yet — ask the administrator",
  "profiles.switch": "Switch profile",
  "profiles.title": "Whose data to show",
  "profiles.newInvitations": "New invitations",
  "profiles.isolation": "Each organization sees only its own clients.",
  "home.greeting": "Hello!",
  "home.cabinetOf": "This is the portal of {name}",
};
