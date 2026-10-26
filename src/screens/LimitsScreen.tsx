import React, { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Field,
  Notice,
  Screen,
  SectionTitle,
  ToggleRow,
  Txt,
} from '../components/ui';
import { formatMinutesOfDay } from '../domain/format';
import {
  issuesFor,
  parseMilestones,
  parseOptionalInt,
  parseResetTime,
} from '../domain/settingsDraft';
import type { SettingsIssue, TrackerSettings } from '../domain/types';
import { t } from '../i18n';
import { useTrackerStore } from '../state/trackerStore';

interface Draft {
  globalVideos: string;
  globalMinutes: string;
  perPlatform: Record<string, { videos: string; minutes: string }>;
  milestones: string;
  approaching: string;
  repeat: string;
  enforceEstimated: boolean;
  reset: string;
}

const str = (n: number | null | undefined) => (n == null ? '' : String(n));

function toDraft(s: TrackerSettings, platformIds: string[]): Draft {
  return {
    globalVideos: str(s.limits.global.maxVideos),
    globalMinutes: str(s.limits.global.maxMinutes),
    perPlatform: Object.fromEntries(
      platformIds.map(id => [
        id,
        {
          videos: str(s.limits.perPlatform[id]?.maxVideos),
          minutes: str(s.limits.perPlatform[id]?.maxMinutes),
        },
      ]),
    ),
    milestones: s.limits.warningMilestones.join(', '),
    approaching: str(s.limits.approachingPercent),
    repeat: str(s.limits.repeatEveryVideosAfterLimit),
    enforceEstimated: s.limits.enforceEstimatedCounts,
    reset: formatMinutesOfDay(s.resetMinuteOfDay),
  };
}

/** Converts the draft into settings, collecting local parse errors per field. */
function fromDraft(
  base: TrackerSettings,
  d: Draft,
): { settings: TrackerSettings; errors: Record<string, string> } {
  const errors: Record<string, string> = {};
  const num = (field: string, text: string) => {
    const v = parseOptionalInt(text);
    if (v === 'invalid') {
      errors[field] = t('limits.invalidNumber');
      return null;
    }
    return v;
  };
  const perPlatform: TrackerSettings['limits']['perPlatform'] = {};
  for (const [id, v] of Object.entries(d.perPlatform)) {
    const rule = {
      maxVideos: num(`pp.${id}.videos`, v.videos),
      maxMinutes: num(`pp.${id}.minutes`, v.minutes),
    };
    if (rule.maxVideos != null || rule.maxMinutes != null) {
      perPlatform[id] = rule;
    }
  }
  const milestones = parseMilestones(d.milestones);
  if (milestones.invalid.length) {
    errors.milestones = t('limits.invalidNumber');
  }
  const reset = parseResetTime(d.reset);
  if (reset == null) {
    errors.reset = 'HH:MM';
  }
  return {
    errors,
    settings: {
      ...base,
      resetMinuteOfDay: reset ?? base.resetMinuteOfDay,
      limits: {
        global: {
          maxVideos: num('globalVideos', d.globalVideos),
          maxMinutes: num('globalMinutes', d.globalMinutes),
        },
        perPlatform,
        warningMilestones: milestones.values,
        approachingPercent: num('approaching', d.approaching),
        repeatEveryVideosAfterLimit: num('repeat', d.repeat),
        enforceEstimatedCounts: d.enforceEstimated,
      },
    },
  };
}

