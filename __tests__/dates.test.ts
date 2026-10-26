import {
  addDays,
  addMonths,
  dayRange,
  endOfMonth,
  isDayKey,
  startOfMonth,
  startOfWeek,
  weekdayIndex,
} from '../src/domain/dates';

describe('day key arithmetic', () => {
  it('adds days across month and year boundaries', () => {
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('builds inclusive ranges', () => {
    expect(dayRange('2026-10-30', '2026-11-02')).toEqual([
      '2026-10-30',
      '2026-10-31',
      '2026-11-01',
      '2026-11-02',
    ]);
  });

  it('finds ISO week starts (Monday)', () => {
    expect(weekdayIndex('2026-10-26')).toBe(0); // Monday
    expect(startOfWeek('2026-11-01')).toBe('2026-10-26'); // Sunday → Monday before
  });

  it('handles month bounds', () => {
    expect(startOfMonth('2026-02-17')).toBe('2026-02-01');
    expect(endOfMonth('2028-02-03')).toBe('2028-02-29');
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-01');
  });

  it('validates keys', () => {
    expect(isDayKey('2026-10-26')).toBe(true);
    expect(isDayKey('26-10-2026')).toBe(false);
  });
});
