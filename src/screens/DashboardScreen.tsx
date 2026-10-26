import React, { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { ConfidenceBadge } from '../components/ConfidenceBadge';
import {
  Card,
  KeyValue,
  Notice,
  ProgressBar,
  Row,
  Screen,
  SectionTitle,
  Txt,
  styles as uiStyles,
} from '../components/ui';
import {
  formatCount,
  formatDuration,
  formatRelative,
  formatTime,
  monitoringStatusLabel,
} from '../domain/format';
import { buildDashboard } from '../domain/summary';
import type { TodaySummary } from '../domain/types';
import { t } from '../i18n';
import { useTrackerStore } from '../state/trackerStore';
import { useTheme } from '../theme';

function blockingText(summary: TodaySummary): string {
  const b = summary.block;
  if (!b.blockingEnabled) {
    return t('dashboard.blockingOff');
  }
  if (b.suspension) {
    return t(`dashboard.blockingSuspended.${b.suspension}`, {
      time: formatTime(b.overrideUntil),
    });
  }
  if (b.enforcedCount > 0) {
    return t('dashboard.blockingActive', {
      n: b.enforcedCount,
      time: formatTime(b.resetsAt),
    });
  }
  return t('dashboard.blockingIdle');
}

export function DashboardScreen({
  onOpenPermissions,
}: {
  onOpenPermissions: () => void;
}) {
  const { palette } = useTheme();
  const { summary, loading, error, refresh, settings, updateSettings } =
    useTrackerStore();

  useEffect(() => {
    refresh();
  }, [refresh]);

  if (!summary) {
    return (
      <Screen refreshing={loading} onRefresh={refresh}>
        {error ? (
          <Notice
            tone="error"
            text={error}
            action={{ title: t('common.retry'), onPress: refresh }}
          />
        ) : (
          <Txt muted>{t('common.loading')}</Txt>
        )}
      </Screen>
    );
  }

  const model = buildDashboard(summary);
  const global = model.global;
  const needsSetup =
    summary.permissions.accessibility !== 'granted' ||
    summary.permissions.usageAccess !== 'granted';

  return (
    <Screen refreshing={loading} onRefresh={refresh}>
      {!summary.monitoringEnabled ? (
        <Notice tone="warning" text={t('dashboard.monitoringPaused')} />
      ) : null}
      {needsSetup ? (
        <Notice
          tone="warning"
          text={t('dashboard.noCounts')}
          action={{
            title: t('dashboard.fixPermissions'),
            onPress: onOpenPermissions,
          }}
        />
      ) : null}

      <Card>
        <Row style={local.top}>
          <View style={uiStyles.flex}>
            <Txt variant="label" muted>
              {t('dashboard.verified')}
            </Txt>
            <Txt variant="number" color={palette.verified}>
              {model.countsUnavailable ? '—' : model.verified}
            </Txt>
          </View>
          <View style={uiStyles.flex}>
            <Txt variant="label" muted>
              {t('dashboard.estimated')}
            </Txt>
            <Txt variant="number" color={palette.estimated}>
              {model.countsUnavailable ? '—' : model.estimated}
            </Txt>
          </View>
        </Row>

        {global?.maxVideos ? (
          <View style={local.limit}>
            <ProgressBar
              label={t('dashboard.limit')}
              progress={model.progress ?? 0}
              color={
                global.videoLimitReached ? palette.danger : palette.primary
              }
            />
            <KeyValue
              label={t('dashboard.limit')}
              value={String(global.maxVideos)}
            />
            <KeyValue
              label={t('dashboard.remaining')}
              value={formatCount(global.remainingVideos)}
            />
            <Txt variant="caption" muted>
              {global.enforcedIncludesEstimates
                ? t('dashboard.remainingBasisAll')
                : t('dashboard.remainingBasisVerified')}
            </Txt>
            {global.videoLimitReached || global.timeLimitReached ? (
              <Txt color={palette.danger}>{t('dashboard.limitReached')}</Txt>
            ) : null}
          </View>
        ) : (
          <KeyValue label={t('dashboard.limit')} value={t('common.noLimit')} />
        )}
      </Card>

      {global &&
      settings &&
      !global.enforcedIncludesEstimates &&
      global.estimatedVideos > 0 ? (
        <Notice
          text={t('dashboard.estimatesExcluded', { n: global.estimatedVideos })}
          action={{
            title: t('dashboard.countEstimates'),
            onPress: async () => {
              await updateSettings({
                limits: { ...settings.limits, enforceEstimatedCounts: true },
              });
              await refresh();
            },
          }}
        />
      ) : null}

      <Card>
        <KeyValue
          label={t('dashboard.time')}
          value={formatDuration(model.activeMillis)}
        />
        <Txt variant="caption" muted>
          {t('dashboard.timeNote')}
        </Txt>
        <KeyValue
          label={t('dashboard.coverage')}
          value={t('dashboard.coverageValue', {
            active: model.activePlatforms,
            total: model.monitoredPlatforms,
          })}
        />
        <KeyValue
          label={t('dashboard.lastDetection')}
          value={formatRelative(model.lastDetectedAt)}
        />
        <KeyValue
          label={t('dashboard.blocking')}
          value={blockingText(summary)}
        />
        {summary.resetsAt ? (
          <Txt variant="caption" muted>
            {t('dashboard.resetAt', { time: formatTime(summary.resetsAt) })}
          </Txt>
        ) : null}
      </Card>

      <SectionTitle>{t('dashboard.byPlatform')}</SectionTitle>
      {summary.platforms
        .filter(p => p.enabled)
        .map(p => {
          const unknown = p.verifiedVideos == null && p.estimatedVideos == null;
          return (
            <Card key={p.id}>
              <Row>
                <Txt variant="heading" style={uiStyles.flex}>
                  {p.displayName}
                </Txt>
                <ConfidenceBadge code={p.effectiveLevel} />
              </Row>
              <KeyValue
                label={t('platforms.videos')}
                value={
                  unknown
                    ? '—'
                    : `${p.verifiedVideos ?? 0} ${t(
                        'history.verified',
                      ).toLowerCase()} · ${p.estimatedVideos ?? 0} ${t(
                        'history.estimated',
                      ).toLowerCase()}`
                }
              />
              <KeyValue
                label={t('platforms.time')}
                value={formatDuration(p.activeMillis)}
              />
              <KeyValue
                label={t('platforms.status')}
                value={monitoringStatusLabel(p.monitoringStatus)}
              />
            </Card>
          );
        })}
    </Screen>
  );
}

const local = StyleSheet.create({
  top: { alignItems: 'flex-start' },
  limit: { gap: 6 },
});
