import React, { useState } from 'react';
import { Alert } from 'react-native';
import {
  Button,
  Card,
  Field,
  KeyValue,
  Notice,
  Screen,
  SectionTitle,
  ToggleRow,
  Txt,
} from '../components/ui';
import { formatTime } from '../domain/format';
import type {
  BlockStateDetails,
  BlockTargetOption,
  BlockTargetsResponse,
  TriggeredBlock,
} from '../domain/types';
import { useAsyncData } from '../hooks/useAsyncData';
import { t } from '../i18n';
import { trackerApi } from '../native/trackerApi';
import { useTrackerStore } from '../state/trackerStore';

function reasonText(block: TriggeredBlock, names: Map<string, string>): string {
  return block.reasons
    .map(r =>
      r === 'global'
        ? t('blocking.reason.global')
        : t('blocking.reason.platform', {
            name: names.get(r.replace('platform:', '')) ?? r,
          }),
    )
    .join(', ');
}

export function BlockingScreen() {
  const { settings, summary, updateSettings, refresh } = useTrackerStore();
  const targets = useAsyncData<BlockTargetsResponse>(
    trackerApi.getBlockTargets,
  );
  const state = useAsyncData<BlockStateDetails>(trackerApi.getBlockState);
  const [domain, setDomain] = useState('');
  const [domainError, setDomainError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const names = new Map(
    (summary?.platforms ?? []).map(p => [p.id, p.displayName]),
  );

  const reloadAll = async () => {
    await Promise.all([targets.reload(), state.reload(), refresh()]);
  };

  const toggleTarget = async (o: BlockTargetOption, selected: boolean) => {
    if (selected) {
      const r = await trackerApi.addBlockTarget(o.kind, o.value);
      if (!r.ok) {
        setMessage(
          r.error === 'essential_app'
            ? t('blocking.essential')
            : r.error ?? null,
        );
      }
    } else {
      await trackerApi.removeBlockTarget(o.kind, o.value);
    }
    await reloadAll();
  };

  const addDomain = async () => {
    const r = await trackerApi.addBlockTarget('domain', domain);
    if (r.ok) {
      setDomain('');
      setDomainError(null);
      await reloadAll();
    } else {
      setDomainError(t('blocking.invalidDomain'));
    }
  };

  const override = async () => {
    const r = await trackerApi.startOverride();
    setMessage(r.ok ? null : t(`blocking.overrideError.${r.error}`));
    await reloadAll();
  };

  const pause = () =>
    Alert.alert(t('blocking.pause'), t('blocking.pauseConfirm'), [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('common.done'),
        style: 'destructive',
        onPress: async () => {
          await trackerApi.pauseBlockingForToday();
          setMessage(t('blocking.paused'));
          await reloadAll();
        },
      },
    ]);

  const s = state.data;
  const enforced = s && !s.suspension ? s.triggered : [];

  return (
    <Screen refreshing={targets.loading || state.loading} onRefresh={reloadAll}>
      {settings ? (
        <Card>
          <ToggleRow
            label={t('blocking.enable')}
            value={settings.blockingEnabled}
            onValueChange={async v => {
              await updateSettings({ blockingEnabled: v });
              await reloadAll();
            }}
          />
        </Card>
      ) : null}

      <Card>
        <Txt variant="heading">{t('blocking.howTitle')}</Txt>
        <Txt>{t('blocking.how')}</Txt>
        <Txt variant="caption" muted>
          {t('blocking.essential')}
        </Txt>
      </Card>

      {message ? <Notice tone="warning" text={message} /> : null}

      <SectionTitle>{t('blocking.state')}</SectionTitle>
      <Card>
        {!s ? (
          <Txt muted>{t('common.loading')}</Txt>
        ) : enforced.length === 0 ? (
          <Txt>
            {s.suspension === 'paused_for_day'
              ? t('blocking.paused')
              : t('blocking.nothingBlocked')}
          </Txt>
        ) : (
          enforced.map(b => (
            <KeyValue
              key={b.kind + b.value}
              label={b.value}
              value={reasonText(b, names)}
            />
          ))
        )}
        {s && enforced.length > 0 ? (
          <Txt variant="caption" muted>
            {t('blocking.blockedUntil', { time: formatTime(s.resetsAt) })}
          </Txt>
        ) : null}
        {s?.suspension === 'override' ? (
          <>
            <Txt>
              {t('blocking.overrideActive', {
                time: formatTime(s.overrideUntil),
              })}
            </Txt>
            <Button
              kind="secondary"
              title={t('blocking.cancelOverride')}
              onPress={async () => {
                await trackerApi.cancelOverrides();
                await reloadAll();
              }}
            />
          </>
        ) : null}
        {s && s.overrideMinutes > 0 && enforced.length > 0 ? (
          <>
            <Button
              kind="secondary"
              title={t('blocking.override', { n: s.overrideMinutes })}
              onPress={override}
            />
            <Txt variant="caption" muted>
              {t('blocking.overrideUsed', {
                used: s.overridesUsedToday,
                max: s.maxOverridesPerDay,
              })}
            </Txt>
          </>
        ) : null}
        {s && s.suspension !== 'paused_for_day' && s.triggered.length > 0 ? (
          <Button kind="danger" title={t('blocking.pause')} onPress={pause} />
        ) : null}
      </Card>

      <SectionTitle>{t('blocking.apps')}</SectionTitle>
      <Card>
        {targets.data?.apps.length === 0 ? (
          <Txt muted>{t('blocking.appsEmpty')}</Txt>
        ) : null}
        {targets.data?.apps.map(o => (
          <ToggleRow
            key={o.value}
            label={o.label}
            help={o.value}
            value={o.selected}
            onValueChange={v => toggleTarget(o, v)}
          />
        ))}
      </Card>

      <SectionTitle>{t('blocking.domains')}</SectionTitle>
      <Card>
        {targets.data?.domains.map(o => (
          <ToggleRow
            key={o.value}
            label={o.label}
            value={o.selected}
            onValueChange={v => toggleTarget(o, v)}
          />
        ))}
        <Field
          label={t('blocking.addDomain')}
          value={domain}
          keyboardType="url"
          onChangeText={setDomain}
          error={domainError}
        />
        <Button
          kind="secondary"
          title={t('blocking.add')}
          onPress={addDomain}
          disabled={!domain.trim()}
        />
        {targets.data ? (
          <Txt variant="caption" muted>
            {targets.data.supportedBrowsers.join(', ')}
          </Txt>
        ) : null}
      </Card>

      <SectionTitle>{t('blocking.log')}</SectionTitle>
      <Card>
        {!s || s.log.length === 0 ? (
          <Txt muted>{t('blocking.logEmpty')}</Txt>
        ) : null}
        {s?.log
          .slice()
          .sort((a, b) => b.at - a.at)
          .map((e, i) => (
            <KeyValue
              key={`${e.type}-${e.at}-${i}`}
              label={`${formatTime(e.at)} · ${e.type.replace(/_/g, ' ')}`}
              value={
                e.target
                  ? `${e.target}${
                      e.occurrences && e.occurrences > 1
                        ? ` ×${e.occurrences}`
                        : ''
                    }`
                  : e.until
                  ? `→ ${formatTime(e.until)}`
                  : ''
              }
            />
          ))}
      </Card>
    </Screen>
  );
}
