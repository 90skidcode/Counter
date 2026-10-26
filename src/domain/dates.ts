/**
 * Pure helpers for tracking-day keys (yyyy-MM-dd). Day boundaries themselves (including the
 * configurable reset time) are computed in Kotlin; JS only does calendar arithmetic on keys.
 */

const DAY_MS = 86_400_000;

function toUtc(dayKey: string): number {
  const [y, m, d] = dayKey.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

function fromUtc(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

export function isDayKey(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(toUtc(value));
}

export function addDays(dayKey: string, days: number): string {
  return fromUtc(toUtc(dayKey) + days * DAY_MS);
}

/** Inclusive list of day keys from `from` to `to`. */
export function dayRange(from: string, to: string): string[] {
  const out: string[] = [];
  for (let t = toUtc(from); t <= toUtc(to); t += DAY_MS) {
    out.push(fromUtc(t));
  }
  return out;
}

/** 0 = Monday … 6 = Sunday. */
export function weekdayIndex(dayKey: string): number {
  return (new Date(toUtc(dayKey)).getUTCDay() + 6) % 7;
}

/** Monday of the ISO week containing the day. */
export function startOfWeek(dayKey: string): string {
  return addDays(dayKey, -weekdayIndex(dayKey));
}

export function startOfMonth(dayKey: string): string {
  return `${dayKey.slice(0, 7)}-01`;
}

export function endOfMonth(dayKey: string): string {
  const [y, m] = dayKey.split('-').map(Number);
  return fromUtc(Date.UTC(y, m, 0));
}

export function monthKey(dayKey: string): string {
  return dayKey.slice(0, 7);
}

export function addMonths(dayKey: string, months: number): string {
  const [y, m] = dayKey.split('-').map(Number);
  return fromUtc(Date.UTC(y, m - 1 + months, 1));
}
