import { useEffect, useState, useCallback } from 'react';
import {
  Grid, Card, Text, Stack, Group, Badge, Button, Pill, Box, UnstyledButton, Anchor,
  Skeleton, Tooltip, Collapse,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconMapPin, IconWorld, IconCircleCheck, IconExternalLink, IconX, IconBriefcase2,
  IconCalendar, IconFilterOff, IconCircleDashed, IconTargetArrow, IconAdjustmentsHorizontal,
} from '@tabler/icons-react';
import { useSearchParams } from 'react-router-dom';
import { getJobs, setApplied } from '../api.js';
import { PageHeader, EmptyState, CompanyAvatar } from '../components/ui.jsx';

/** One clickable filter row with a count pill. */
function Facet({ label, count, active, onClick }) {
  return (
    <UnstyledButton className="jw-facet" data-active={active || undefined} onClick={onClick}>
      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{label}</span>
      {count != null && <span className="jw-facet-count">{count}</span>}
    </UnstyledButton>
  );
}

function FacetSection({ title, children }) {
  return (
    <Box>
      <Text size="xs" tt="uppercase" fw={700} c="dimmed" mb={6} px={10} lts={0.6}>{title}</Text>
      <Stack gap={2}>{children}</Stack>
    </Box>
  );
}

function formatDate(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso.slice(0, 10);
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}

function JobCard({ job, onApplied }) {
  const posted = formatDate(job.postedAt);
  return (
    <Card className="jw-card-hover jw-job" padding="md">
      <Group wrap="nowrap" align="flex-start" gap="md">
        <CompanyAvatar name={job.company} />
        <Box style={{ flex: 1, minWidth: 0 }}>
          <Group justify="space-between" align="flex-start" wrap="nowrap" gap="sm">
            <Box style={{ minWidth: 0 }}>
              <Anchor
                href={job.applyUrl} target="_blank" rel="noopener noreferrer"
                className="jw-job-title" fz="md" lh={1.3}
              >
                {job.title}
                <IconExternalLink size={14} style={{ marginLeft: 6, verticalAlign: -2, opacity: 0.6 }} />
              </Anchor>
              <Text size="sm" c="dimmed" fw={500} mt={2}>{job.company}</Text>
            </Box>
            <Tooltip label="Match score against your profile">
              <Badge
                variant="gradient" gradient={{ from: 'brand.6', to: 'grape.5', deg: 135 }}
                size="lg" leftSection={<IconTargetArrow size={13} />}
                style={{ flexShrink: 0, overflow: 'visible' }}
                styles={{ label: { overflow: 'visible' } }}
              >
                {job.score}
              </Badge>
            </Tooltip>
          </Group>

          <Group gap="md" mt="sm" wrap="wrap">
            {job.location && (
              <Group gap={5} wrap="nowrap">
                <IconMapPin size={15} color="var(--mantine-color-dimmed)" />
                <Text size="sm" c="dimmed">{job.location}</Text>
              </Group>
            )}
            {posted && (
              <Group gap={5} wrap="nowrap">
                <IconCalendar size={15} color="var(--mantine-color-dimmed)" />
                <Text size="sm" c="dimmed">{posted}</Text>
              </Group>
            )}
          </Group>

          <Group justify="space-between" mt="md" wrap="wrap" gap="xs">
            <Group gap={6}>
              {job.remote && (
                <Badge color="teal" variant="light" leftSection={<IconWorld size={12} />}>Remote</Badge>
              )}
              {job.profile && <Badge variant="light" color="brand" tt="none">{job.profile}</Badge>}
            </Group>
            <Button
              size="xs"
              variant={job.applied ? 'light' : 'default'}
              color={job.applied ? 'teal' : 'gray'}
              leftSection={job.applied ? <IconCircleCheck size={15} /> : <IconCircleDashed size={15} />}
              onClick={() => onApplied(job)}
            >
              {job.applied ? 'Applied' : 'Mark applied'}
            </Button>
          </Group>
        </Box>
      </Group>
    </Card>
  );
}

function JobSkeletons() {
  return (
    <Stack gap="md">
      {[0, 1, 2, 3].map((i) => (
        <Card key={i} padding="md">
          <Group wrap="nowrap" gap="md">
            <Skeleton h={44} w={44} radius="lg" />
            <Stack gap={8} style={{ flex: 1 }}>
              <Skeleton h={14} w="55%" />
              <Skeleton h={12} w="30%" />
              <Skeleton h={12} w="70%" mt={6} />
            </Stack>
          </Group>
        </Card>
      ))}
    </Stack>
  );
}

