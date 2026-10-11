import { createTheme } from '@mantine/core';

/**
 * JobWatch design system — one place that defines the whole look:
 * violet-indigo brand, soft rounded surfaces, Inter typography, and
 * component defaults so every page stays visually consistent.
 */
export const theme = createTheme({
  primaryColor: 'brand',
  primaryShade: { light: 6, dark: 5 },
  defaultRadius: 'md',
  cursorType: 'pointer',
  fontFamily:
    'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
  headings: {
    fontFamily:
      'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
    fontWeight: '800',
    sizes: {
      h1: { fontSize: '2.25rem', lineHeight: '1.2' },
      h2: { fontSize: '1.75rem', lineHeight: '1.25' },
      h3: { fontSize: '1.375rem', lineHeight: '1.3' },
    },
  },
  colors: {
    brand: [
      '#f1f0ff',
      '#e2e0ff',
      '#c7c3ff',
      '#a9a2ff',
      '#8c82ff',
      '#766aff',
      '#6355f5', // primary (light)
      '#5242d9',
      '#4335b3',
      '#352b8c',
    ],
  },
  shadows: {
    xs: '0 1px 2px rgba(16, 24, 40, 0.05)',
    sm: '0 1px 3px rgba(16, 24, 40, 0.08), 0 1px 2px rgba(16, 24, 40, 0.04)',
    md: '0 8px 24px -6px rgba(16, 24, 40, 0.10), 0 2px 6px rgba(16, 24, 40, 0.04)',
    lg: '0 20px 40px -12px rgba(16, 24, 40, 0.18)',
  },
  components: {
    Card: {
      defaultProps: { radius: 'lg', withBorder: true, padding: 'lg' },
      classNames: { root: 'jw-card' },
    },
    Button: {
      defaultProps: { radius: 'md', fw: 600 },
    },
    ActionIcon: { defaultProps: { radius: 'md' } },
    Badge: { defaultProps: { radius: 'sm', fw: 600 } },
    TextInput: { defaultProps: { radius: 'md' } },
    NumberInput: { defaultProps: { radius: 'md' } },
    TagsInput: { defaultProps: { radius: 'md' } },
    Tooltip: { defaultProps: { radius: 'md', withArrow: true } },
  },
});

/** Shared chart / accent colors (used by Recharts, which can't read Mantine props). */
export const BRAND = '#6355f5';
export const BRAND_SOFT = '#a9a2ff';
export const OK = '#12b886';
export const WARN = '#f59f00';
