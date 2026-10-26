import {
  formatCount,
  formatDuration,
  formatMinutesOfDay,
} from '../src/domain/format';
import {
  parseMilestones,
  parseOptionalInt,
  parseResetTime,
  withPlatformRule,
} from '../src/domain/settingsDraft';
import { buildDashboard } from '../src/domain/summary';
import { t } from '../src/i18n';
import { fixtureSettings, fixtureSummary } from './support/fixtures';

describe('dashboard model', () => {
  it('keeps verified, estimated and time separate', () => {
    const m = buildDashboard(fixtureSummary());
    expect(m.verified).toBe(3);
    expect(m.estimated).toBe(9);
    expect(m.activeMillis).toBe(900_000);
    expect(m.progress).toBeCloseTo(3 / 50);
    expect(m.countsUnavailable).toBe(false);
  });

  it('reports unknown counts when no platform can count', () => {
    const s = fixtureSummary();
    s.platforms = s.platforms.map(p => ({
      ...p,
      verifiedVideos: null,
      estimatedVideos: null,
      activeMillis: null,
    }));
    const m = buildDashboard(s);
    expect(m.countsUnavailable).toBe(true);
    expect(m.activeMillis).toBeNull();
  });
});

describe('formatting', () => {
  it('never shows unknown as zero', () => {
    expect(formatCount(null)).toBe('—');
    expect(formatCount(0)).toBe('0');
    expect(formatDuration(null)).toBe(t('common.unknown'));
  });

  it('formats durations and times', () => {
    expect(formatDuration(30_000)).toBe('< 1 min');
    expect(formatDuration(42 * 60_000)).toBe('42 min');
    expect(formatDuration(90 * 60_000)).toBe('1 h 30 min');
    expect(formatMinutesOfDay(4 * 60 + 5)).toBe('04:05');
  });

  it('interpolates strings', () => {
    expect(t('dashboard.coverageValue', { active: 2, total: 5 })).toBe(
      '2 of 5 platforms monitored',
    );
  });
});

describe('settings drafts', () => {
  it('parses milestones and optional numbers', () => {
    expect(parseMilestones('25, 10 ,10 x')).toEqual({
      values: [10, 25],
      invalid: ['x'],
    });
    expect(parseOptionalInt('')).toBeNull();
    expect(parseOptionalInt('0')).toBe('invalid');
    expect(parseOptionalInt('12')).toBe(12);
  });

  it('parses reset times', () => {
    expect(parseResetTime('04:30')).toBe(270);
    expect(parseResetTime('24:00')).toBeNull();
    expect(parseResetTime('4.30')).toBeNull();
  });

  it('removes empty per-platform rules', () => {
    const s = withPlatformRule(fixtureSettings(), 'tiktok', {
      maxVideos: 10,
      maxMinutes: null,
    });
    expect(s.limits.perPlatform.tiktok).toEqual({
      maxVideos: 10,
      maxMinutes: null,
    });
    expect(
      withPlatformRule(s, 'tiktok', { maxVideos: null, maxMinutes: null })
        .limits.perPlatform.tiktok,
    ).toBeUndefined();
  });
});
