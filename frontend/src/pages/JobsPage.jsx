import { useEffect, useState, useCallback } from 'react';
import {
  Grid, Card, Title, Text, Stack, Group, Table, Badge, Button, Anchor, Loader, Center,
  Pill, UnstyledButton, Box,
} from '@mantine/core';
import { IconMapPin, IconWorld, IconCircleCheck, IconExternalLink, IconX } from '@tabler/icons-react';
import { useSearchParams } from 'react-router-dom';
import { getJobs, setApplied } from '../api.js';

function FacetGroup({ title, items, activeLabel, onPick }) {
  if (!items || items.length === 0) return null;
  return (
    <Box mb="md">
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb={6}>{title}</Text>
      <Stack gap={2}>
        {items.map((f) => {
          const sel = activeLabel === f.label;
          return (
            <UnstyledButton
              key={f.label}
              onClick={() => onPick(sel ? null : f.label)}
              style={{
                display: 'flex', justifyContent: 'space-between', padding: '4px 8px',
                borderRadius: 6, fontSize: 13,
                background: sel ? 'var(--mantine-color-indigo-0)' : undefined,
                color: sel ? 'var(--mantine-color-indigo-7)' : 'var(--mantine-color-gray-7)',
                fontWeight: sel ? 600 : 400,
              }}
            >
              <span>{f.label}</span>
              <span style={{ color: 'var(--mantine-color-gray-5)' }}>{f.count}</span>
            </UnstyledButton>
          );
        })}
      </Stack>
    </Box>
  );
}

