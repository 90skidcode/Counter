import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { useTrackerStore } from '../state/trackerStore';

/**
 * Loads data from the native layer, reloading when Kotlin reports a change and when the app
 * returns to the foreground (so permission revocations are noticed).
 */
export function useAsyncData<T>(
  loader: () => Promise<T>,
  deps: unknown[] = [],
) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const revision = useTrackerStore(s => s.revision);
  const loaderRef = useRef(loader);
  loaderRef.current = loader;

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setData(await loaderRef.current());
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reload, revision, ...deps]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        reload();
      }
    });
    return () => sub.remove();
  }, [reload]);

  return { data, error, loading, reload };
}
