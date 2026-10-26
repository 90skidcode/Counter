/**
 * Codegen spec for the Kotlin TurboModule (android/app/.../bridge/ShortVideoTrackerModule.kt).
 *
 * Structured payloads are JSON strings; their shapes live in src/domain/types.ts and are parsed
 * and checked in src/native/trackerApi.ts. Keep this file free of imports other than types so
 * codegen can read it.
 */
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  getPermissionStatus(): Promise<string>;
  openSettings(kind: string): Promise<boolean>;

  getTodaySummary(): Promise<string>;
  getPlatforms(): Promise<string>;
  setPlatformEnabled(platformId: string, enabled: boolean): Promise<void>;
  getHistory(fromDay: string, toDay: string): Promise<string>;

  getSettings(): Promise<string>;
  validateSettings(settingsJson: string): Promise<string>;
  saveSettings(settingsJson: string): Promise<string>;

  getBlockTargets(): Promise<string>;
  addBlockTarget(kind: string, value: string): Promise<string>;
  removeBlockTarget(kind: string, value: string): Promise<void>;
  getBlockState(): Promise<string>;
  startOverride(): Promise<string>;
  pauseBlockingForToday(): Promise<void>;
  cancelOverrides(): Promise<void>;

  getDiagnostics(): Promise<string>;
  getStoredDataSummary(): Promise<string>;
  exportReport(fromDay: string, toDay: string): Promise<string>;
  clearToday(): Promise<void>;
  clearAllHistory(): Promise<void>;
  deleteAllData(): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('ShortVideoTracker');
