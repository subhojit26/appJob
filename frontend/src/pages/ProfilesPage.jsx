import { useEffect, useState } from 'react';
import {
  Card, Group, Button, Badge, ActionIcon, Text, Stack, Tooltip, SimpleGrid, Box, Switch,
  Divider, ThemeIcon,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconPlus, IconEdit, IconTrash, IconUsers, IconBolt, IconClockHour4, IconUserPlus,
  IconSparkles,
} from '@tabler/icons-react';
import { useNavigate } from 'react-router-dom';
import { getProfiles, toggleActive, deleteProfile } from '../api.js';
import { PageHeader, EmptyState, PageLoader } from '../components/ui.jsx';

function TagSection({ label, items, color = 'brand', empty = 'Any' }) {
  return (
    <Box>
      <Text size="xs" tt="uppercase" fw={700} c="dimmed" mb={6} lts={0.5}>{label}</Text>
      {items && items.length > 0 ? (
        <Group gap={6}>
          {items.map((t) => (
            <Badge key={t} variant="light" color={color} tt="none" size="md">{t}</Badge>
          ))}
        </Group>
      ) : (
        <Text size="sm" c="dimmed">{empty}</Text>
      )}
    </Box>
  );
}

function ProfileCard({ p, active, busy, onToggle, onEdit, onDelete }) {
  return (
    <Card
      className="jw-card-hover"
      style={active ? {
        borderColor: 'var(--mantine-color-brand-4)',
        boxShadow: '0 0 0 3px var(--jw-ring), var(--mantine-shadow-sm)',
      } : undefined}
    >
      <Stack gap="md" h="100%">
        <Group justify="space-between" align="flex-start" wrap="nowrap">
          <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
            <ThemeIcon
              size={42} radius="lg"
              variant={active ? 'gradient' : 'light'}
              gradient={{ from: 'brand.6', to: 'grape.5', deg: 135 }}
              color={active ? undefined : 'gray'}
            >
              {active ? <IconBolt size={22} stroke={1.8} /> : <IconUsers size={22} stroke={1.6} />}
            </ThemeIcon>
            <Box style={{ minWidth: 0 }}>
              <Text fw={700} fz="lg" truncate>{p.name}</Text>
              <Group gap={6} mt={2}>
                {active && <Badge size="xs" color="teal" variant="light">Active</Badge>}
                {!p.enabled && <Badge size="xs" color="gray" variant="outline">Disabled</Badge>}
              </Group>
            </Box>
          </Group>
          <Group gap={2} wrap="nowrap">
            <Tooltip label="Edit">
              <ActionIcon variant="subtle" color="gray" onClick={onEdit} aria-label={`Edit ${p.name}`}>
                <IconEdit size={18} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Delete">
              <ActionIcon variant="subtle" color="red" onClick={onDelete} aria-label={`Delete ${p.name}`}>
                <IconTrash size={18} />
              </ActionIcon>
            </Tooltip>
          </Group>
        </Group>

        <TagSection label="Role keywords" items={p.includeTitle} color="brand" empty="—" />
        <TagSection label="Tech stack" items={p.stack} color="grape" empty="—" />
        <TagSection label="Companies" items={p.companies} color="teal" empty="Any company" />

        <Box mt="auto">
          <Divider mb="sm" />
          <Group justify="space-between">
            <Group gap={6}>
              <IconClockHour4 size={16} color="var(--mantine-color-dimmed)" />
              <Text size="sm" c="dimmed">
                {(p.expMin ?? 0)}–{p.expMax ?? '∞'} yrs experience
              </Text>
            </Group>
            <Switch
              size="md"
              color="teal"
              checked={!!active}
              disabled={busy}
              onChange={onToggle}
              label={active ? 'Active' : 'Activate'}
              labelPosition="left"
              styles={{ label: { fontWeight: 600, fontSize: 13 } }}
            />
          </Group>
        </Box>
      </Stack>
    </Card>
  );
}

export default function ProfilesPage() {
  const nav = useNavigate();
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = () => getProfiles().then(setData).catch(() =>
    notifications.show({ color: 'red', message: 'Failed to load profiles' }));

  useEffect(() => { load(); }, []);

  const isActive = (id) => data?.activeIds?.includes(id);

  const onToggle = async (id) => {
    setBusy(true);
    try {
      const res = await toggleActive(id);
      setData((d) => ({ ...d, activeIds: res.activeIds }));
    } catch {
      notifications.show({ color: 'red', message: 'Could not update the profile' });
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (id, name) => {
    if (!window.confirm(`Delete profile "${name}"?`)) return;
    try {
      await deleteProfile(id);
      notifications.show({ color: 'gray', message: `Deleted "${name}"` });
      load();
    } catch {
      notifications.show({ color: 'red', message: 'Delete failed' });
    }
  };

  if (!data) return <PageLoader />;

  const total = data.profiles.length;
  const activeCount = data.profiles.filter((p) => isActive(p.id)).length;

  return (
    <Stack gap={0}>
      <PageHeader
        icon={IconUsers}
        title="My Profiles"
        subtitle="Each profile describes a kind of role you want. Jobs matching any active profile are shown and emailed to you."
        actions={
          <Button size="md" leftSection={<IconPlus size={18} />} onClick={() => nav('/profiles/new')}>
            New profile
          </Button>
        }
      />

      {total > 0 && (
        <Card className="jw-hero" mb="xl" padding="lg">
          <Group justify="space-between" wrap="wrap" gap="md" style={{ position: 'relative', zIndex: 1 }}>
            <Group gap="md" wrap="nowrap">
              <ThemeIcon size={48} radius="lg" color="white" variant="white" c="brand.6">
                <IconSparkles size={26} stroke={1.7} />
              </ThemeIcon>
              <Box>
                <Text fw={800} fz={20} c="white">
                  {activeCount === 0
                    ? 'No profile is active yet'
                    : `${activeCount} active profile${activeCount > 1 ? 's' : ''} watching for jobs`}
                </Text>
                <Text c="rgba(255,255,255,0.85)" size="sm">
                  {activeCount === 0
                    ? 'Switch one on below to start receiving matches.'
                    : `${total} profile${total > 1 ? 's' : ''} in total — toggle any card to change what you track.`}
                </Text>
              </Box>
            </Group>
          </Group>
        </Card>
      )}

      {total === 0 ? (
        <Card>
          <EmptyState
            icon={IconUserPlus}
            title="Create your first profile"
            message="Tell JobWatch which roles, skills and companies you care about and we’ll watch for matching openings."
            action={
              <Button mt="sm" leftSection={<IconPlus size={18} />} onClick={() => nav('/profiles/new')}>
                New profile
              </Button>
            }
          />
        </Card>
      ) : (
        <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }} spacing="lg" className="jw-stagger">
          {data.profiles.map((p) => (
            <ProfileCard
              key={p.id}
              p={p}
              active={isActive(p.id)}
              busy={busy}
              onToggle={() => onToggle(p.id)}
              onEdit={() => nav(`/profiles/${p.id}/edit`)}
              onDelete={() => onDelete(p.id, p.name)}
            />
          ))}
        </SimpleGrid>
      )}
    </Stack>
  );
}
