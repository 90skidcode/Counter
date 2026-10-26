import type { ConfidenceCode, MonitoringStatus } from './types';
import { t } from '../i18n';

export function formatDuration(ms: number | null | undefined): string {
  if (ms == null) {
    return t('common.unknown');
  }
  if (ms < 60_000) {
    return ms > 0
      ? t('duration.lessThanMinute')
      : t('duration.minutes', { n: 0 });
  }
  const totalMinutes = Math.round(ms / 60_000);
  const h = Math.floor(totalMinutes / 60);
  const m = totalMinutes % 60;
  if (h === 0) {
    return t('duration.minutes', { n: m });
  }
  return m === 0
    ? t('duration.hours', { n: h })
    : t('duration.hoursMinutes', { h, m });
}

/** Unknown counts are shown as a dash, never as zero. */
export function formatCount(value: number | null | undefined): string {
  return value == null ? '—' : String(value);
}

export function formatMinutesOfDay(minuteOfDay: number): string {
  const h = Math.floor(minuteOfDay / 60);
  const m = minuteOfDay % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export function formatTime(epochMs: number | null | undefined): string {
  if (epochMs == null) {
    return t('common.never');
  }
  const d = new Date(epochMs);
  return `${String(d.getHours()).padStart(2, '0')}:${String(
    d.getMinutes(),
  ).padStart(2, '0')}`;
}

export function formatRelative(
  epochMs: number | null | undefined,
  now = Date.now(),
): string {
  if (epochMs == null) {
    return t('common.never');
  }
  const diff = Math.max(0, now - epochMs);
  if (diff < 60_000) {
    return t('relative.justNow');
  }
  if (diff < 3_600_000) {
    return t('relative.minutesAgo', { n: Math.floor(diff / 60_000) });
  }
  if (diff < 86_400_000) {
    return t('relative.hoursAgo', { n: Math.floor(diff / 3_600_000) });
  }
  return t('relative.daysAgo', { n: Math.floor(diff / 86_400_000) });
}

export function confidenceLabel(code: ConfidenceCode): string {
  return t(`confidence.${code}.label`);
}

export function confidenceDescription(code: ConfidenceCode): string {
  return t(`confidence.${code}.description`);
}

export function monitoringStatusLabel(status: MonitoringStatus): string {
  return t(`monitoring.${status}`);
}
