import type { Messages } from "./messages";
import type { ru } from "./ru";

/** Английский словарь; тексты совпадают с `composeApp/.../values-en/strings.xml`. */
export const en: Messages<typeof ru> = {
  "app.name": "AthleticaCRM",

  "auth.loginTitle": "Log in",
  "auth.loginSubtitle": "Log in to continue",
  "auth.registerTitle": "Sign up",
  "auth.registerSubtitle": "Create your organisation — it takes a minute",
  "auth.branchTitle": "Choose a branch",
  "auth.branchSubtitle": "Your account has access to several branches",
  "auth.email": "Email",
  "auth.password": "Password",
  "auth.orgName": "Organisation name",
  "auth.yourName": "Your name",
  "auth.timezone": "Time zone",
  "auth.currency": "Currency",
  "auth.currencyHint": "The currency cannot be changed after sign-up",
  "auth.passwordShow": "Show password",
  "auth.passwordHide": "Hide password",
  "auth.actionLogin": "Log in",
  "auth.actionRegister": "Sign up",
  "auth.noAccount": "Don't have an account?",
  "auth.haveAccount": "Already have an account?",

  "action.back": "Back",
  "action.close": "Close",
  "action.retry": "Retry",

  "error.invalidCredentials": "Invalid login or password",
  "error.noBranches": "The account has no access to any branch",
  "error.serviceUnavailable": "Service unavailable. Check your connection",
  "error.registrationFailed": "Registration failed. Please try again",
  "error.required": "Fill in this field",
  "error.invalidEmail": "Enter an email",

  "nav.label": "Sections",
  "nav.openMenu": "Open menu",
  "nav.home": "Home",
  "nav.schedule": "Schedule",
  "nav.clients": "Clients",
  "nav.groups": "Groups",
  "nav.employees": "Employees",
  "nav.tasks": "Tasks",
  "nav.settings": "Settings",

  "account.menu": "Account menu",
  "account.branch": "Branch “{name}”",
  "account.profile": "Profile",
  "account.switchBranch": "Switch branch",
  "account.language": "Language",
  "account.logout": "Log out",

  "language.ru": "Русский",
  "language.en": "English",

  "home.title": "Home",
  "home.notMigrated":
    "Sections are moving to the new interface gradually. For now they open in the main app.",
  "home.openApp": "Open the main app",

  "notFound.title": "Page not found",
  "notFound.text": "Check the address or go back home.",
  "notFound.home": "Go home",

  "session.loadError": "Failed to load data. Check your connection and try again.",
};
