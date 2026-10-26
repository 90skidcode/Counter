import React from 'react';
import { ConfidenceBadge } from '../components/ConfidenceBadge';
import {
  Card,
  KeyValue,
  Notice,
  Row,
  Screen,
  SectionTitle,
  Txt,
  styles as uiStyles,
} from '../components/ui';
import {
  formatRelative,
  formatTime,
  monitoringStatusLabel,
} from '../domain/format';
import type { Diagnostics } from '../domain/types';
import { useAsyncData } from '../hooks/useAsyncData';
import { t } from '../i18n';
import { trackerApi } from '../native/trackerApi';
import { useTheme } from '../theme';

export function DiagnosticsScreen() {
  const { palette } = useTheme();
  const { data, error, loading, reload } = useAsyncData<Diagnostics>(
    trackerApi.getDiagnostics,
  );
  const names = new Map(
    (data?.platforms ?? []).map(p => [p.id, p.displayName]),
  );

  return (
    <Screen refreshing={loading} onRefresh={reload}>
      {error ? (
        <Notice
          tone="error"
          text={error}
          action={{ title: t('common.retry'), onPress: reload }}
        />
      ) : null}
      {data ? (
        <>
          <Card>
            <KeyValue
              label={t('diagnostics.service')}
              value={
                data.serviceConnected
                  ? t('diagnostics.connected')
                  : t('diagnostics.disconnected')
              }
              valueColor={
                data.serviceConnected ? palette.success : palette.warning
              }
            />
            <KeyValue
              label={t('permissions.usage.title')}
              value={t(`permissions.status.${data.permissions.usageAccess}`)}
            />
            <KeyValue
              label={t('permissions.accessibility.title')}
              value={t(`permissions.status.${data.permissions.accessibility}`)}
            />
            <KeyValue
              label={t('permissions.notifications.title')}
              value={t(`permissions.status.${data.permissions.notifications}`)}
            />
            <Txt variant="caption" muted>
              {t('diagnostics.database', { n: data.databaseVersion })}
            </Txt>
          </Card>

          <SectionTitle>{t('diagnostics.detectors')}</SectionTitle>
          {data.platforms.map(p => {
            const counters = data.counters.filter(c => c.platformId === p.id);
            return (
              <Card key={p.id}>
                <Row>
                  <Txt variant="heading" style={uiStyles.flex}>
                    {p.displayName}
                  </Txt>
                  <ConfidenceBadge code={p.effectiveLevel} />
                </Row>
                <KeyValue
                  label={t('platforms.status')}
                  value={monitoringStatusLabel(p.monitoringStatus)}
                />
                <KeyValue
                  label="Detector"
                  value={`${p.detectorType} v${p.detectorVersion}`}
                />
                <KeyValue
                  label={t('platforms.tested')}
                  value={p.testedAppVersions}
                />
                <KeyValue
                  label={t('diagnostics.lastSignal')}
                  value={formatRelative(p.lastSignalAt)}
                />
                <KeyValue
                  label={t('platforms.lastEvent')}
                  value={formatRelative(p.lastDetectedAt)}
                />
                {counters.map(c => (
                  <KeyValue
                    key={c.metric}
                    label={t(`diagnostics.metric.${c.metric}`)}
                    value={String(c.value)}
                  />
                ))}
              </Card>
            );
          })}

          <SectionTitle>{t('diagnostics.recent')}</SectionTitle>
          <Card>
            <Txt variant="caption" muted>
              {t('diagnostics.recentNote')}
            </Txt>
            {data.recentEvents.length === 0 ? (
              <Txt muted>{t('diagnostics.noEvents')}</Txt>
            ) : null}
            {data.recentEvents.map((e, i) => (
              <KeyValue
                key={`${e.at}-${i}`}
                label={`${formatTime(e.at)} · ${
                  names.get(e.platformId) ?? e.platformId
                }`}
                value={`${e.confidenceLevel} · ${e.detector}`}
              />
            ))}
          </Card>
        </>
      ) : null}
    </Screen>
  );
}
