import React, { type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import type { PermissionState } from '../domain/types';
import { t } from '../i18n';
import { useTheme } from '../theme';
import { Card, Row, Txt } from './ui';

export function PermissionCard({
  title,
  status,
  purpose,
  without,
  children,
}: {
  title: string;
  status: PermissionState;
  purpose: string;
  without?: string;
  children?: ReactNode;
}) {
  const { palette } = useTheme();
  const color =
    status === 'granted'
      ? palette.success
      : status === 'not_used'
      ? palette.textMuted
      : palette.warning;
  return (
    <Card>
      <Row>
        <Txt variant="heading" style={styles.title}>
          {title}
        </Txt>
        <Txt variant="label" color={color}>
          {t(`permissions.status.${status}`)}
        </Txt>
      </Row>
      <Txt>{purpose}</Txt>
      {without && status !== 'granted' ? (
        <Txt variant="caption" muted>
          {without}
        </Txt>
      ) : null}
      {children ? <View style={styles.actions}>{children}</View> : null}
    </Card>
  );
}

const styles = StyleSheet.create({
  title: { flex: 1 },
  actions: { gap: 8 },
});
