import {
  averagePerDay,
  dailyTotals,
  monthlyTotals,
  weeklyTotals,
} from '../src/domain/history';
import type { HistoryRow } from '../src/domain/types';

const rows: HistoryRow[] = [
  {
    dayKey: '2026-10-26',
    platformId: 'youtube_shorts',
    verifiedVideos: 0,
    estimatedVideos: 12,
    activeMillis: 600_000,
    timeSource: 'usage_stats',
  },
  {
    dayKey: '2026-10-26',
    platformId: 'brave',
    verifiedVideos: 3,
    estimatedVideos: 0,
    activeMillis: null,
    timeSource: 'web_site_timer',
  },
  {
    dayKey: '2026-10-28',
    platformId: 'youtube_shorts',
    verifiedVideos: 0,
    estimatedVideos: 4,
    activeMillis: 120_000,
    timeSource: 'usage_stats',
  },
];

describe('history aggregation', () => {
  it('fills missing days as "no data" rather than zero', () => {
    const days = dailyTotals(rows, '2026-10-26', '2026-10-28');
    expect(days.map(d => d.hasData)).toEqual([true, false, true]);
    expect(days[1].activeMillis).toBeNull();
    expect(days[0]).toMatchObject({
      verified: 3,
      estimated: 12,
      activeMillis: 600_000,
    });
  });

  it('filters by platform', () => {
    const days = dailyTotals(
      rows,
      '2026-10-26',
      '2026-10-28',
      new Set(['brave']),
    );
    expect(days[0]).toMatchObject({
      verified: 3,
      estimated: 0,
      activeMillis: null,
    });
    expect(days[2].hasData).toBe(false);
  });

  it('groups into weeks and months and averages over days with data', () => {
    const days = dailyTotals(rows, '2026-10-26', '2026-11-02');
    const weeks = weeklyTotals(days);
    expect(weeks.map(w => w.periodKey)).toEqual(['2026-10-26', '2026-11-02']);
    expect(weeks[0]).toMatchObject({
      verified: 3,
      estimated: 16,
      daysWithData: 2,
      days: 7,
    });
    expect(averagePerDay(weeks[0])).toBeCloseTo(9.5);
    expect(averagePerDay(weeks[1])).toBeNull();
    expect(monthlyTotals(days).map(m => m.periodKey)).toEqual([
      '2026-10',
      '2026-11',
    ]);
  });
});
