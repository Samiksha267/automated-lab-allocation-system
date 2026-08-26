/**
 * Single formatting utility (PART 62 of the Phase 20 brief) - every screen
 * goes through these functions rather than ad-hoc `Date`/`toLocaleString`
 * calls scattered through components.
 *
 * Scheduling values (`allocationDate`/`startTime`/`endTime`) are plain
 * `LocalDate`/`LocalTime` strings from the backend, already expressed in the
 * college's own timezone convention (`Asia/Kolkata`, see
 * `SchedulingTimeMapper` server-side) - they are rendered as-is, with no
 * `Date` parsing or browser-timezone conversion, so a slot's displayed time
 * can never silently shift.
 *
 * Audit/system `Instant` values (`createdAt`, `uploadedAt`, etc.) genuinely
 * are UTC instants and are formatted using the browser's locale/timezone via
 * `Intl.DateTimeFormat` - appropriate for "when did this happen" system
 * timestamps, unlike a fixed timetable slot.
 */

export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return "-";
  const [year, month, day] = isoDate.split("-");
  return `${day}/${month}/${year}`;
}

export function formatTime(isoTime: string | null | undefined): string {
  if (!isoTime) return "-";
  return isoTime.slice(0, 5);
}

export function formatTimeRange(start: string | null | undefined, end: string | null | undefined): string {
  return `${formatTime(start)}-${formatTime(end)}`;
}

const instantFormatter = new Intl.DateTimeFormat(undefined, {
  year: "numeric",
  month: "short",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

export function formatInstant(isoInstant: string | null | undefined): string {
  if (!isoInstant) return "-";
  try {
    return instantFormatter.format(new Date(isoInstant));
  } catch {
    return isoInstant;
  }
}

const WEEKDAY_NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

/**
 * Derives the day-of-week name from a plain `YYYY-MM-DD` date string without ever constructing a
 * locale/timezone-sensitive `Date` from it - `Date.UTC` + `getUTCDay()` on the parsed Y/M/D components is
 * timezone-neutral, unlike `new Date("YYYY-MM-DD")` (parsed as UTC midnight, which can display as the previous
 * calendar day in a negative-UTC-offset browser) or any local-timezone constructor.
 */
export function dayOfWeekName(isoDate: string | null | undefined): string {
  if (!isoDate) return "-";
  const [year, month, day] = isoDate.split("-").map(Number);
  return WEEKDAY_NAMES[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

export function titleCase(value: string | null | undefined): string {
  if (!value) return "";
  return value
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2") // camelCase (e.g. audit metadata keys) -> snake_case first
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
