import { AppShell, Group, Title, NavLink, Box, Text, rem } from '@mantine/core';
import { IconUser, IconChartBar, IconBriefcase, IconBriefcase2 } from '@tabler/icons-react';
import { NavLink as RouterNavLink, useLocation } from 'react-router-dom';

const LINKS = [
  { to: '/profiles', label: 'My Profiles', icon: IconUser, match: ['/', '/profiles'] },
  { to: '/analytics', label: 'Analytics', icon: IconChartBar, match: ['/analytics'] },
  { to: '/jobs', label: 'Jobs', icon: IconBriefcase, match: ['/jobs'] },
];

/**
 * Consistent app chrome: a branded header and a left nav rail used across all pages.
 */
export default function Layout({ children }) {
  const { pathname } = useLocation();

  return (
    <AppShell header={{ height: 64 }} navbar={{ width: 240, breakpoint: 'sm' }} padding="lg">
      <AppShell.Header
        style={{
          background: 'linear-gradient(90deg, #1d4ed8 0%, #2563eb 100%)',
          border: 'none',
        }}
      >
        <Group h="100%" px="lg" gap="sm">
          <IconBriefcase2 size={26} color="white" />
          <Title order={3} c="white" fw={700}>
            JobWatch
          </Title>
          <Text c="blue.1" size="sm" ml={4} mt={rem(3)}>
            job alert dashboard
          </Text>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md">
        {LINKS.map(({ to, label, icon: Icon, match }) => {
          const active = match.includes(pathname) || pathname.startsWith(to + '/');
          return (
            <NavLink
              key={to}
              component={RouterNavLink}
              to={to}
              label={label}
              leftSection={<Icon size={18} stroke={1.8} />}
              active={active}
              variant="filled"
              mb={4}
              style={{ borderRadius: 8 }}
            />
          );
        })}
      </AppShell.Navbar>

      <AppShell.Main>
        <Box maw={1200} mx="auto">
          {children}
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}

