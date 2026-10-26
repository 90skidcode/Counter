import { dayRange, monthKey, startOfWeek } from './dates';
import type { HistoryRow } from './types';

export interface DayTotal {
  dayKey: string;
  verified: number;
  estimated: number;
  /** Null when no time measurement exists for the day (unknown, not zero). */
  activeMillis: number | null;
  /** True when any row exists for the day. */
  hasData: boolean;
}

export interface PeriodTotal extends Omit<DayTotal, 'dayKey'> {
  periodKey: string;
  days: number;
  daysWithData: number;
}

/** Sums rows per day over the full range, filling missing days with "no data". */
export function dailyTotals(
  rows: HistoryRow[],
  fromDay: string,
  toDay: string,
  platformFilter?: ReadonlySet<string>,
): DayTotal[] {
  const byDay = new Map<string, DayTotal>();
  for (const day of dayRange(fromDay, toDay)) {
    byDay.set(day, {
      dayKey: day,
      verified: 0,
      estimated: 0,
      activeMillis: null,
      hasData: false,
    });
  }
  for (const row of rows) {
    if (platformFilter && !platformFilter.has(row.platformId)) {
      continue;
    }
    const total = byDay.get(row.dayKey);
    if (!total) {
      continue;
    }
    total.hasData = true;
    total.verified += row.verifiedVideos;
    total.estimated += row.estimatedVideos;
    if (row.activeMillis != null) {
      total.activeMillis = (total.activeMillis ?? 0) + row.activeMillis;
    }
  }
  return [...byDay.values()];
}

function groupBy(
  days: DayTotal[],
  keyOf: (dayKey: string) => string,
): PeriodTotal[] {
  const groups = new Map<string, PeriodTotal>();
  for (const d of days) {
    const key = keyOf(d.dayKey);
    let g = groups.get(key);
    if (!g) {
      g = {
        periodKey: key,
        verified: 0,
        estimated: 0,
        activeMillis: null,
        hasData: false,
        days: 0,
        daysWithData: 0,
      };
      groups.set(key, g);
    }
    g.days += 1;
    g.verified += d.verified;
    g.estimated += d.estimated;
    if (d.activeMillis != null) {
      g.activeMillis = (g.activeMillis ?? 0) + d.activeMillis;
    }
    if (d.hasData) {
      g.hasData = true;
      g.daysWithData += 1;
    }
  }
  return [...groups.values()];
}

export const weeklyTotals = (days: DayTotal[]) => groupBy(days, startOfWeek);
export const monthlyTotals = (days: DayTotal[]) => groupBy(days, monthKey);

/** Average videos per day over days that have data, or null if none. */
export function averagePerDay(period: PeriodTotal): number | null {
  if (period.daysWithData === 0) {
    return null;
  }
  return (period.verified + period.estimated) / period.daysWithData;
}
