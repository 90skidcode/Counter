import { create } from 'zustand';
import { trackerApi } from '../native/trackerApi';
import type {
  SaveSettingsResult,
  TodaySummary,
  TrackerSettings,
} from '../domain/types';

interface TrackerState {
  summary: TodaySummary | null;
  settings: TrackerSettings | null;
  loading: boolean;
  error: string | null;
  /** Bumped on every native change event so screens with their own queries can refetch. */
  revision: number;

  refresh(): Promise<void>;
  loadSettings(): Promise<void>;
  saveSettings(next: TrackerSettings): Promise<SaveSettingsResult>;
  /** Applies a partial change to the current settings and saves it. */
  updateSettings(
    patch: Partial<TrackerSettings>,
  ): Promise<SaveSettingsResult | null>;
  markChanged(): void;
}

const message = (e: unknown) => (e instanceof Error ? e.message : String(e));

/**
 * UI state only. The source of truth is the Kotlin repository; this store caches the latest
 * snapshot and refetches when Kotlin pushes a change event.
 */
export const useTrackerStore = create<TrackerState>((set, get) => ({
  summary: null,
  settings: null,
  loading: false,
  error: null,
  revision: 0,

  async refresh() {
    set({ loading: true });
    try {
      const summary = await trackerApi.getTodaySummary();
      set({ summary, error: null });
    } catch (e) {
      set({ error: message(e) });
    } finally {
      set({ loading: false });
    }
  },

  async loadSettings() {
    try {
      set({ settings: await trackerApi.getSettings(), error: null });
    } catch (e) {
      set({ error: message(e) });
    }
  },

  async saveSettings(next) {
    const result = await trackerApi.saveSettings(next);
    if (result.saved) {
      set({ settings: result.settings });
    }
    return result;
  },

  async updateSettings(patch) {
    const current = get().settings;
    if (!current) {
      return null;
    }
    return get().saveSettings({ ...current, ...patch });
  },

  markChanged() {
    set(s => ({ revision: s.revision + 1 }));
  },
}));

let subscribed = false;

/** Wires native change events to the store exactly once. */
export function connectTrackerEvents(): () => void {
  if (subscribed) {
    return () => {};
  }
  subscribed = true;
  const subscription = trackerApi.subscribe(() => {
    const store = useTrackerStore.getState();
    store.markChanged();
    store.refresh();
  });
  return () => {
    subscription.remove();
    subscribed = false;
  };
}
