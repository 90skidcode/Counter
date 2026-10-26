import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../theme';

export interface BarDatum {
  key: string;
  label: string;
  verified: number;
  estimated: number;
  /** Days without any measurement render as an outlined placeholder, not a zero bar. */
  hasData: boolean;
}

/** Simple stacked bar chart: verified (solid) + estimated (lighter) per bucket. */
export function StackedBars({
  data,
  height = 140,
}: {
  data: BarDatum[];
  height?: number;
}) {
  const { palette } = useTheme();
  const max = Math.max(1, ...data.map(d => d.verified + d.estimated));
  const showEvery = Math.ceil(data.length / 8);
  return (
    <View>
      <View style={[styles.chart, { height }]}>
        {data.map(d => {
          const total = d.verified + d.estimated;
          return (
            <View
              key={d.key}
              accessible
              accessibilityLabel={
                d.hasData
                  ? `${d.label}: ${d.verified} verified, ${d.estimated} estimated`
                  : `${d.label}: no data`
              }
              style={styles.column}
            >
              {d.hasData ? (
                <View
                  style={[styles.stack, { height: (total / max) * height }]}
                >
                  <View
                    style={[
                      styles.estimated,
                      { flex: d.estimated, backgroundColor: palette.estimated },
                    ]}
                  />
                  <View
                    style={{
                      flex: d.verified,
                      backgroundColor: palette.verified,
                    }}
                  />
                </View>
              ) : (
                <View style={[styles.empty, { borderColor: palette.border }]} />
              )}
            </View>
          );
        })}
      </View>
      <View style={styles.labels}>
        {data.map((d, i) => (
          <Text
            key={d.key}
            style={[styles.label, { color: palette.textMuted }]}
            numberOfLines={1}
          >
            {i % showEvery === 0 ? d.label : ''}
          </Text>
        ))}
      </View>
      <View style={styles.legend}>
        <View style={[styles.swatch, { backgroundColor: palette.verified }]} />
        <Text style={{ color: palette.textMuted }}>Verified</Text>
        <View
          style={[
            styles.swatch,
            styles.estimatedSwatch,
            { backgroundColor: palette.estimated },
          ]}
        />
        <Text style={{ color: palette.textMuted }}>Estimated</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  chart: { flexDirection: 'row', alignItems: 'flex-end', gap: 3 },
  column: { flex: 1, justifyContent: 'flex-end' },
  stack: { justifyContent: 'flex-end' },
  estimated: { opacity: 0.55, borderTopLeftRadius: 3, borderTopRightRadius: 3 },
  estimatedSwatch: { opacity: 0.55 },
  empty: { height: 4, borderTopWidth: 1, borderStyle: 'dashed' },
  labels: { flexDirection: 'row', gap: 3, marginTop: 4 },
  label: { flex: 1, fontSize: 10, textAlign: 'center' },
  legend: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 8 },
  swatch: { width: 10, height: 10, borderRadius: 2, marginLeft: 6 },
});
