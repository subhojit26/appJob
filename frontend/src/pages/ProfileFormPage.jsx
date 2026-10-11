import { useEffect, useState } from 'react';
import {
  Card, Title, Stack, TextInput, TagsInput, NumberInput, Switch, Group, Button,
  Text, Breadcrumbs, Anchor, Loader, Center,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconDeviceFloppy, IconArrowLeft } from '@tabler/icons-react';
import { useNavigate, useParams } from 'react-router-dom';
import { getProfile, createProfile, updateProfile } from '../api.js';

const EMPTY = {
  name: '', includeTitle: [], stack: [], excludeTitle: [], companies: [],
  expMin: null, expMax: null, enabled: true,
};

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
      notifications.show({ color: 'green', message: `Profile "${form.name}" saved` });
      nav('/profiles');
    } catch {
      notifications.show({ color: 'red', message: 'Save failed' });
    } finally {
      setSaving(false);
    }
  };

  if (!form) return <Center h={300}><Loader /></Center>;

  return (
    <Stack gap="md" maw={680} mx="auto">
      <Breadcrumbs>
        <Anchor onClick={() => nav('/profiles')} c="blue">My Profiles</Anchor>
        <Text>{editing ? 'Edit' : 'New'}</Text>
      </Breadcrumbs>

      <Title order={2}>{editing ? 'Edit profile' : 'New profile'}</Title>

      <Card withBorder shadow="sm" radius="md" padding="lg">
        <Stack gap="md">
          <TextInput
            label="Profile name"
            description="e.g. backend-java"
            withAsterisk
            value={form.name}
            error={err || undefined}
            onChange={(e) => set('name', e.currentTarget.value)}
          />
          <TagsInput
            label="Role / title keywords"
            description="Press Enter to add — e.g. java, spring, backend"
            value={form.includeTitle}
            onChange={(v) => set('includeTitle', v)}
          />
          <TagsInput
            label="Tech stack / skills"
            description="Matched against the job description — e.g. spring boot, kafka, aws"
            value={form.stack}
            onChange={(v) => set('stack', v)}
          />
          <TagsInput
            label="Exclude keywords"
            description="Skip if the title contains — e.g. javascript, intern"
            value={form.excludeTitle}
            onChange={(v) => set('excludeTitle', v)}
          />
          <TagsInput
            label="Preferred companies"
            description="Leave empty for any company"
            value={form.companies}
            onChange={(v) => set('companies', v)}
          />
          <Group grow>
            <NumberInput
              label="Min experience (yrs)"
              min={0} max={50}
              value={form.expMin ?? ''}
              onChange={(v) => set('expMin', v === '' ? null : v)}
            />
            <NumberInput
              label="Max experience (yrs)"
              min={0} max={50}
              value={form.expMax ?? ''}
              onChange={(v) => set('expMax', v === '' ? null : v)}
            />
          </Group>
          <Switch
            label="Enabled (used by the pipeline)"
            checked={form.enabled}
            onChange={(e) => set('enabled', e.currentTarget.checked)}
          />

          <Group mt="sm">
            <Button leftSection={<IconDeviceFloppy size={18} />} loading={saving} onClick={submit}>
              Save profile
            </Button>
            <Button variant="default" leftSection={<IconArrowLeft size={18} />} onClick={() => nav('/profiles')}>
              Cancel
            </Button>
          </Group>
        </Stack>
      </Card>
    </Stack>
  );
}

