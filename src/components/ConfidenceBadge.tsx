import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import type { ConfidenceCode } from '../domain/types';
import { confidenceDescription, confidenceLabel } from '../domain/format';
import { confidenceColor, useTheme } from '../theme';

export function ConfidenceBadge({ code }: { code: ConfidenceCode }) {
  const { palette } = useTheme();
  const color = confidenceColor(palette, code);
  return (
    <View
      accessible
      accessibilityLabel={`${confidenceLabel(code)}. ${confidenceDescription(
        code,
      )}`}
      style={[styles.badge, { borderColor: color }]}
    >
      <Text style={[styles.text, { color }]}>
        {code} · {confidenceLabel(code)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: 'flex-start',
    borderWidth: 1,
    borderRadius: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  text: { fontSize: 12, fontWeight: '600' },
});
