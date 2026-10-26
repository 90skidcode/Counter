import React, { useState } from 'react';
import { PermissionsAndroid, Platform } from 'react-native';
import { PermissionCard } from '../components/PermissionCard';
import { Button, Notice, Screen, ToggleRow, Txt } from '../components/ui';
import type { PermissionStatus, SettingsKind } from '../domain/types';
import { useAsyncData } from '../hooks/useAsyncData';
import { t } from '../i18n';
import { trackerApi } from '../native/trackerApi';
import { useTrackerStore } from '../state/trackerStore';

export function PermissionsScreen({
  onboarding = false,
}: {
  onboarding?: boolean;
}) {
  const { data, error, reload } = useAsyncData<PermissionStatus>(
    trackerApi.getPermissionStatus,
  );
  const updateSettings = useTrackerStore(s => s.updateSettings);
  const [consent, setConsent] = useState(false);
  const [consentHint, setConsentHint] = useState(false);

  const open = (kind: SettingsKind) => trackerApi.openSettings(kind);

  const requestNotifications = async () => {
    if (Platform.OS === 'android' && Platform.Version >= 33) {
      const result = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
      );
      if (result === PermissionsAndroid.RESULTS.NEVER_ASK_AGAIN) {
        await open('notifications');
      }
    } else {
      await open('notifications');
    }
    reload();
  };

  return (
    <Screen>
      <Txt>{t('permissions.intro')}</Txt>
      {error ? (
        <Notice
          tone="error"
          text={error}
          action={{ title: t('common.retry'), onPress: reload }}
        />
      ) : null}
      {data ? (
        <>
          <PermissionCard
            title={t('permissions.usage.title')}
            status={data.usageAccess}
            purpose={t('permissions.usage.purpose')}
            without={t('permissions.usage.without')}
          >
            {data.usageAccess !== 'granted' ? (
              <Button
                kind="secondary"
                title={t('common.openSettings')}
                onPress={() => open('usageAccess')}
              />
            ) : null}
          </PermissionCard>

          <PermissionCard
            title={t('permissions.accessibility.title')}
            status={data.accessibility}
            purpose={t('permissions.accessibility.purpose')}
            without={t('permissions.accessibility.without')}
          >
            <Txt variant="caption" muted>
              {t('permissions.accessibility.notStored')}
            </Txt>
            {data.accessibility !== 'granted' ? (
              <>
                <ToggleRow
                  label={t('permissions.accessibility.consent')}
                  value={consent}
                  onValueChange={v => {
                    setConsent(v);
                    setConsentHint(false);
                  }}
                />
                {consentHint ? (
                  <Notice
                    tone="warning"
                    text={t('permissions.accessibility.consentRequired')}
                  />
                ) : null}
                <Button
                  kind="secondary"
                  title={t('common.openSettings')}
                  onPress={() =>
                    consent ? open('accessibility') : setConsentHint(true)
                  }
                />
              </>
            ) : (
              <Button
                kind="secondary"
                title={t('common.openSettings')}
                onPress={() => open('accessibility')}
              />
            )}
          </PermissionCard>

          <PermissionCard
            title={t('permissions.notifications.title')}
            status={data.notifications}
            purpose={t('permissions.notifications.purpose')}
            without={t('permissions.notifications.without')}
          >
            {data.notifications !== 'granted' ? (
              <Button
                kind="secondary"
                title={t('permissions.notifications.request')}
                onPress={requestNotifications}
              />
            ) : null}
          </PermissionCard>

          <PermissionCard
            title={t('permissions.overlay.title')}
            status={data.overlay}
            purpose={t('permissions.overlay.purpose')}
          />
          <PermissionCard
            title={t('permissions.vpn.title')}
            status={data.vpn}
            purpose={t('permissions.vpn.purpose')}
          />
        </>
      ) : null}
      {onboarding ? (
        <Button
          title={t('onboarding.finish')}
          onPress={() => updateSettings({ onboardingCompleted: true })}
        />
      ) : null}
    </Screen>
  );
}
