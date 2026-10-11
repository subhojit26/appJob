import { useEffect, useState } from 'react';
import {
  SimpleGrid, Card, Text, Title, Stack, Group, Loader, Center, ThemeIcon, Box, Divider,
} from '@mantine/core';
import {
  IconEye, IconTargetArrow, IconMail, IconChecks, IconCalendarStats,
} from '@tabler/icons-react';
import {
  ResponsiveContainer, AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid,
} from 'recharts';
import { getAnalytics } from '../api.js';
import { BRAND, OK } from '../theme.js';

function Kpi({ icon: Icon, color, value, label }) {
  return (
    <Card withBorder shadow="sm" radius="md" padding="lg">
      <Group justify="space-between" align="flex-start">
        <Box>
          <Text fz={32} fw={700} c={color}>{value ?? 0}</Text>
          <Text size="sm" c="dimmed" mt={2}>{label}</Text>
        </Box>
        <ThemeIcon variant="light" color={color} size={42} radius="md">
          <Icon size={22} />
        </ThemeIcon>
      </Group>
    </Card>
  );
}

function Breakdown({ title, rows }) {
  return (
    <Card withBorder shadow="sm" radius="md" padding="lg">
      <Title order={5} mb="sm">{title}</Title>
      <Stack gap={6}>
        {(!rows || rows.length === 0) && <Text c="dimmed" size="sm">No data yet.</Text>}
        {rows?.map((r) => (
          <Group key={r.label} justify="space-between">
            <Text size="sm">{r.label}</Text>
            <Text size="sm" fw={600}>{r.count}</Text>
          </Group>
        ))}
      </Stack>
    </Card>
  );
}

export default function AnalyticsPage() {
  const [d, setD] = useState(null);

  useEffect(() => { getAnalytics().then(setD).catch(() => setD({})); }, []);

  if (!d) return <Center h={300}><Loader /></Center>;

  const seen = (d.seenSeries ?? []).map((x) => ({ day: x.label?.slice(5), count: x.count }));
  const emailed = (d.emailedSeries ?? []).map((x) => ({ day: x.label?.slice(5), count: x.count }));

  return (
    <Stack gap="lg">
      <Title order={2}>Analytics</Title>

      <SimpleGrid cols={{ base: 1, xs: 2, md: 4 }} spacing="md">
        <Kpi icon={IconEye} color="blue" value={d.totalJobs} label="Jobs seen (total)" />
        <Kpi icon={IconTargetArrow} color="indigo" value={d.totalMatched} label="Matched to a profile" />
        <Kpi icon={IconMail} color="teal" value={d.totalEmailed} label="Emailed to you" />
        <Kpi icon={IconChecks} color="green" value={d.totalApplied} label="Marked applied" />
      </SimpleGrid>

      <Box>
        <Group gap={8} mb="xs">
          <IconCalendarStats size={18} color={BRAND} />
          <Title order={5}>New openings seen</Title>
        </Group>
        <SimpleGrid cols={{ base: 1, xs: 3 }} spacing="md">
          <Kpi icon={IconEye} color="blue" value={d.seenToday} label="in the last day" />
          <Kpi icon={IconEye} color="blue" value={d.seenWeek} label="in the last week" />
          <Kpi icon={IconEye} color="blue" value={d.seenMonth} label="in the last month" />
        </SimpleGrid>
      </Box>

      <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
        <Card withBorder shadow="sm" radius="md" padding="lg">
          <Title order={5} mb="sm">Openings seen / day (30d)</Title>
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={seen} margin={{ left: -20, right: 8, top: 4 }}>
              <defs>
                <linearGradient id="gSeen" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={BRAND} stopOpacity={0.5} />
                  <stop offset="95%" stopColor={BRAND} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eef0f3" />
              <XAxis dataKey="day" fontSize={11} tickLine={false} axisLine={false} minTickGap={16} />
              <YAxis fontSize={11} tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip />
              <Area type="monotone" dataKey="count" stroke={BRAND} strokeWidth={2} fill="url(#gSeen)" />
            </AreaChart>
          </ResponsiveContainer>
        </Card>
        <Card withBorder shadow="sm" radius="md" padding="lg">
          <Title order={5} mb="sm">Jobs emailed / day (30d)</Title>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={emailed} margin={{ left: -20, right: 8, top: 4 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eef0f3" />
              <XAxis dataKey="day" fontSize={11} tickLine={false} axisLine={false} minTickGap={16} />
              <YAxis fontSize={11} tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="count" fill={OK} radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      </SimpleGrid>

      <Divider />

      <SimpleGrid cols={{ base: 1, md: 3 }} spacing="md">
        <Breakdown title="By profile" rows={d.byProfile} />
        <Breakdown title="By source" rows={d.bySource} />
        <Breakdown title="Top companies" rows={d.topCompanies} />
      </SimpleGrid>
    </Stack>
  );
}

