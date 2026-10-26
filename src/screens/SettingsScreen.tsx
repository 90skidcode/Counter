import React, { useEffect, useState } from 'react';
import {
  Card,
  Field,
  ListLink,
  Notice,
  Screen,
  Segmented,
  SectionTitle,
  ToggleRow,
  Txt,
} from '../components/ui';
import { formatMinutesOfDay } from '../domain/format';
import { parseResetTime } from '../domain/settingsDraft';
import type {
  FloatingCounterMode,
  SettingsIssue,
  ThemePreference,
  TrackerSettings,
} from '../domain/types';
import { t } from '../i18n';
import { useTrackerStore } from '../state/trackerStore';

const NUMERIC_KEYS = [
  'minViewSeconds',
  'sessionGapMinutes',
  'overrideMinutes',
  'maxOverridesPerDay',
  'retentionDays',
] as const;
type NumericKey = (typeof NUMERIC_KEYS)[number];

const VERSION = '0.1.0';

export function SettingsScreen({
  onOpenDiagnostics,
  onOpenPrivacy,
}: {
  onOpenDiagnostics: () => void;
  onOpenPrivacy: () => void;
}) {
  const { settings, loadSettings, saveSettings } = useTrackerStore();
  const [text, setText] = useState<Record<string, string>>({});
  const [issues, setIssues] = useState<SettingsIssue[]>([]);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!settings) {
      loadSettings();
    }
  }, [settings, loadSettings]);

  useEffect(() => {
    if (settings) {
      setText({
        minViewSeconds: String(settings.minViewSeconds),
        sessionGapMinutes: String(settings.sessionGapMinutes),
        overrideMinutes: String(settings.overrideMinutes),
        maxOverridesPerDay: String(settings.maxOverridesPerDay),
        retentionDays: String(settings.retentionDays),
        reset: formatMinutesOfDay(settings.resetMinuteOfDay),
      });
    }
  }, [settings]);

  if (!settings) {
    return (
      <Screen>
        <Txt muted>{t('common.loading')}</Txt>
      </Screen>
    );
  }

  const apply = async (patch: Partial<TrackerSettings>) => {
    const result = await saveSettings({ ...settings, ...patch });
    setIssues(result.issues);
    setSaved(result.saved);
  };

  /** Saves every changed numeric field in one call, so no change overwrites another. */
  const commitNumbers = () => {
    const patch: Partial<TrackerSettings> = {};
    for (const key of NUMERIC_KEYS) {
      const n = Number(text[key]);
      if (
        text[key] !== undefined &&
        Number.isInteger(n) &&
        n !== settings[key]
      ) {
        patch[key] = n;
      }
    }
    if (Object.keys(patch).length) {
      apply(patch);
    }
  };

  const numberField = (key: NumericKey, label: string, help?: string) => (
    <Field
      label={label}
      help={help}
      value={text[key] ?? ''}
      onChangeText={v => setText({ ...text, [key]: v })}
      error={issues.find(i => i.field === key)?.message}
    />
  );

  return (
    <Screen>
      {saved ? <Notice text={t('settings.saved')} /> : null}
      <SectionTitle>{t('settings.general')}</SectionTitle>
      <Card>
        <Txt variant="label">{t('settings.theme')}</Txt>
        <Segmented<ThemePreference>
          value={settings.theme}
          onChange={v => apply({ theme: v })}
          options={(['system', 'light', 'dark'] as const).map(v => ({
            value: v,
            label: t(`settings.theme.${v}`),
          }))}
        />
        <Txt variant="label">{t('settings.counter')}</Txt>
        <Segmented<FloatingCounterMode>
          value={settings.floatingCounter}
          onChange={v => apply({ floatingCounter: v })}
          options={(['off', 'monitored_apps', 'all_apps'] as const).map(v => ({
            value: v,
            label: t(`settings.counter.${v}`),
          }))}
        />
        <Txt variant="caption" muted>
          {t('settings.counterHelp')}
        </Txt>
        <ToggleRow
          label={t('settings.monitoring')}
          help={t('settings.monitoringHelp')}
          value={settings.monitoringEnabled}
          onValueChange={v => apply({ monitoringEnabled: v })}
        />
        <ToggleRow
          label={t('settings.warnings')}
          value={settings.warningsEnabled}
          onValueChange={v => apply({ warningsEnabled: v })}
        />
        <Field
          label={t('limits.reset')}
          value={text.reset ?? ''}
          keyboardType="default"
          onChangeText={v => {
            setText({ ...text, reset: v });
            const m = parseResetTime(v);
            if (m != null && m !== settings.resetMinuteOfDay) {
              apply({ resetMinuteOfDay: m });
            }
          }}
        />
      </Card>

      <SectionTitle>{t('settings.detection')}</SectionTitle>
      <Card>
        {numberField(
          'minViewSeconds',
          t('settings.minView'),
          t('settings.minViewHelp'),
        )}
        {numberField(
          'sessionGapMinutes',
          t('settings.sessionGap'),
          t('settings.sessionGapHelp'),
        )}
        <ListLink label={t('more.diagnostics')} onPress={onOpenDiagnostics} />
      </Card>

      <SectionTitle>{t('settings.blockingSection')}</SectionTitle>
      <Card>
        <ToggleRow
          label={t('blocking.enable')}
          value={settings.blockingEnabled}
          onValueChange={v => apply({ blockingEnabled: v })}
        />
        {numberField('overrideMinutes', t('settings.overrideMinutes'))}
        {numberField('maxOverridesPerDay', t('settings.maxOverrides'))}
      </Card>

      <SectionTitle>{t('settings.data')}</SectionTitle>
      <Card>
        {numberField('retentionDays', t('settings.retention'))}
        <ListLink label={t('more.privacy')} onPress={onOpenPrivacy} />
      </Card>

      <Txt variant="caption" muted>
        {t('settings.aboutText', { version: VERSION })}
      </Txt>
      <SaveOnLeave onSave={commitNumbers} />
    </Screen>
  );
}

/** Numeric fields commit when the user taps save or leaves the screen. */
function SaveOnLeave({ onSave }: { onSave: () => void }) {
  const saveRef = React.useRef(onSave);
  saveRef.current = onSave;
  useEffect(() => () => saveRef.current(), []);
  return <ListLink label={t('common.save')} onPress={onSave} />;
}
