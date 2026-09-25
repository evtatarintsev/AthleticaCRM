import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Объединяет классы Tailwind [inputs]: условные через `clsx`, конфликтующие — через `tailwind-merge`. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
