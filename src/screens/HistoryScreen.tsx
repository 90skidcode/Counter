import React, { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { CalendarMonth } from '../components/CalendarMonth';
import { StackedBars } from '../components/StackedBars';
import {
  Button,
  Card,
  Chip,
  KeyValue,
  Notice,
  Row,
  Screen,
  Segmented,
  Txt,
} from '../components/ui';
import {
  addDays,
  addMonths,
  endOfMonth,
  startOfMonth,
  startOfWeek,
} from '../domain/dates';
import { formatDuration } from '../domain/format';
import {
  averagePerDay,
  dailyTotals,
  monthlyTotals,
  weeklyTotals,
  type DayTotal,
  type PeriodTotal,
} from '../domain/history';
import type { HistoryResponse } from '../domain/types';
import { useAsyncData } from '../hooks/useAsyncData';
import { t } from '../i18n';
import { trackerApi } from '../native/trackerApi';
import { useTrackerStore } from '../state/trackerStore';

type Mode = 'day' | 'week' | 'month' | 'calendar';

function rangeFor(
  mode: Mode,
  today: string,
  calendarMonth: string,
): [string, string] {
  switch (mode) {
    case 'day':
      return [addDays(today, -13), today];
    case 'week':
      return [startOfWeek(addDays(today, -7 * 7)), today];
    case 'month':
      return [startOfMonth(addMonths(today, -5)), today];
    case 'calendar':
      return [startOfMonth(calendarMonth), endOfMonth(calendarMonth)];
  }
}

function PeriodCard({
  title,
  total,
}: {
  title: string;
  total: PeriodTotal | DayTotal;
}) {
  const avg = 'daysWithData' in total ? averagePerDay(total) : null;
  return (
    <Card>
      <Txt variant="heading">{title}</Txt>
      {total.hasData ? (
        <>
          <KeyValue
            label={t('history.verified')}
            value={String(total.verified)}
          />
          <KeyValue
            label={t('history.estimated')}
            value={String(total.estimated)}
          />
          <KeyValue
            label={t('history.time')}
            value={formatDuration(total.activeMillis)}
          />
          {avg != null ? (
            <Txt variant="caption" muted>
              {t('history.average', { n: avg.toFixed(1) })}
            </Txt>
          ) : null}
        </>
      ) : (
        <Txt muted>{t('history.noData')}</Txt>
      )}
    </Card>
  );
}

export function HistoryScreen() {
  const summary = useTrackerStore(s => s.summary);
  const today = summary?.dayKey ?? new Date().toISOString().slice(0, 10);
  const [mode, setMode] = useState<Mode>('day');
  const [calendarMonth, setCalendarMonth] = useState(startOfMonth(today));
  const [filter, setFilter] = useState<Set<string>>(new Set());
  const [from, to] = rangeFor(mode, today, calendarMonth);

  const { data, error, loading, reload } = useAsyncData<HistoryResponse>(
    () => trackerApi.getHistory(from, to),
    [from, to],
  );

  const platforms = summary?.platforms ?? [];
  const days = useMemo(
    () =>
      data
        ? dailyTotals(data.rows, from, to, filter.size ? filter : undefined)
        : [],
    [data, from, to, filter],
  );
  const periods =
    mode === 'week'
      ? weeklyTotals(days)
      : mode === 'month'
      ? monthlyTotals(days)
      : null;

  const toggleFilter = (id: string) => {
    const next = new Set(filter);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setFilter(next);
  };

  const bars = (periods ?? days).map(p => {
    const key = 'periodKey' in p ? p.periodKey : p.dayKey;
    return {
      key,
      label: mode === 'month' ? key.slice(5, 7) : key.slice(8, 10),
      verified: p.verified,
      estimated: p.estimated,
      hasData: p.hasData,
    };
  });

  return (
    <Screen refreshing={loading} onRefresh={reload}>
      <Segmented<Mode>
        value={mode}
        onChange={setMode}
        options={[
          { value: 'day', label: t('history.day') },
          { value: 'week', label: t('history.week') },
          { value: 'month', label: t('history.month') },
          { value: 'calendar', label: t('history.calendar') },
        ]}
      />
      <Row style={local.wrap}>
        <Chip
          label={t('history.allPlatforms')}
          selected={filter.size === 0}
          onPress={() => setFilter(new Set())}
        />
        {platforms.map(p => (
          <Chip
            key={p.id}
            label={p.displayName}
            selected={filter.has(p.id)}
            onPress={() => toggleFilter(p.id)}
          />
        ))}
      </Row>
      {error ? (
        <Notice
          tone="error"
          text={error}
          action={{ title: t('common.retry'), onPress: reload }}
        />
      ) : null}
      {data && data.rows.length === 0 ? (
        <Notice text={t('history.empty')} />
      ) : null}

      {mode === 'calendar' ? (
        <Card>
          <Row>
            <Button
              kind="secondary"
              title="‹"
              onPress={() => setCalendarMonth(addMonths(calendarMonth, -1))}
            />
            <Txt variant="heading" style={local.monthTitle}>
              {calendarMonth.slice(0, 7)}
            </Txt>
            <Button
              kind="secondary"
              title="›"
              disabled={calendarMonth >= startOfMonth(today)}
              onPress={() => setCalendarMonth(addMonths(calendarMonth, 1))}
            />
          </Row>
          <CalendarMonth monthDay={calendarMonth} totals={days} />
        </Card>
      ) : (
        <Card>
          <Txt variant="label" muted>
            {t('history.videos')}
          </Txt>
          <StackedBars data={bars} />
        </Card>
      )}

      <View style={local.list}>
        {(periods ?? [...days].reverse())
          .slice()
          .sort((a, b) => {
            const ka = 'periodKey' in a ? a.periodKey : a.dayKey;
            const kb = 'periodKey' in b ? b.periodKey : b.dayKey;
            return kb.localeCompare(ka);
          })
          .filter(p => mode !== 'calendar' || p.hasData)
          .map(p => {
            const key = 'periodKey' in p ? p.periodKey : p.dayKey;
            const title =
              mode === 'week' ? t('history.weekOf', { day: key }) : key;
            return <PeriodCard key={key} title={title} total={p} />;
          })}
      </View>
    </Screen>
  );
}

const local = StyleSheet.create({
  wrap: { flexWrap: 'wrap' },
  monthTitle: { flex: 1, textAlign: 'center' },
  list: { gap: 12 },
});