export default function JobsPage() {
  const [sp, setSp] = useSearchParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [filtersOpen, { toggle: toggleFilters }] = useDisclosure(false);

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
  if (f.location) chips.push(['location', `Location: ${f.location}`]);
  if (f.remote != null) chips.push(['remote', f.remote ? 'Remote' : 'On-site']);
  if (f.profile) chips.push(['profile', `Profile: ${f.profile}`]);
  if (f.applied != null) chips.push(['applied', f.applied ? 'Applied' : 'Not applied']);

  const jobs = data?.jobs ?? [];

  const filtersPanel = (
  <Card padding="md">
            <Group gap={8} mb="md" px={10}>
              <IconAdjustmentsHorizontal size={18} color="var(--mantine-color-brand-6)" />
              <Text fw={700}>Filters</Text>
            </Group>
            <Stack gap="lg">
              <FacetSection title="Work type">
                <Facet
                  label="Remote" count={remoteCount('Remote')} active={f.remote === true}
                  onClick={() => update('remote', f.remote === true ? null : 'true')}
                />
                <Facet
                  label="On-site" count={remoteCount('On-site')} active={f.remote === false}
                  onClick={() => update('remote', f.remote === false ? null : 'false')}
                />
              </FacetSection>

              {data?.profileFacet?.length > 0 && (
                <FacetSection title="Profile">
                  {data.profileFacet.map((x) => (
                    <Facet
                      key={x.label} label={x.label} count={x.count} active={f.profile === x.label}
                      onClick={() => update('profile', f.profile === x.label ? null : x.label)}
                    />
                  ))}
                </FacetSection>
              )}

              {data?.locationFacet?.length > 0 && (
                <FacetSection title="Location">
                  {data.locationFacet.map((x) => (
                    <Facet
                      key={x.label} label={x.label} count={x.count} active={f.location === x.label}
                      onClick={() => update('location', f.location === x.label ? null : x.label)}
                    />
                  ))}
                </FacetSection>
              )}

              <FacetSection title="Status">
                <Facet
                  label="Applied" active={f.applied === true}
                  onClick={() => update('applied', f.applied === true ? null : 'true')}
                />
                <Facet
                  label="Not applied" active={f.applied === false}
                  onClick={() => update('applied', f.applied === false ? null : 'false')}
                />
              </FacetSection>
            </Stack>
          </Card>
  );

  return (
    <Stack gap={0}>
      <PageHeader
        icon={IconBriefcase2}
        title="Jobs"
        subtitle="Fresh openings matched to your active profiles. Narrow them down with the filters and track what you’ve applied to."
      />

      <Grid gutter="lg">
        <Grid.Col span={{ base: 12, md: 3 }}>
          <Box visibleFrom="md" style={{ position: 'sticky', top: 80 }}>{filtersPanel}</Box>
          <Box hiddenFrom="md">
            <Button
              fullWidth variant="default" size="md"
              leftSection={<IconAdjustmentsHorizontal size={18} />}
              rightSection={chips.length > 0 ? <Badge size="sm" circle>{chips.length}</Badge> : null}
              onClick={toggleFilters}
            >
              {filtersOpen ? 'Hide filters' : 'Show filters'}
            </Button>
            <Collapse in={filtersOpen}><Box mt="sm">{filtersPanel}</Box></Collapse>
          </Box>
        </Grid.Col>

        <Grid.Col span={{ base: 12, md: 9 }}>
          <Group justify="space-between" mb="md" wrap="wrap" gap="xs">
            <Group gap="xs">
              <Text fw={800} fz={22}>{data?.total ?? 0}</Text>
              <Text c="dimmed" fw={500}>{data?.total === 1 ? 'job' : 'jobs'}</Text>
              {chips.map(([key, label]) => (
                <Pill
                  key={key} withRemoveButton size="md"
                  onRemove={() => update(key, null)}
                  style={{ background: 'var(--mantine-color-brand-light)', color: 'var(--mantine-color-brand-light-color)', fontWeight: 600 }}
                >
                  {label}
                </Pill>
              ))}
            </Group>
            {chips.length > 0 && (
              <Button variant="subtle" size="xs" leftSection={<IconX size={14} />} onClick={clearAll}>
                Clear all
              </Button>
            )}
          </Group>

          {loading ? (
            <JobSkeletons />
          ) : jobs.length === 0 ? (
            <Card>
              <EmptyState
                icon={IconFilterOff}
                title="No jobs match these filters"
                message="Try removing a filter, or activate more profiles to widen what we look for."
                action={chips.length > 0 && (
                  <Button mt="sm" variant="light" onClick={clearAll}>Clear filters</Button>
                )}
              />
            </Card>
          ) : (
            <Stack gap="md" className="jw-stagger">
              {jobs.map((j) => <JobCard key={j.id} job={j} onApplied={onApplied} />)}
            </Stack>
          )}
        </Grid.Col>
      </Grid>
    </Stack>
  );
}
