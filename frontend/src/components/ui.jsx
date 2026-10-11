import { Box, Card, Center, Group, Loader, Stack, Text, ThemeIcon, Title } from '@mantine/core';

/** Consistent page title block (title + subtitle on the left, actions on the right). */
export function PageHeader({ title, subtitle, actions, icon: Icon }) {
  return (
    <Group justify="space-between" align="flex-end" mb="xl" wrap="wrap" gap="md">
      <Group gap="md" wrap="nowrap" align="center">
        {Icon && (
          <ThemeIcon size={48} radius="lg" variant="light" color="brand" visibleFrom="xs">
            <Icon size={26} stroke={1.7} />
          </ThemeIcon>
        )}
        <Box>
          <Title order={2}>{title}</Title>
          {subtitle && <Text c="dimmed" size="sm" mt={4} maw={620}>{subtitle}</Text>}
        </Box>
      </Group>
      {actions}
    </Group>
  );
}

/** Friendly empty state used by every list/table. */
export function EmptyState({ icon: Icon, title, message, action }) {
  return (
    <Stack align="center" gap="xs" py={56} px="md">
      <ThemeIcon size={64} radius="xl" variant="light" color="brand">
        <Icon size={32} stroke={1.5} />
      </ThemeIcon>
      <Text fw={700} fz="lg" mt="xs">{title}</Text>
      <Text c="dimmed" size="sm" ta="center" maw={380}>{message}</Text>
      {action}
    </Stack>
  );
}

/** Centered spinner used while data loads. */
export function PageLoader({ h = 320 }) {
  return <Center h={h}><Loader type="dots" size="lg" /></Center>;
}

/** Metric card used on the analytics page. */
export function StatCard({ icon: Icon, color = 'brand', value, label, hint }) {
  return (
    <Card className="jw-card-hover">
      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Box>
          <Text size="xs" tt="uppercase" fw={700} c="dimmed" lts={0.6}>{label}</Text>
          <Text fz={34} fw={800} lh={1.1} mt={8}>{(value ?? 0).toLocaleString()}</Text>
          {hint && <Text size="xs" c="dimmed" mt={6}>{hint}</Text>}
        </Box>
        <ThemeIcon variant="light" color={color} size={46} radius="lg">
          <Icon size={24} stroke={1.7} />
        </ThemeIcon>
      </Group>
    </Card>
  );
}

const AVATAR_COLORS = ['brand', 'grape', 'teal', 'orange', 'cyan', 'pink', 'lime', 'blue'];

/** Deterministic colored initial for a company, so each employer is recognizable. */
export function CompanyAvatar({ name = '?', size = 44 }) {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  const color = AVATAR_COLORS[h % AVATAR_COLORS.length];
  return (
    <ThemeIcon size={size} radius="lg" variant="light" color={color}>
      <Text fw={800} fz={size * 0.42}>{name.trim().charAt(0).toUpperCase() || '?'}</Text>
    </ThemeIcon>
  );
}
