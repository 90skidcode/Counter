import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import {
  createNativeStackNavigator,
  type NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { t } from '../i18n';
import { BlockingScreen } from '../screens/BlockingScreen';
import { DashboardScreen } from '../screens/DashboardScreen';
import { DiagnosticsScreen } from '../screens/DiagnosticsScreen';
import { HistoryScreen } from '../screens/HistoryScreen';
import { LimitsScreen } from '../screens/LimitsScreen';
import { MoreScreen } from '../screens/MoreScreen';
import { OnboardingScreen } from '../screens/OnboardingScreen';
import { PermissionsScreen } from '../screens/PermissionsScreen';
import { PlatformsScreen } from '../screens/PlatformsScreen';
import { PrivacyScreen } from '../screens/PrivacyScreen';
import { SettingsScreen } from '../screens/SettingsScreen';

export type RootStackParams = {
  Tabs: undefined;
  Welcome: undefined;
  Permissions: { onboarding?: boolean } | undefined;
  Blocking: undefined;
  Diagnostics: undefined;
  Privacy: undefined;
  Settings: undefined;
};

const Stack = createNativeStackNavigator<RootStackParams>();
const Tab = createBottomTabNavigator();

type Nav = NativeStackScreenProps<RootStackParams>['navigation'];

function Tabs({ navigation }: { navigation: Nav }) {
  return (
    <Tab.Navigator
      screenOptions={{
        tabBarIcon: () => null,
        tabBarLabelStyle: { fontSize: 13 },
      }}
    >
      <Tab.Screen name="Today" options={{ title: t('tabs.today') }}>
        {() => (
          <DashboardScreen
            onOpenPermissions={() => navigation.navigate('Permissions')}
          />
        )}
      </Tab.Screen>
      <Tab.Screen
        name="Platforms"
        component={PlatformsScreen}
        options={{ title: t('tabs.platforms') }}
      />
      <Tab.Screen
        name="History"
        component={HistoryScreen}
        options={{ title: t('tabs.history') }}
      />
      <Tab.Screen
        name="Limits"
        component={LimitsScreen}
        options={{ title: t('tabs.limits') }}
      />
      <Tab.Screen name="More" options={{ title: t('tabs.more') }}>
        {() => <MoreScreen onNavigate={route => navigation.navigate(route)} />}
      </Tab.Screen>
    </Tab.Navigator>
  );
}

export function RootNavigator({
  onboardingCompleted,
}: {
  onboardingCompleted: boolean;
}) {
  return (
    <Stack.Navigator>
      {onboardingCompleted ? (
        <>
          <Stack.Screen
            name="Tabs"
            component={Tabs}
            options={{ headerShown: false }}
          />
          <Stack.Screen
            name="Permissions"
            options={{ title: t('permissions.title') }}
          >
            {({ route }) => (
              <PermissionsScreen onboarding={route.params?.onboarding} />
            )}
          </Stack.Screen>
          <Stack.Screen
            name="Blocking"
            component={BlockingScreen}
            options={{ title: t('blocking.title') }}
          />
          <Stack.Screen
            name="Diagnostics"
            component={DiagnosticsScreen}
            options={{ title: t('diagnostics.title') }}
          />
          <Stack.Screen name="Privacy" options={{ title: t('privacy.title') }}>
            {({ navigation }) => (
              <PrivacyScreen
                onOpenPermissions={() => navigation.navigate('Permissions')}
              />
            )}
          </Stack.Screen>
          <Stack.Screen
            name="Settings"
            options={{ title: t('settings.title') }}
          >
            {({ navigation }) => (
              <SettingsScreen
                onOpenDiagnostics={() => navigation.navigate('Diagnostics')}
                onOpenPrivacy={() => navigation.navigate('Privacy')}
              />
            )}
          </Stack.Screen>
        </>
      ) : (
        <>
          <Stack.Screen name="Welcome" options={{ title: t('app.name') }}>
            {({ navigation }) => (
              <OnboardingScreen
                onSetup={() =>
                  navigation.navigate('Permissions', { onboarding: true })
                }
              />
            )}
          </Stack.Screen>
          <Stack.Screen
            name="Permissions"
            options={{ title: t('permissions.title') }}
          >
            {({ route }) => (
              <PermissionsScreen onboarding={route.params?.onboarding} />
            )}
          </Stack.Screen>
        </>
      )}
    </Stack.Navigator>
  );
}
