import type { LimitStatus, PlatformToday, TodaySummary } from './types';

export interface DashboardModel {
  verified: number;
  estimated: number;
  /** Total app/site time across platforms with a known measurement, or null if none. */
  activeMillis: number | null;
  global: LimitStatus | null;
  /** 0..1 progress towards the global video limit, on the enforcement basis. */
  progress: number | null;
  activePlatforms: number;
  monitoredPlatforms: number;
  lastDetectedAt: number | null;
  countsUnavailable: boolean;
}

export function globalLimit(summary: TodaySummary): LimitStatus | null {
  return summary.limits.find(l => l.scope === 'global') ?? null;
}

export function platformLimit(
  summary: TodaySummary,
  platformId: string,
): LimitStatus | null {
  return summary.limits.find(l => l.platformId === platformId) ?? null;
}

export function buildDashboard(summary: TodaySummary): DashboardModel {
  const enabled = summary.platforms.filter(p => p.enabled);
  const sum = (pick: (p: PlatformToday) => number | null) =>
    enabled.reduce((acc, p) => acc + (pick(p) ?? 0), 0);
  const timed = enabled.filter(p => p.activeMillis != null);
  const global = globalLimit(summary);
  const progress =
    global && global.maxVideos
      ? Math.min(1, global.enforcedVideos / global.maxVideos)
      : null;
  const countsKnown = enabled.filter(
    p => p.verifiedVideos != null || p.estimatedVideos != null,
  );
  const lastDetected = enabled
    .map(p => p.lastDetectedAt)
    .filter((v): v is number => v != null);
  return {
    verified: sum(p => p.verifiedVideos),
    estimated: sum(p => p.estimatedVideos),
    activeMillis: timed.length
      ? timed.reduce((a, p) => a + (p.activeMillis ?? 0), 0)
      : null,
    global,
    progress,
    activePlatforms: enabled.filter(p => p.monitoringStatus === 'active')
      .length,
    monitoredPlatforms: enabled.length,
    lastDetectedAt: lastDetected.length ? Math.max(...lastDetected) : null,
    countsUnavailable: countsKnown.length === 0,
  };
}
