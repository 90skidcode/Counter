import React, { useEffect, useMemo } from 'react';
import { StatusBar, StyleSheet, useColorScheme, View } from 'react-native';
import {
  DarkTheme,
  DefaultTheme,
  NavigationContainer,
} from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { Txt } from './components/ui';
import { t } from './i18n';
import { RootNavigator } from './navigation/RootNavigator';
import { connectTrackerEvents, useTrackerStore } from './state/trackerStore';
import { dark, light, ThemeContext } from './theme';

export default function App() {
  const system = useColorScheme();
  const settings = useTrackerStore(s => s.settings);
  const error = useTrackerStore(s => s.error);
  const loadSettings = useTrackerStore(s => s.loadSettings);
  const refresh = useTrackerStore(s => s.refresh);

  useEffect(() => {
    loadSettings();
    refresh();
    return connectTrackerEvents();
  }, [loadSettings, refresh]);

  const isDark =
    settings?.theme === 'dark' ||
    (settings?.theme !== 'light' && system === 'dark');
  const palette = isDark ? dark : light;
  const theme = useMemo(() => ({ palette, isDark }), [palette, isDark]);
  const navTheme = useMemo(() => {
    const base = isDark ? DarkTheme : DefaultTheme;
    return {
      ...base,
      colors: {
        ...base.colors,
        background: palette.background,
        card: palette.surface,
        text: palette.text,
        border: palette.border,
        primary: palette.primary,
      },
    };
  }, [isDark, palette]);

  return (
    <SafeAreaProvider>
      <ThemeContext.Provider value={theme}>
        <StatusBar
          barStyle={isDark ? 'light-content' : 'dark-content'}
          backgroundColor={palette.surface}
        />
        {settings ? (
          <NavigationContainer theme={navTheme}>
            <RootNavigator onboardingCompleted={settings.onboardingCompleted} />
          </NavigationContainer>
        ) : (
          <View
            style={[styles.splash, { backgroundColor: palette.background }]}
          >
            <Txt muted>{error ?? t('common.loading')}</Txt>
          </View>
        )}
      </ThemeContext.Provider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  splash: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
});
