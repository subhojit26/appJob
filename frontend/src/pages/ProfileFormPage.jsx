import { useEffect, useState } from 'react';
import {
  Card, Stack, TextInput, TagsInput, NumberInput, Switch, Group, Button, Text, Breadcrumbs,
  Anchor, Box, ThemeIcon, Divider,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconDeviceFloppy, IconArrowLeft, IconUserEdit, IconUserPlus, IconTargetArrow,
  IconCode, IconBuilding, IconSettings,
} from '@tabler/icons-react';
import { useNavigate, useParams } from 'react-router-dom';
import { getProfile, createProfile, updateProfile } from '../api.js';
import { PageHeader, PageLoader } from '../components/ui.jsx';

const EMPTY = {
  name: '', includeTitle: [], stack: [], excludeTitle: [], companies: [],
  expMin: null, expMax: null, enabled: true,
};

function Section({ icon: Icon, title, description, children }) {
  return (
    <Box>
      <Group gap="sm" mb="md" wrap="nowrap">
        <ThemeIcon variant="light" size={34} radius="md"><Icon size={18} stroke={1.8} /></ThemeIcon>
        <Box>
          <Text fw={700}>{title}</Text>
          <Text size="xs" c="dimmed">{description}</Text>
        </Box>
      </Group>
      <Stack gap="md">{children}</Stack>
    </Box>
  );
}

export default function ProfileFormPage() {
  const { id } = useParams();
  const editing = Boolean(id);
  const nav = useNavigate();
  const [form, setForm] = useState(editing ? null : EMPTY);
  const [saving, setSaving] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    if (editing) {
      getProfile(id)
        .then((r) => setForm({
          name: r.name ?? '',
          includeTitle: r.includeTitle ?? [],
          stack: r.stack ?? [],
          excludeTitle: r.excludeTitle ?? [],
          companies: r.companies ?? [],
          expMin: r.expMin ?? null,
          expMax: r.expMax ?? null,
          enabled: r.enabled ?? true,
        }))
        .catch(() => notifications.show({ color: 'red', message: 'Failed to load profile' }));
    }
  }, [id, editing]);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async () => {
    if (!form.name.trim()) { setErr('Profile name is required'); return; }
    setErr('');
    setSaving(true);
    try {
      if (editing) {
        await updateProfile(id, form);
      } else {
        await createProfile(form);
      }
      notifications.show({ color: 'teal', message: `Profile "${form.name}" saved` });
      nav('/profiles');
    } catch {
      notifications.show({ color: 'red', message: 'Save failed' });
    } finally {
      setSaving(false);
    }
  };

  if (!form) return <PageLoader />;

  return (
    <Box maw={760} mx="auto">
      <Breadcrumbs mb="md" separatorMargin="xs">
        <Anchor size="sm" onClick={() => nav('/profiles')}>My Profiles</Anchor>
        <Text size="sm" c="dimmed">{editing ? 'Edit' : 'New'}</Text>
      </Breadcrumbs>

      <PageHeader
        icon={editing ? IconUserEdit : IconUserPlus}
        title={editing ? 'Edit profile' : 'New profile'}
        subtitle="Describe the kind of role you want — JobWatch scores every opening against it."
      />

      <Card padding="xl">
        <Stack gap="xl">
          <Section icon={IconTargetArrow} title="The role" description="What should the job be called?">
            <TextInput
              label="Profile name"
              description="e.g. backend-java"
              placeholder="Give this profile a short name"
              withAsterisk
              size="md"
              value={form.name}
              error={err || undefined}
              onChange={(e) => set('name', e.currentTarget.value)}
            />
            <TagsInput
              label="Role / title keywords"
              description="Press Enter to add — e.g. java, spring, backend"
              placeholder="Add keyword"
              size="md"
              value={form.includeTitle}
              onChange={(v) => set('includeTitle', v)}
            />
            <TagsInput
              label="Exclude keywords"
              description="Skip jobs whose title contains — e.g. javascript, intern"
              placeholder="Add keyword to exclude"
              size="md"
              value={form.excludeTitle}
              onChange={(v) => set('excludeTitle', v)}
            />
          </Section>

          <Divider />

          <Section icon={IconCode} title="Skills" description="Matched against the job description.">
            <TagsInput
              label="Tech stack / skills"
              description="e.g. spring boot, kafka, aws"
              placeholder="Add skill"
              size="md"
              value={form.stack}
              onChange={(v) => set('stack', v)}
            />
          </Section>

          <Divider />

          <Section icon={IconBuilding} title="Companies & experience" description="Narrow down who and how senior.">
            <TagsInput
              label="Preferred companies"
              description="Leave empty for any company"
              placeholder="Add company"
              size="md"
              value={form.companies}
              onChange={(v) => set('companies', v)}
            />
            <Group grow align="flex-start">
              <NumberInput
                label="Min experience (yrs)"
                size="md" min={0} max={50}
                value={form.expMin ?? ''}
                onChange={(v) => set('expMin', v === '' ? null : v)}
              />
              <NumberInput
                label="Max experience (yrs)"
                size="md" min={0} max={50}
                value={form.expMax ?? ''}
                onChange={(v) => set('expMax', v === '' ? null : v)}
              />
            </Group>
          </Section>

          <Divider />

          <Section icon={IconSettings} title="Status" description="Control whether the pipeline uses this profile.">
            <Switch
              size="md"
              color="teal"
              label="Enabled"
              description="Used by the job-matching pipeline"
              checked={form.enabled}
              onChange={(e) => set('enabled', e.currentTarget.checked)}
            />
          </Section>

          <Group justify="flex-end" mt="sm">
            <Button variant="default" size="md" leftSection={<IconArrowLeft size={18} />} onClick={() => nav('/profiles')}>
              Cancel
            </Button>
            <Button size="md" leftSection={<IconDeviceFloppy size={18} />} loading={saving} onClick={submit}>
              Save profile
            </Button>
          </Group>
        </Stack>
      </Card>
    </Box>
  );
}