export function LimitsScreen() {
  const { settings, summary, loadSettings, saveSettings } = useTrackerStore();
  const platforms = (summary?.platforms ?? []).filter(p => p.enabled);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [issues, setIssues] = useState<SettingsIssue[]>([]);
  const [status, setStatus] = useState<string | null>(null);

  useEffect(() => {
    if (!settings) {
      loadSettings();
    }
  }, [settings, loadSettings]);

  const platformKey = platforms.map(p => p.id).join(',');
  useEffect(() => {
    if (settings) {
      setDraft(toDraft(settings, platformKey ? platformKey.split(',') : []));
    }
  }, [settings, platformKey]);

  if (!settings || !draft) {
    return (
      <Screen>
        <Txt muted>{t('common.loading')}</Txt>
      </Screen>
    );
  }

  const set = (patch: Partial<Draft>) => {
    setDraft({ ...draft, ...patch });
    setStatus(null);
  };

  const save = async () => {
    const { settings: next, errors: local } = fromDraft(settings, draft);
    setErrors(local);
    if (Object.keys(local).length) {
      setStatus(t('limits.notSaved'));
      return;
    }
    const result = await saveSettings(next);
    setIssues(result.issues);
    setStatus(result.saved ? t('limits.saved') : t('limits.notSaved'));
  };

  const issueText = (prefix: string) =>
    issuesFor(issues, prefix)
      .map(i => i.message)
      .join(' ') || null;

  return (
    <Screen>
      <SectionTitle>{t('limits.global')}</SectionTitle>
      <Card>
        <Field
          label={t('limits.videoLimit')}
          value={draft.globalVideos}
          placeholder={t('common.noLimit')}
          onChangeText={v => set({ globalVideos: v })}
          error={errors.globalVideos ?? issueText('limits.global.maxVideos')}
        />
        <Field
          label={t('limits.timeLimit')}
          value={draft.globalMinutes}
          placeholder={t('common.noLimit')}
          onChangeText={v => set({ globalMinutes: v })}
          error={errors.globalMinutes ?? issueText('limits.global.maxMinutes')}
        />
        <ToggleRow
          label={t('limits.enforceEstimated')}
          help={t('limits.enforceEstimatedHelp')}
          value={draft.enforceEstimated}
          onValueChange={v => set({ enforceEstimated: v })}
        />
        <Field
          label={t('limits.reset')}
          value={draft.reset}
          keyboardType="default"
          onChangeText={v => set({ reset: v })}
          error={errors.reset ?? issueText('resetMinuteOfDay')}
        />
      </Card>

      <SectionTitle>{t('limits.warnings')}</SectionTitle>
      <Card>
        <Field
          label={t('limits.milestones')}
          help={t('limits.milestonesHelp')}
          value={draft.milestones}
          keyboardType="default"
          onChangeText={v => set({ milestones: v })}
          error={errors.milestones ?? issueText('limits.warningMilestones')}
        />
        <Field
          label={t('limits.approaching')}
          value={draft.approaching}
          placeholder={t('common.off')}
          onChangeText={v => set({ approaching: v })}
          error={errors.approaching ?? issueText('limits.approachingPercent')}
        />
        <Field
          label={t('limits.repeat')}
          value={draft.repeat}
          placeholder={t('common.off')}
          onChangeText={v => set({ repeat: v })}
          error={
            errors.repeat ?? issueText('limits.repeatEveryVideosAfterLimit')
          }
        />
      </Card>

      <SectionTitle>{t('limits.perPlatform')}</SectionTitle>
      <Txt variant="caption" muted>
        {t('limits.perPlatformHelp')}
      </Txt>
      {platforms.map(p => {
        const v = draft.perPlatform[p.id] ?? { videos: '', minutes: '' };
        const update = (patch: Partial<typeof v>) =>
          set({
            perPlatform: { ...draft.perPlatform, [p.id]: { ...v, ...patch } },
          });
        return (
          <Card key={p.id}>
            <Txt variant="heading">{p.displayName}</Txt>
            <Field
              label={t('limits.videoLimit')}
              value={v.videos}
              placeholder={t('common.noLimit')}
              onChangeText={x => update({ videos: x })}
              error={
                errors[`pp.${p.id}.videos`] ??
                issueText(`limits.perPlatform.${p.id}`)
              }
            />
            <Field
              label={t('limits.timeLimit')}
              value={v.minutes}
              placeholder={t('common.noLimit')}
              onChangeText={x => update({ minutes: x })}
              error={errors[`pp.${p.id}.minutes`]}
            />
          </Card>
        );
      })}

      {issues
        .filter(i => i.severity === 'warning')
        .map(i => (
          <Notice key={i.field + i.message} tone="warning" text={i.message} />
        ))}
      <Txt variant="caption" muted>
        {t('limits.blockingNote', {
          state: settings.blockingEnabled ? t('common.on') : t('common.off'),
        })}
      </Txt>
      {status ? (
        <Notice
          text={status}
          tone={status === t('limits.saved') ? 'info' : 'error'}
        />
      ) : null}
      <Button title={t('common.save')} onPress={save} />
    </Screen>
  );
}