export default function JobsPage() {
  const [sp, setSp] = useSearchParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  const params = {
    location: sp.get('location') || undefined,
    remote: sp.get('remote') || undefined,
    profile: sp.get('profile') || undefined,
    applied: sp.get('applied') || undefined,
  };

  const load = useCallback(() => {
    setLoading(true);
    getJobs(params).then(setData).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sp]);

  useEffect(() => { load(); }, [load]);

  const update = (key, value) => {
    const next = new URLSearchParams(sp);
    if (value === null || value === undefined) next.delete(key);
    else next.set(key, value);
    setSp(next);
  };

  const clearAll = () => setSp(new URLSearchParams());

  const onApplied = async (job) => {
    await setApplied(job.id, !job.applied);
    load();
  };

  const f = data?.filter ?? {};
  const remoteCount = (label) => {
    const hit = (data?.remoteFacet ?? []).find((x) => x.label === label);
    return hit ? hit.count : 0;
  };

  const chips = [];
  if (f.location) chips.push(['location', `location: ${f.location}`]);
  if (f.remote != null) chips.push(['remote', f.remote ? 'remote' : 'on-site']);
  if (f.profile) chips.push(['profile', `profile: ${f.profile}`]);
  if (f.applied != null) chips.push(['applied', f.applied ? 'applied' : 'not applied']);

  return (
    <Grid gutter="lg">
      <Grid.Col span={{ base: 12, sm: 3 }}>
        <Card withBorder shadow="sm" radius="md" padding="md">
          <Box mb="md">
            <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb={6}>Remote</Text>
            <Stack gap={2}>
              <UnstyledButton
                onClick={() => update('remote', f.remote === true ? null : 'true')}
                style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 8px', borderRadius: 6, fontSize: 13, fontWeight: f.remote === true ? 600 : 400, background: f.remote === true ? 'var(--mantine-color-indigo-0)' : undefined }}
              >
                <span>Remote</span><span style={{ color: '#9ca3af' }}>{remoteCount('Remote')}</span>
              </UnstyledButton>
              <UnstyledButton
                onClick={() => update('remote', f.remote === false ? null : 'false')}
                style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 8px', borderRadius: 6, fontSize: 13, fontWeight: f.remote === false ? 600 : 400, background: f.remote === false ? 'var(--mantine-color-indigo-0)' : undefined }}
              >
                <span>On-site</span><span style={{ color: '#9ca3af' }}>{remoteCount('On-site')}</span>
              </UnstyledButton>
            </Stack>
          </Box>
          <FacetGroup title="Profile" items={data?.profileFacet} activeLabel={f.profile} onPick={(v) => update('profile', v)} />
          <FacetGroup title="Location" items={data?.locationFacet} activeLabel={f.location} onPick={(v) => update('location', v)} />
          <Box>
            <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb={6}>Applied</Text>
            <Stack gap={2}>
              <UnstyledButton onClick={() => update('applied', f.applied === true ? null : 'true')}
                style={{ padding: '4px 8px', borderRadius: 6, fontSize: 13, fontWeight: f.applied === true ? 600 : 400, background: f.applied === true ? 'var(--mantine-color-indigo-0)' : undefined }}>
                Applied
              </UnstyledButton>
              <UnstyledButton onClick={() => update('applied', f.applied === false ? null : 'false')}
                style={{ padding: '4px 8px', borderRadius: 6, fontSize: 13, fontWeight: f.applied === false ? 600 : 400, background: f.applied === false ? 'var(--mantine-color-indigo-0)' : undefined }}>
                Not applied
              </UnstyledButton>
            </Stack>
          </Box>
        </Card>
      </Grid.Col>

      <Grid.Col span={{ base: 12, sm: 9 }}>
        <Group justify="space-between" mb="sm">
          <Group gap="xs">
            <Title order={3}>{data?.total ?? 0}</Title>
            <Text c="dimmed">jobs</Text>
            {chips.map(([key, label]) => (
              <Pill key={key} withRemoveButton onRemove={() => update(key, null)}>{label}</Pill>
            ))}
            {chips.length > 0 && (
              <Anchor size="sm" onClick={clearAll} c="blue">
                <Group gap={2}><IconX size={13} /> clear all</Group>
              </Anchor>
            )}
          </Group>
        </Group>

        <Card withBorder shadow="sm" radius="md" padding="0">
          {loading ? (
            <Center h={200}><Loader /></Center>
          ) : (
            <Table verticalSpacing="sm" horizontalSpacing="md" highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Title</Table.Th>
                  <Table.Th>Company</Table.Th>
                  <Table.Th>Location</Table.Th>
                  <Table.Th>Remote</Table.Th>
                  <Table.Th>Profile</Table.Th>
                  <Table.Th>Score</Table.Th>
                  <Table.Th>Posted</Table.Th>
                  <Table.Th>Applied</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(data?.jobs ?? []).map((j) => (
                  <Table.Tr key={j.id}>
                    <Table.Td>
                      <Anchor href={j.applyUrl} target="_blank" fw={500}>
                        <Group gap={4} wrap="nowrap">{j.title}<IconExternalLink size={13} /></Group>
                      </Anchor>
                    </Table.Td>
                    <Table.Td>{j.company}</Table.Td>
                    <Table.Td><Group gap={4}><IconMapPin size={13} color="#9ca3af" />{j.location}</Group></Table.Td>
                    <Table.Td>
                      {j.remote
                        ? <Badge color="green" variant="light" leftSection={<IconWorld size={12} />}>remote</Badge>
                        : <Text c="dimmed">—</Text>}
                    </Table.Td>
                    <Table.Td><Badge variant="light" color="indigo" tt="none">{j.profile}</Badge></Table.Td>
                    <Table.Td>{j.score}</Table.Td>
                    <Table.Td>{j.postedAt ? j.postedAt.slice(0, 10) : '—'}</Table.Td>
                    <Table.Td>
                      <Button
                        size="xs"
                        variant={j.applied ? 'light' : 'default'}
                        color={j.applied ? 'green' : 'gray'}
                        leftSection={j.applied ? <IconCircleCheck size={14} /> : null}
                        onClick={() => onApplied(j)}
                      >
                        {j.applied ? 'applied' : 'mark applied'}
                      </Button>
                    </Table.Td>
                  </Table.Tr>
                ))}
                {(data?.jobs ?? []).length === 0 && (
                  <Table.Tr>
                    <Table.Td colSpan={8}>
                      <Text c="dimmed" ta="center" py="lg">No jobs match these filters yet.</Text>
                    </Table.Td>
                  </Table.Tr>
                )}
              </Table.Tbody>
            </Table>
          )}
        </Card>
      </Grid.Col>
    </Grid>
  );
}


