import type { LimitRule, TrackerSettings } from './types';

/** Parses "10, 25 ,40" into sorted unique positive integers; invalid parts are reported. */
export function parseMilestones(text: string): {
  values: number[];
  invalid: string[];
} {
  const invalid: string[] = [];
  const values = new Set<number>();
  for (const part of text.split(/[\s,]+/).filter(Boolean)) {
    const n = Number(part);
    if (Number.isInteger(n) && n > 0) {
      values.add(n);
    } else {
      invalid.push(part);
    }
  }
  return { values: [...values].sort((a, b) => a - b), invalid };
}

/** Parses an optional positive integer field; empty means "no limit". */
export function parseOptionalInt(text: string): number | null | 'invalid' {
  const trimmed = text.trim();
  if (trimmed === '') {
    return null;
  }
  const n = Number(trimmed);
  return Number.isInteger(n) && n > 0 ? n : 'invalid';
}

export function withPlatformRule(
  settings: TrackerSettings,
  platformId: string,
  rule: LimitRule,
): TrackerSettings {
  const perPlatform = { ...settings.limits.perPlatform };
  if (rule.maxVideos == null && rule.maxMinutes == null) {
    delete perPlatform[platformId];
  } else {
    perPlatform[platformId] = rule;
  }
  return { ...settings, limits: { ...settings.limits, perPlatform } };
}

export function issuesFor<T extends { field: string }>(
  issues: T[],
  prefix: string,
): T[] {
  return issues.filter(
    i => i.field === prefix || i.field.startsWith(`${prefix}.`),
  );
}

/** Parses "HH:MM" (24-hour) into minutes after midnight, or null if invalid. */
export function parseResetTime(text: string): number | null {
  const m = /^(\d{1,2}):(\d{2})$/.exec(text.trim());
  if (!m) {
    return null;
  }
  const h = Number(m[1]);
  const min = Number(m[2]);
  return h < 24 && min < 60 ? h * 60 + min : null;
}
