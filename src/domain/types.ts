/**
 * Shapes of the JSON payloads produced by android/app/.../bridge/TrackerJson.kt and
 * ShortVideoTrackerModule.kt. Keep both sides in sync.
 */

/** A = verified, B = estimated, C = app/site time only, D = unavailable. */
export type ConfidenceCode = 'A' | 'B' | 'C' | 'D';

export type PermissionState =
  | 'granted'
  | 'not_granted'
  | 'enabled_not_running'
  | 'not_used'
  | 'requires_accessibility';

export interface PermissionStatus {
  usageAccess: PermissionState;
  accessibility: PermissionState;
  notifications: PermissionState;
  vpn: PermissionState;
  overlay: PermissionState;
}

export type SettingsKind =
  | 'usageAccess'
  | 'accessibility'
  | 'notifications'
  | 'appDetails';

export type MonitoringStatus =
  | 'active'
  | 'needs_accessibility'
  | 'needs_usage_access'
  | 'usage_only'
  | 'disabled'
  | 'paused'
  | 'not_installed';

export type TimeSource = 'usage_stats' | 'web_site_timer';

export interface PlatformInfo {
  id: string;
  displayName: string;
  packageNames: string[];
  platformType: 'app' | 'browser';
  detectorType: string;
  detectorVersion: number;
  capabilityLevel: ConfidenceCode;
  effectiveLevel: ConfidenceCode;
  enabled: boolean;
  installed: boolean;
  monitoringStatus: MonitoringStatus;
  lastDetectedAt: number | null;
  lastSignalAt: number | null;
  canDistinguishShortsFromRegular: boolean;
  measuresBackgroundPlayback: boolean;
  blockGranularity: 'whole_app' | 'whole_domain';
  testedAppVersions: string;
  knownLimitations: string[];
  timeSource: TimeSource;
}

export interface PlatformToday {
  id: string;
  displayName: string;
  monitoringStatus: MonitoringStatus;
  effectiveLevel: ConfidenceCode;
  capabilityLevel: ConfidenceCode;
  /** null means unknown, which is different from zero. */
  verifiedVideos: number | null;
  estimatedVideos: number | null;
  activeMillis: number | null;
  timeSource: TimeSource;
  lastDetectedAt: number | null;
  enabled: boolean;
}

export interface LimitStatus {
  scope: string;
  platformId: string | null;
  maxVideos: number | null;
  maxMinutes: number | null;
  verifiedVideos: number;
  estimatedVideos: number;
  enforcedVideos: number;
  enforcedIncludesEstimates: boolean;
  remainingVideos: number | null;
  activeMinutes: number | null;
  videoLimitReached: boolean;
  timeLimitReached: boolean;
}

export type BlockSuspension = 'disabled' | 'override' | 'paused_for_day';

export interface TriggeredBlock {
  kind: 'app' | 'domain';
  value: string;
  platformId: string | null;
  reasons: string[];
}

export interface BlockStateInfo {
  blockingEnabled: boolean;
  triggered: TriggeredBlock[];
  enforcedCount: number;
  suspension: BlockSuspension | null;
  overrideUntil: number | null;
  resetsAt: number | null;
}

export interface BlockLogEntry {
  type: string;
  at: number;
  until?: number | null;
  cancelledAt?: number | null;
  target?: string;
  occurrences?: number;
}

export interface BlockStateDetails extends BlockStateInfo {
  overrideMinutes: number;
  overridesUsedToday: number;
  maxOverridesPerDay: number;
  log: BlockLogEntry[];
}

export interface TodaySummary {
  generatedAt: number;
  dayKey: string;
  resetsAt: number | null;
  permissions: PermissionStatus;
  monitoringEnabled: boolean;
  platforms: PlatformToday[];
  limits: LimitStatus[];
  block: BlockStateInfo;
}

export interface LimitRule {
  maxVideos: number | null;
  maxMinutes: number | null;
}

export interface LimitSettings {
  global: LimitRule;
  perPlatform: Record<string, LimitRule>;
  warningMilestones: number[];
  approachingPercent: number | null;
  repeatEveryVideosAfterLimit: number | null;
  enforceEstimatedCounts: boolean;
}

export type FloatingCounterMode = 'off' | 'monitored_apps' | 'all_apps';
export type ThemePreference = 'system' | 'light' | 'dark';

export interface TrackerSettings {
  onboardingCompleted: boolean;
  monitoringEnabled: boolean;
  warningsEnabled: boolean;
  blockingEnabled: boolean;
  floatingCounter: FloatingCounterMode;
  resetMinuteOfDay: number;
  minViewSeconds: number;
  sessionGapMinutes: number;
  overrideMinutes: number;
  maxOverridesPerDay: number;
  retentionDays: number;
  theme: ThemePreference;
  limits: LimitSettings;
}

export interface SettingsIssue {
  field: string;
  severity: 'error' | 'warning';
  message: string;
}

export interface SaveSettingsResult {
  settings: TrackerSettings;
  issues: SettingsIssue[];
  saved: boolean;
}

export interface HistoryRow {
  dayKey: string;
  platformId: string;
  verifiedVideos: number;
  estimatedVideos: number;
  activeMillis: number | null;
  timeSource: TimeSource;
}

export interface HistoryResponse {
  fromDay: string;
  toDay: string;
  firstRecordedDay: string | null;
  rows: HistoryRow[];
}

export interface BlockTargetOption {
  kind: 'app' | 'domain';
  value: string;
  label: string;
  platformId: string | null;
  selected: boolean;
}

export interface BlockTargetsResponse {
  apps: BlockTargetOption[];
  domains: BlockTargetOption[];
  supportedBrowsers: string[];
}

export interface AddBlockTargetResult {
  ok: boolean;
  value?: string;
  error?: 'not_supported' | 'invalid_domain' | 'invalid_kind' | 'essential_app';
}

export interface OverrideResult {
  ok: boolean;
  error: 'overrides_disabled' | 'override_limit_reached' | null;
}

export interface DiagnosticCounter {
  platformId: string;
  metric: string;
  value: number;
}

export interface DiagnosticEvent {
  platformId: string;
  at: number;
  confidenceLevel: ConfidenceCode;
  evidenceType: string;
  detector: string;
}

export interface Diagnostics {
  dayKey: string;
  permissions: PermissionStatus;
  serviceConnected: boolean;
  databaseVersion: number;
  platforms: PlatformInfo[];
  counters: DiagnosticCounter[];
  recentEvents: DiagnosticEvent[];
}

export interface StoredDataSummary {
  videoEvents: number;
  activityRows: number;
  blockTargets: number;
  firstRecordedDay: string | null;
  retentionDays: number;
}
