import { useEffect, useState } from 'react';
import {
  SimpleGrid, Card, Text, Title, Stack, Group, Box, Progress, ThemeIcon, useComputedColorScheme,
} from '@mantine/core';
import {
  IconEye, IconTargetArrow, IconMail, IconChecks, IconChartAreaLine, IconClock24,
  IconCalendarWeek, IconCalendarMonth, IconInbox,
} from '@tabler/icons-react';
import {
  ResponsiveContainer, AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid,
} from 'recharts';
import { getAnalytics } from '../api.js';
import { BRAND, OK } from '../theme.js';
import { PageHeader, PageLoader, StatCard, EmptyState } from '../components/ui.jsx';

function ChartCard({ title, subtitle, children }) {
  return (
    <Card>
      <Title order={5}>{title}</Title>
      <Text size="xs" c="dimmed" mb="md">{subtitle}</Text>
      {children}
    </Card>
  );
}

function Breakdown({ title, rows, color = 'brand' }) {
  const max = Math.max(1, ...(rows ?? []).map((r) => r.count));
  return (
    <Card>
      <Title order={5} mb="md">{title}</Title>
      {(!rows || rows.length === 0) ? (
        <Text c="dimmed" size="sm">No data yet.</Text>
      ) : (
        <Stack gap="md">
          {rows.map((r) => (
            <Box key={r.label}>
              <Group justify="space-between" mb={5} wrap="nowrap">
                <Text size="sm" fw={500} truncate>{r.label}</Text>
                <Text size="sm" fw={700}>{r.count.toLocaleString()}</Text>
              </Group>
              <Progress value={(r.count / max) * 100} color={color} size="sm" radius="xl" />
            </Box>
          ))}
        </Stack>
      )}
    </Card>
  );
}

export default function AnalyticsPage() {
  const [d, setD] = useState(null);
  const scheme = useComputedColorScheme('light');
  const dark = scheme === 'dark';

  useEffect(() => { getAnalytics().then(setD).catch(() => setD({})); }, []);

  if (!d) return <PageLoader />;

  const grid = dark ? '#2a2c3a' : '#eceef5';
  const axis = dark ? '#8b8fa3' : '#868e96';
  const tooltipStyle = {
    background: dark ? '#1a1b26' : '#ffffff',
    border: `1px solid ${dark ? '#2a2c3a' : '#e8eaf2'}`,
    borderRadius: 10,
    boxShadow: '0 8px 24px -6px rgba(16,24,40,0.2)',
    color: dark ? '#e9ecef' : '#212529',
    fontSize: 12,
  };

  const seen = (d.seenSeries ?? []).map((x) => ({ day: x.label?.slice(5), count: x.count }));
  const emailed = (d.emailedSeries ?? []).map((x) => ({ day: x.label?.slice(5), count: x.count }));
  const hasData = (d.totalJobs ?? 0) > 0;

  return (
    <Stack gap={0}>
      <PageHeader
        icon={IconChartAreaLine}
        title="Analytics"
        subtitle="How many openings JobWatch has found, matched and sent to you."
      />

      {!hasData && (
        <Card mb="xl">
          <EmptyState
            icon={IconInbox}
            title="Nothing to analyse yet"
            message="Once JobWatch has run and found openings, your trends and breakdowns will show up here."
          />
        </Card>
      )}

      <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }} spacing="lg" className="jw-stagger">
        <StatCard icon={IconEye} color="brand" value={d.totalJobs} label="Jobs seen" hint="All time" />
        <StatCard icon={IconTargetArrow} color="grape" value={d.totalMatched} label="Matched" hint="To a profile" />
        <StatCard icon={IconMail} color="teal" value={d.totalEmailed} label="Emailed" hint="Sent to you" />
        <StatCard icon={IconChecks} color="green" value={d.totalApplied} label="Applied" hint="Marked as applied" />
      </SimpleGrid>

      <Text fw={700} fz="lg" mt={36} mb="md">New openings</Text>
      <SimpleGrid cols={{ base: 1, xs: 3 }} spacing="lg" className="jw-stagger">
        <StatCard icon={IconClock24} color="cyan" value={d.seenToday} label="Last 24 hours" />
        <StatCard icon={IconCalendarWeek} color="blue" value={d.seenWeek} label="Last 7 days" />
        <StatCard icon={IconCalendarMonth} color="indigo" value={d.seenMonth} label="Last 30 days" />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 2 }} spacing="lg" mt={36}>
        <ChartCard title="Openings seen" subtitle="Per day · last 30 days">
          <ResponsiveContainer width="100%" height={240}>
            <AreaChart data={seen} margin={{ left: -20, right: 8, top: 8 }}>
              <defs>
                <linearGradient id="gSeen" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={BRAND} stopOpacity={0.45} />
                  <stop offset="95%" stopColor={BRAND} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={grid} />
              <XAxis dataKey="day" fontSize={11} tickLine={false} axisLine={false} minTickGap={16} stroke={axis} />
              <YAxis fontSize={11} tickLine={false} axisLine={false} allowDecimals={false} stroke={axis} />
              <Tooltip contentStyle={tooltipStyle} cursor={{ stroke: BRAND, strokeOpacity: 0.3 }} />
              <Area type="monotone" dataKey="count" name="Openings" stroke={BRAND} strokeWidth={2.5} fill="url(#gSeen)" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Jobs emailed" subtitle="Per day · last 30 days">
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={emailed} margin={{ left: -20, right: 8, top: 8 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={grid} />
              <XAxis dataKey="day" fontSize={11} tickLine={false} axisLine={false} minTickGap={16} stroke={axis} />
              <YAxis fontSize={11} tickLine={false} axisLine={false} allowDecimals={false} stroke={axis} />
              <Tooltip contentStyle={tooltipStyle} cursor={{ fill: BRAND, fillOpacity: 0.08 }} />
              <Bar dataKey="count" name="Emailed" fill={OK} radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, md: 3 }} spacing="lg" mt="lg">
        <Breakdown title="By profile" rows={d.byProfile} color="brand" />
        <Breakdown title="By source" rows={d.bySource} color="grape" />
        <Breakdown title="Top companies" rows={d.topCompanies} color="teal" />
      </SimpleGrid>
    </Stack>
  );
}
