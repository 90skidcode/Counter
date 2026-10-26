import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Card, Screen, Txt } from '../components/ui';
import { t } from '../i18n';
import { useTrackerStore } from '../state/trackerStore';

const SECTIONS = [
  ['onboarding.measuresTitle', 'onboarding.measures'],
  ['onboarding.privacyTitle', 'onboarding.privacy'],
  ['onboarding.limitsTitle', 'onboarding.limits'],
  ['onboarding.permissionsTitle', 'onboarding.permissions'],
  ['onboarding.blockingTitle', 'onboarding.blocking'],
] as const;

export function OnboardingScreen({ onSetup }: { onSetup: () => void }) {
  const updateSettings = useTrackerStore(s => s.updateSettings);
  return (
    <Screen>
      <Txt variant="title">{t('onboarding.title')}</Txt>
      <Txt>{t('onboarding.intro')}</Txt>
      {SECTIONS.map(([title, body]) => (
        <Card key={title}>
          <Txt variant="heading">{t(title)}</Txt>
          <Txt>{t(body)}</Txt>
        </Card>
      ))}
      <View style={local.actions}>
        <Button title={t('onboarding.continue')} onPress={onSetup} />
        <Button
          kind="secondary"
          title={t('onboarding.skip')}
          onPress={() => updateSettings({ onboardingCompleted: true })}
        />
      </View>
    </Screen>
  );
}

const local = StyleSheet.create({
  actions: { gap: 8 },
});
