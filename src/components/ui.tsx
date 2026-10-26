import React, { type ReactNode } from 'react';
import {
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { radius, spacing, useTheme } from '../theme';

type Variant = 'title' | 'heading' | 'body' | 'caption' | 'number' | 'label';

const variantStyle: Record<Variant, TextStyle> = {
  title: { fontSize: 24, fontWeight: '700' },
  heading: { fontSize: 17, fontWeight: '600' },
  body: { fontSize: 15, lineHeight: 21 },
  caption: { fontSize: 13, lineHeight: 18 },
  number: { fontSize: 34, fontWeight: '700', fontVariant: ['tabular-nums'] },
  label: { fontSize: 13, fontWeight: '600', letterSpacing: 0.2 },
};

export function Txt({
  variant = 'body',
  muted,
  color,
  style,
  children,
  ...rest
}: {
  variant?: Variant;
  muted?: boolean;
  color?: string;
  style?: StyleProp<TextStyle>;
  children: ReactNode;
  accessibilityRole?: 'header' | 'text';
  numberOfLines?: number;
}) {
  const { palette } = useTheme();
  return (
    <Text
      style={[
        variantStyle[variant],
        { color: color ?? (muted ? palette.textMuted : palette.text) },
        style,
      ]}
      accessibilityRole={
        variant === 'title' || variant === 'heading' ? 'header' : undefined
      }
      {...rest}
    >
      {children}
    </Text>
  );
}

export function Screen({
  children,
  refreshing,
  onRefresh,
}: {
  children: ReactNode;
  refreshing?: boolean;
  onRefresh?: () => void;
}) {
  const { palette } = useTheme();
  return (
    <ScrollView
      style={{ backgroundColor: palette.background }}
      contentContainerStyle={styles.screen}
      refreshControl={
        onRefresh ? (
          <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} />
        ) : undefined
      }
    >
      {children}
    </ScrollView>
  );
}

export function Card({
  children,
  style,
}: {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
}) {
  const { palette } = useTheme();
  return (
    <View
      style={[
        styles.card,
        { backgroundColor: palette.surface, borderColor: palette.border },
        style,
      ]}
    >
      {children}
    </View>
  );
}

export function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <Txt variant="label" muted style={styles.sectionTitle}>
      {children}
    </Txt>
  );
}

export function Row({
  children,
  style,
}: {
  children: ReactNode;
  style?: StyleProp<ViewStyle>;
}) {
  return <View style={[styles.row, style]}>{children}</View>;
}

export function KeyValue({
  label,
  value,
  valueColor,
}: {
  label: string;
  value: string;
  valueColor?: string;
}) {
  return (
    <Row style={styles.keyValue}>
      <Txt muted style={styles.flex}>
        {label}
      </Txt>
      <Txt color={valueColor} style={styles.value}>
        {value}
      </Txt>
    </Row>
  );
}

export function Button({
  title,
  onPress,
  kind = 'primary',
  disabled,
  accessibilityHint,
}: {
  title: string;
  onPress: () => void;
  kind?: 'primary' | 'secondary' | 'danger';
  disabled?: boolean;
  accessibilityHint?: string;
}) {
  const { palette } = useTheme();
  const bg =
    kind === 'primary'
      ? palette.primary
      : kind === 'danger'
      ? 'transparent'
      : palette.surfaceAlt;
  const fg =
    kind === 'primary'
      ? palette.onPrimary
      : kind === 'danger'
      ? palette.danger
      : palette.text;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      accessibilityHint={accessibilityHint}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        {
          backgroundColor: bg,
          borderColor: kind === 'danger' ? palette.danger : 'transparent',
          opacity: disabled ? 0.5 : pressed ? 0.8 : 1,
        },
      ]}
    >
      <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>
    </Pressable>
  );
}

export function ToggleRow({
  label,
  help,
  value,
  onValueChange,
  disabled,
}: {
  label: string;
  help?: string;
  value: boolean;
  onValueChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <View style={styles.toggleRow}>
      <View style={styles.flex}>
        <Txt>{label}</Txt>
        {help ? (
          <Txt variant="caption" muted>
            {help}
          </Txt>
        ) : null}
      </View>
      <Switch
        accessibilityLabel={label}
        value={value}
        onValueChange={onValueChange}
        disabled={disabled}
      />
    </View>
  );
}

