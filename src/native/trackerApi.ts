import { DeviceEventEmitter, type EmitterSubscription } from 'react-native';
import Native from './NativeShortVideoTracker';
import type {
  AddBlockTargetResult,
  BlockStateDetails,
  BlockTargetsResponse,
  Diagnostics,
  HistoryResponse,
  OverrideResult,
  PermissionStatus,
  PlatformInfo,
  SaveSettingsResult,
  SettingsKind,
  StoredDataSummary,
  TodaySummary,
  TrackerSettings,
} from '../domain/types';

export const TRACKER_CHANGED_EVENT = 'ShortVideoTrackerChanged';

export class TrackerPayloadError extends Error {}

/** Parses a JSON payload from Kotlin and checks the keys the UI relies on. */
export function parsePayload<T>(json: string, requiredKeys: string[] = []): T {
  let value: unknown;
  try {
    value = JSON.parse(json);
  } catch {
    throw new TrackerPayloadError('Native module returned invalid JSON');
  }
  if (typeof value !== 'object' || value === null) {
    throw new TrackerPayloadError(
      'Native module returned a non-object payload',
    );
  }
  for (const key of requiredKeys) {
    if (!(key in value)) {
      throw new TrackerPayloadError(`Native payload is missing "${key}"`);
    }
  }
  return value as T;
}

/** Typed facade over the TurboModule. All data stays on the device. */
export const trackerApi = {
  getPermissionStatus: async () =>
    parsePayload<PermissionStatus>(await Native.getPermissionStatus(), [
      'usageAccess',
      'accessibility',
      'notifications',
    ]),
  openSettings: (kind: SettingsKind) => Native.openSettings(kind),

  getTodaySummary: async () =>
    parsePayload<TodaySummary>(await Native.getTodaySummary(), [
      'dayKey',
      'platforms',
      'limits',
      'block',
      'permissions',
    ]),
  getPlatforms: async () =>
    parsePayload<PlatformInfo[]>(await Native.getPlatforms()),
  setPlatformEnabled: (platformId: string, enabled: boolean) =>
    Native.setPlatformEnabled(platformId, enabled),
  getHistory: async (fromDay: string, toDay: string) =>
    parsePayload<HistoryResponse>(await Native.getHistory(fromDay, toDay), [
      'rows',
    ]),

  getSettings: async () =>
    parsePayload<TrackerSettings>(await Native.getSettings(), ['limits']),
  validateSettings: async (settings: TrackerSettings) =>
    parsePayload<SaveSettingsResult>(
      await Native.validateSettings(JSON.stringify(settings)),
      ['settings', 'issues'],
    ),
  saveSettings: async (settings: TrackerSettings) =>
    parsePayload<SaveSettingsResult>(
      await Native.saveSettings(JSON.stringify(settings)),
      ['settings', 'issues', 'saved'],
    ),

  getBlockTargets: async () =>
    parsePayload<BlockTargetsResponse>(await Native.getBlockTargets(), [
      'apps',
      'domains',
    ]),
  addBlockTarget: async (kind: 'app' | 'domain', value: string) =>
    parsePayload<AddBlockTargetResult>(
      await Native.addBlockTarget(kind, value),
      ['ok'],
    ),
  removeBlockTarget: (kind: 'app' | 'domain', value: string) =>
    Native.removeBlockTarget(kind, value),
  getBlockState: async () =>
    parsePayload<BlockStateDetails>(await Native.getBlockState(), [
      'triggered',
      'log',
    ]),
  startOverride: async () =>
    parsePayload<OverrideResult>(await Native.startOverride(), ['ok']),
  pauseBlockingForToday: () => Native.pauseBlockingForToday(),
  cancelOverrides: () => Native.cancelOverrides(),

  getDiagnostics: async () =>
    parsePayload<Diagnostics>(await Native.getDiagnostics(), [
      'platforms',
      'counters',
      'recentEvents',
    ]),
  getStoredDataSummary: async () =>
    parsePayload<StoredDataSummary>(await Native.getStoredDataSummary()),
  exportReport: (fromDay: string, toDay: string) =>
    Native.exportReport(fromDay, toDay),
  clearToday: () => Native.clearToday(),
  clearAllHistory: () => Native.clearAllHistory(),
  deleteAllData: () => Native.deleteAllData(),

  /** Pushed by Kotlin whenever counts, limits or block state change. */
  subscribe(listener: (reason: string) => void): EmitterSubscription {
    return DeviceEventEmitter.addListener(TRACKER_CHANGED_EVENT, listener);
  },
};
