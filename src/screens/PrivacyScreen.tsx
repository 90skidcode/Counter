import React, { useState } from 'react';
import { Alert, Share } from 'react-native';
import {
  Button,
  Card,
  Notice,
  Screen,
  SectionTitle,
  ToggleRow,
  Txt,
} from '../components/ui';
import { addDays } from '../domain/dates';
import type { StoredDataSummary } from '../domain/types';
import { useAsyncData } from '../hooks/useAsyncData';
import { t } from '../i18n';
import { trackerApi } from '../native/trackerApi';
import { useTrackerStore } from '../state/trackerStore';

export function PrivacyScreen({
  onOpenPermissions,
}: {
  onOpenPermissions: () => void;
}) {
  const {
    settings,
    summary,
    updateSettings,
    refresh,
    loadSettings,
    markChanged,
  } = useTrackerStore();
  const stored = useAsyncData<StoredDataSummary>(
    trackerApi.getStoredDataSummary,
  );
  const [message, setMessage] = useState<string | null>(null);

  const confirm = (title: string, body: string, action: () => Promise<void>) =>
    Alert.alert(title, body, [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: title,
        style: 'destructive',
        onPress: async () => {
          await action();
          await Promise.all([refresh(), loadSettings(), stored.reload()]);
          markChanged();
          setMessage(t('privacy.deleted'));
        },
      },
    ]);

  const exportReport = async () => {
    const today = summary?.dayKey ?? new Date().toISOString().slice(0, 10);
    const from = stored.data?.firstRecordedDay ?? addDays(today, -30);
    const csv = await trackerApi.exportReport(from, today);
    await Share.share({ title: 'Reel Counter report', message: csv });
  };

  return (
    <Screen refreshing={stored.loading} onRefresh={stored.reload}>
      {message ? <Notice text={message} /> : null}
      <Card>
        <Txt variant="heading">{t('privacy.stored')}</Txt>
        <Txt>{t('privacy.storedList')}</Txt>
        {stored.data ? (
          <>
            <Txt variant="caption" muted>
              {t('privacy.summary', {
                events: stored.data.videoEvents,
                activity: stored.data.activityRows,
                first: stored.data.firstRecordedDay ?? '—',
              })}
            </Txt>
            <Txt variant="caption" muted>
              {t('privacy.retention', { n: stored.data.retentionDays })}
            </Txt>
          </>
        ) : null}
      </Card>
      <Card>
        <Txt variant="heading">{t('privacy.notStored')}</Txt>
        <Txt>{t('privacy.notStoredList')}</Txt>
      </Card>

      <SectionTitle>{t('privacy.controls')}</SectionTitle>
      <Card>
        {settings ? (
          <>
            <ToggleRow
              label={t('settings.monitoring')}
              help={t('settings.monitoringHelp')}
              value={settings.monitoringEnabled}
              onValueChange={v => updateSettings({ monitoringEnabled: v })}
            />
            <ToggleRow
              label={t('settings.blocking')}
              value={settings.blockingEnabled}
              onValueChange={v => updateSettings({ blockingEnabled: v })}
            />
          </>
        ) : null}
        <Button
          kind="secondary"
          title={t('more.permissions')}
          onPress={onOpenPermissions}
        />
        <Button
          kind="secondary"
          title={t('privacy.export')}
          onPress={exportReport}
        />
        <Txt variant="caption" muted>
          {t('privacy.exportNote')}
        </Txt>
      </Card>

      <Card>
        <Button
          kind="danger"
          title={t('privacy.clearToday')}
          onPress={() =>
            confirm(
              t('privacy.clearToday'),
              t('privacy.clearTodayConfirm'),
              trackerApi.clearToday,
            )
          }
        />
        <Button
          kind="danger"
          title={t('privacy.clearAll')}
          onPress={() =>
            confirm(
              t('privacy.clearAll'),
              t('privacy.clearAllConfirm'),
              trackerApi.clearAllHistory,
            )
          }
        />
        <Button
          kind="danger"
          title={t('privacy.deleteAll')}
          onPress={() =>
            confirm(
              t('privacy.deleteAll'),
              t('privacy.deleteAllConfirm'),
              trackerApi.deleteAllData,
            )
          }
        />
      </Card>

      <SectionTitle>{t('privacy.policy')}</SectionTitle>
      <Card>
        <Txt>{t('privacy.policyText')}</Txt>
      </Card>
    </Screen>
  );
}
