import type { ClientAttendanceItemSchema, MembershipSchema } from "@/api/generated/contracts";
import { membershipView } from "../membership/membershipView";

export type AttendanceFilter = "all" | "PRESENT" | "ABSENT";

/** Только начавшиеся/будущие неистёкшие абонементы; начавшийся с ближайшим окончанием первый. */
export function currentMemberships(memberships: readonly MembershipSchema[] = []) {
  return memberships
    .filter((membership) => {
      const view = membershipView(membership);
      return ["active", "ending", "upcoming"].includes(view.phase) && !view.expired;
    })
    .toSorted((a, b) => {
      const av = membershipView(a),
        bv = membershipView(b);
      const phase = Number(av.phase === "upcoming") - Number(bv.phase === "upcoming");
      if (phase !== 0) {
        return phase;
      }
      const end = (av.endDate ?? "~").localeCompare(bv.endDate ?? "~");
      return end === 0 ? a.id.localeCompare(b.id) : end;
    });
}

export function attendanceHistory(
  items: readonly ClientAttendanceItemSchema[] = [],
  filter: AttendanceFilter = "all",
) {
  return items
    .filter((item) => filter === "all" || item.presence === filter)
    .toSorted((a, b) => `${b.date}T${b.startTime}`.localeCompare(`${a.date}T${a.startTime}`));
}

export function attendanceSummary(items: readonly ClientAttendanceItemSchema[] = []) {
  const history = attendanceHistory(items);
  const recent = history.slice(0, 10);
  return {
    recent,
    present: recent.filter((item) => item.presence === "PRESENT").length,
    lastVisit: history.find((item) => item.presence === "PRESENT") ?? null,
  };
}

/** Полных лет на дату [today] при дате рождения [birthday] (`YYYY-MM-DD`). */
export function ageOn(birthday: string, today: Date): number {
  const [year = 0, month = 1, day = 1] = birthday.split("-").map(Number);
  const beforeBirthday =
    today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day);
  return today.getFullYear() - year - (beforeBirthday ? 1 : 0);
}
