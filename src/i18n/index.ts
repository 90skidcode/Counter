import { en, type StringKey } from './en';

/**
 * Minimal, language-ready string lookup. Add a locale by creating a file with the same keys
 * and selecting it here; components only ever call t().
 */
const tables: Record<string, Record<string, string>> = { en };
let current: Record<string, string> = en;

export function setLocale(locale: string): void {
  current = tables[locale] ?? en;
}

export function t(
  key: StringKey | string,
  params?: Record<string, string | number>,
): string {
  const template = current[key] ?? en[key as StringKey] ?? key;
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}
