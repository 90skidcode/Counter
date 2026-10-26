import React from 'react';
import { ConfidenceBadge } from '../components/ConfidenceBadge';
import {
  Card,
  KeyValue,
  Notice,
  Row,
  Screen,
  ToggleRow,
  Txt,
  styles as uiStyles,
} from '../components/ui';
import {
  confidenceDescription,
  formatCount,
  formatDuration,
  formatRelative,
  monitoringStatusLabel,
} from '../domain/format';
import type { PlatformInfo } from '../domain/types';
import { useAsyncData } from '../hooks/useAsyncData';
import { t } from '../i18n';
import { trackerApi } from '../native/trackerApi';
import { useTrackerStore } from '../state/trackerStore';

export function PlatformsScreen() {
  const { data, error, loading, reload } = useAsyncData<PlatformInfo[]>(
    trackerApi.getPlatforms,
  );
  const summary = useTrackerStore(s => s.summary);
  const refresh = useTrackerStore(s => s.refresh);

  const toggle = async (id: string, enabled: boolean) => {
    await trackerApi.setPlatformEnabled(id, enabled);
    await Promise.all([reload(), refresh()]);
  };

  return (
    <Screen refreshing={loading} onRefresh={reload}>
      <Txt muted>{t('platforms.intro')}</Txt>
      {error ? (
        <Notice
          tone="error"
          text={error}
          action={{ title: t('common.retry'), onPress: reload }}
        />
      ) : null}
      {data?.map(p => {
        const today = summary?.platforms.find(x => x.id === p.id);
        const unknown =
          today?.verifiedVideos == null && today?.estimatedVideos == null;
        return (
          <Card key={p.id}>
            <Row>
              <Txt variant="heading" style={uiStyles.flex}>
                {p.displayName}
              </Txt>
              <ConfidenceBadge code={p.effectiveLevel} />
            </Row>
            <Txt variant="caption" muted>
              {confidenceDescription(p.effectiveLevel)}
            </Txt>
            <ToggleRow
              label={t('platforms.monitor')}
              value={p.enabled}
              onValueChange={v => toggle(p.id, v)}
            />
            <KeyValue
              label={t('platforms.videos')}
              value={
                unknown
                  ? '—'
                  : `${formatCount(today?.verifiedVideos)} ✓ · ${formatCount(
                      today?.estimatedVideos,
                    )} ~`
              }
            />
            <KeyValue
              label={`${t('platforms.time')} (${t(
                `platforms.timeSource.${p.timeSource}`,
              )})`}
              value={formatDuration(today?.activeMillis)}
            />
            <KeyValue
              label={t('platforms.status')}
              value={monitoringStatusLabel(p.monitoringStatus)}
            />
            <KeyValue
              label={t('platforms.lastEvent')}
              value={formatRelative(p.lastDetectedAt)}
            />
            <KeyValue
              label={t('platforms.blocking')}
              value={
                p.blockGranularity === 'whole_app'
                  ? t('platforms.blockWholeApp')
                  : t('platforms.blockWholeDomain')
              }
            />
            <KeyValue
              label={t('platforms.tested')}
              value={p.testedAppVersions}
            />
            <Txt variant="label" muted>
              {t('platforms.limitations')}
            </Txt>
            {p.knownLimitations.map(l => (
              <Txt key={l} variant="caption" muted>
                • {l}
              </Txt>
            ))}
          </Card>
        );
      })}
    </Screen>
  );
}