export function Field({
  label,
  help,
  value,
  onChangeText,
  error,
  placeholder,
  keyboardType = 'number-pad',
}: {
  label: string;
  help?: string;
  value: string;
  onChangeText: (text: string) => void;
  error?: string | null;
  placeholder?: string;
  keyboardType?: 'number-pad' | 'default' | 'url';
}) {
  const { palette } = useTheme();
  return (
    <View style={styles.field}>
      <Txt variant="label">{label}</Txt>
      <TextInput
        accessibilityLabel={label}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={palette.textMuted}
        keyboardType={keyboardType}
        autoCapitalize="none"
        autoCorrect={false}
        style={[
          styles.input,
          {
            color: palette.text,
            borderColor: error ? palette.danger : palette.border,
            backgroundColor: palette.surfaceAlt,
          },
        ]}
      />
      {error ? (
        <Txt variant="caption" color={palette.danger} accessibilityRole="text">
          {error}
        </Txt>
      ) : help ? (
        <Txt variant="caption" muted>
          {help}
        </Txt>
      ) : null}
    </View>
  );
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  const { palette } = useTheme();
  return (
    <View
      style={[styles.segmented, { backgroundColor: palette.surfaceAlt }]}
      accessibilityRole="tablist"
    >
      {options.map(o => {
        const selected = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            onPress={() => onChange(o.value)}
            style={[
              styles.segment,
              selected && { backgroundColor: palette.surface },
            ]}
          >
            <Text
              style={[
                styles.segmentText,
                { color: selected ? palette.text : palette.textMuted },
              ]}
            >
              {o.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function Chip({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  const { palette } = useTheme();
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={[
        styles.chip,
        {
          backgroundColor: selected ? palette.primary : palette.surfaceAlt,
        },
      ]}
    >
      <Text style={{ color: selected ? palette.onPrimary : palette.text }}>
        {label}
      </Text>
    </Pressable>
  );
}

export function ProgressBar({
  progress,
  color,
  label,
}: {
  progress: number;
  color?: string;
  label: string;
}) {
  const { palette } = useTheme();
  const pct = Math.round(Math.max(0, Math.min(1, progress)) * 100);
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityValue={{ min: 0, max: 100, now: pct }}
      style={[styles.track, { backgroundColor: palette.track }]}
    >
      <View
        style={[
          styles.fill,
          { width: `${pct}%`, backgroundColor: color ?? palette.primary },
        ]}
      />
    </View>
  );
}

export function Notice({
  text,
  tone = 'info',
  action,
}: {
  text: string;
  tone?: 'info' | 'warning' | 'error';
  action?: { title: string; onPress: () => void };
}) {
  const { palette } = useTheme();
  const color =
    tone === 'error'
      ? palette.danger
      : tone === 'warning'
      ? palette.warning
      : palette.primary;
  return (
    <View
      accessibilityRole="alert"
      style={[
        styles.notice,
        { borderLeftColor: color, backgroundColor: palette.surface },
      ]}
    >
      <Txt>{text}</Txt>
      {action ? (
        <View style={styles.noticeAction}>
          <Button
            kind="secondary"
            title={action.title}
            onPress={action.onPress}
          />
        </View>
      ) : null}
    </View>
  );
}

export function Divider() {
  const { palette } = useTheme();
  return <View style={[styles.divider, { backgroundColor: palette.border }]} />;
}

export function ListLink({
  label,
  onPress,
  detail,
}: {
  label: string;
  onPress: () => void;
  detail?: string;
}) {
  const { palette } = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.listLink,
        pressed && { backgroundColor: palette.surfaceAlt },
      ]}
    >
      <Txt style={styles.flex}>{label}</Txt>
      {detail ? <Txt muted>{detail}</Txt> : null}
      <Txt muted> ›</Txt>
    </Pressable>
  );
}

export const styles = StyleSheet.create({
  screen: {
    padding: spacing.lg,
    paddingBottom: spacing.xl * 2,
    gap: spacing.md,
  },
  card: {
    borderRadius: radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    padding: spacing.lg,
    gap: spacing.sm,
  },
  sectionTitle: { marginTop: spacing.md, textTransform: 'uppercase' },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  keyValue: { justifyContent: 'space-between', minHeight: 28 },
  value: { textAlign: 'right', flexShrink: 1 },
  flex: { flex: 1 },
  button: {
    minHeight: 48,
    borderRadius: radius.md,
    borderWidth: 1,
    paddingHorizontal: spacing.lg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: { fontSize: 15, fontWeight: '600' },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    minHeight: 48,
  },
  field: { gap: spacing.xs },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderRadius: radius.sm,
    paddingHorizontal: spacing.md,
    fontSize: 16,
  },
  segmented: { flexDirection: 'row', borderRadius: radius.md, padding: 3 },
  segment: {
    flex: 1,
    minHeight: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.sm,
  },
  segmentText: { fontSize: 14, fontWeight: '600' },
  chip: {
    paddingHorizontal: spacing.md,
    minHeight: 36,
    borderRadius: 18,
    justifyContent: 'center',
  },
  track: { height: 10, borderRadius: 5, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 5 },
  notice: {
    borderLeftWidth: 4,
    borderRadius: radius.sm,
    padding: spacing.md,
    gap: spacing.sm,
  },
  noticeAction: { alignSelf: 'flex-start' },
  divider: { height: StyleSheet.hairlineWidth, marginVertical: spacing.xs },
  listLink: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: 52,
    paddingHorizontal: spacing.xs,
  },
});
