import { useEffect, useState } from 'react';
import {
  Card, Group, Title, Button, Table, Badge, ActionIcon, Text, Stack, Tooltip,
  Loader, Center, Box,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconPlus, IconEdit, IconTrash, IconCircleCheck, IconCircle,
} from '@tabler/icons-react';
import { useNavigate } from 'react-router-dom';
import { getProfiles, toggleActive, deleteProfile } from '../api.js';

function TagList({ items, empty = 'any' }) {
  if (!items || items.length === 0) return <Text c="dimmed" size="xs">{empty}</Text>;
  return (
    <Group gap={4}>
      {items.map((t) => (
        <Badge key={t} variant="light" color="indigo" radius="sm" size="sm" tt="none">
          {t}
        </Badge>
      ))}
    </Group>
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
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (id, name) => {
    if (!window.confirm(`Delete profile "${name}"?`)) return;
    await deleteProfile(id);
    notifications.show({ color: 'gray', message: `Deleted "${name}"` });
    load();
  };

  if (!data) {
    return <Center h={300}><Loader /></Center>;
  }

  const rows = data.profiles.map((p) => {
    const active = isActive(p.id);
    return (
      <Table.Tr key={p.id} bg={active ? 'var(--mantine-color-green-0)' : undefined}>
        <Table.Td>
          <Button
            size="xs"
            variant={active ? 'light' : 'filled'}
            color={active ? 'green' : 'blue'}
            leftSection={active ? <IconCircleCheck size={15} /> : <IconCircle size={15} />}
            loading={busy}
            onClick={() => onToggle(p.id)}
          >
            {active ? 'Active' : 'Set active'}
          </Button>
        </Table.Td>
        <Table.Td>
          <Text fw={600}>{p.name}</Text>
          {!p.enabled && <Text c="dimmed" size="xs">(disabled)</Text>}
        </Table.Td>
        <Table.Td><TagList items={p.includeTitle} empty="—" /></Table.Td>
        <Table.Td><TagList items={p.stack} empty="—" /></Table.Td>
        <Table.Td><TagList items={p.companies} /></Table.Td>
        <Table.Td>
          <Text size="sm">
            {(p.expMin ?? 0)}–{p.expMax ?? '∞'} yrs
          </Text>
        </Table.Td>
        <Table.Td>
          <Group gap={6} wrap="nowrap">
            <Tooltip label="Edit">
              <ActionIcon variant="subtle" onClick={() => nav(`/profiles/${p.id}/edit`)}>
                <IconEdit size={17} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Delete">
              <ActionIcon variant="subtle" color="red" onClick={() => onDelete(p.id, p.name)}>
                <IconTrash size={17} />
              </ActionIcon>
            </Tooltip>
          </Group>
        </Table.Td>
      </Table.Tr>
    );
  });

  return (
    <Stack gap="md">
      <Group justify="space-between" align="center">
        <Box>
          <Title order={2}>My Profiles</Title>
          <Text c="dimmed" size="sm" mt={4}>
            Activate one or more profiles — jobs matching <b>any</b> active profile are shown and emailed.
          </Text>
        </Box>
        <Button leftSection={<IconPlus size={18} />} onClick={() => nav('/profiles/new')}>
          New profile
        </Button>
      </Group>

      <Card withBorder shadow="sm" padding="0" radius="md">
        <Table verticalSpacing="sm" horizontalSpacing="md" highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Active</Table.Th>
              <Table.Th>Name</Table.Th>
              <Table.Th>Role keywords</Table.Th>
              <Table.Th>Stack</Table.Th>
              <Table.Th>Companies</Table.Th>
              <Table.Th>Experience</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {rows.length > 0 ? rows : (
              <Table.Tr>
                <Table.Td colSpan={7}>
                  <Text c="dimmed" ta="center" py="lg">No profiles yet. Create one to start.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
  );
}

