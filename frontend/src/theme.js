import { createTheme } from '@mantine/core';

/**
 * Shared JobWatch theme — a consistent blue brand, rounded cards, and a clean
 * sans-serif stack. Used everywhere via MantineProvider so the look stays uniform.
 */
export const theme = createTheme({
  primaryColor: 'brand',
  defaultRadius: 'md',
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
  headings: {
    fontWeight: '700',
  },
  colors: {
    brand: [
      '#eff6ff',
      '#dbeafe',
      '#bfdbfe',
      '#93c5fd',
      '#60a5fa',
      '#3b82f6',
      '#2563eb', // primary
      '#1d4ed8',
      '#1e40af',
      '#1e3a8a',
    ],
  },
});

export const BRAND = '#2563eb';
export const OK = '#16a34a';

