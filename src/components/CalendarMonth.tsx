import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import {
  dayRange,
  endOfMonth,
  startOfMonth,
  weekdayIndex,
} from '../domain/dates';
import type { DayTotal } from '../domain/history';
import { useTheme } from '../theme';

const WEEKDAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

/** Month grid; cell intensity follows the day's total. Days without data stay blank. */
export function CalendarMonth({
  monthDay,
  totals,
}: {
  monthDay: string;
  totals: DayTotal[];
}) {
  const { palette } = useTheme();
  const byDay = new Map(totals.map(t => [t.dayKey, t]));
  const days = dayRange(startOfMonth(monthDay), endOfMonth(monthDay));
  const max = Math.max(
    1,
    ...days.map(
      d => (byDay.get(d)?.verified ?? 0) + (byDay.get(d)?.estimated ?? 0),
    ),
  );
  const leading = weekdayIndex(days[0]);
  const cells: (string | null)[] = [...Array(leading).fill(null), ...days];
  return (
    <View>
      <View style={styles.week}>
        {WEEKDAYS.map((w, i) => (
          <Text key={i} style={[styles.weekday, { color: palette.textMuted }]}>
            {w}
          </Text>
        ))}
      </View>
      <View style={styles.grid}>
        {cells.map((day, i) => {
          if (!day) {
            return <View key={`blank-${i}`} style={styles.cell} />;
          }
          const t = byDay.get(day);
          const total = (t?.verified ?? 0) + (t?.estimated ?? 0);
          const intensity = t?.hasData ? 0.15 + 0.85 * (total / max) : 0;
          return (
            <View
              key={day}
              accessible
              accessibilityLabel={
                t?.hasData ? `${day}: ${total} videos` : `${day}: no data`
              }
              style={[
                styles.cell,
                styles.dayCell,
                { borderColor: palette.border },
              ]}
            >
              <View
                style={[
                  StyleSheet.absoluteFill,
                  styles.heat,
                  { backgroundColor: palette.primary, opacity: intensity },
                ]}
              />
              <Text style={[styles.dayNumber, { color: palette.text }]}>
                {Number(day.slice(8))}
              </Text>
              {t?.hasData ? (
                <Text style={[styles.dayCount, { color: palette.text }]}>
                  {total}
                </Text>
              ) : null}
            </View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  week: { flexDirection: 'row' },
  weekday: { width: `${100 / 7}%`, textAlign: 'center', fontSize: 12 },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: `${100 / 7}%`, aspectRatio: 1, padding: 2 },
  dayCell: {
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  heat: { borderRadius: 6 },
  dayNumber: { fontSize: 11 },
  dayCount: { fontSize: 12, fontWeight: '700' },
});
