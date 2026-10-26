import React from 'react';
import { Card, Divider, ListLink, Screen } from '../components/ui';
import { t } from '../i18n';

export type MoreRoute =
  | 'Blocking'
  | 'Permissions'
  | 'Diagnostics'
  | 'Privacy'
  | 'Settings';

export function MoreScreen({
  onNavigate,
}: {
  onNavigate: (route: MoreRoute) => void;
}) {
  const items: [MoreRoute, string][] = [
    ['Blocking', t('more.blocking')],
    ['Permissions', t('more.permissions')],
    ['Diagnostics', t('more.diagnostics')],
    ['Privacy', t('more.privacy')],
    ['Settings', t('more.settings')],
  ];
  return (
    <Screen>
      <Card>
        {items.map(([route, label], i) => (
          <React.Fragment key={route}>
            {i > 0 ? <Divider /> : null}
            <ListLink label={label} onPress={() => onNavigate(route)} />
          </React.Fragment>
        ))}
      </Card>
    </Screen>
  );
}
