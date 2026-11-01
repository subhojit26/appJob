import axios from 'axios';

/**
 * Thin API client for the JobWatch REST endpoints. In dev, Vite proxies `/api`
 * to the Spring Boot app; in prod the SPA is served from the same origin.
 */
const http = axios.create({ baseURL: '/api' });

function asArray(v) {
  return Array.isArray(v) ? v : [];
}

function asObject(v) {
  return v && typeof v === 'object' && !Array.isArray(v) ? v : {};
}

function normalizeProfiles(data) {
  if (Array.isArray(data)) {
    return { profiles: data, activeIds: [] };
  }
  const obj = asObject(data);
  return {
    ...obj,
    profiles: asArray(obj.profiles),
    activeIds: asArray(obj.activeIds),
  };
}

function normalizeJobs(data) {
  const obj = asObject(data);
  return {
    ...obj,
    jobs: asArray(obj.jobs),
    profileFacet: asArray(obj.profileFacet),
    locationFacet: asArray(obj.locationFacet),
    remoteFacet: asArray(obj.remoteFacet),
    filter: asObject(obj.filter),
    total: Number.isFinite(obj.total) ? obj.total : asArray(obj.jobs).length,
  };
}

function normalizeAnalytics(data) {
  const obj = asObject(data);
  return {
    ...obj,
    seenSeries: asArray(obj.seenSeries),
    emailedSeries: asArray(obj.emailedSeries),
    byProfile: asArray(obj.byProfile),
    bySource: asArray(obj.bySource),
    topCompanies: asArray(obj.topCompanies),
  };
}

// ── Profiles ────────────────────────────────────────────────────────────────
export const getProfiles = () => http.get('/profiles').then((r) => normalizeProfiles(r.data));
export const getProfile = (id) => http.get(`/profiles/${id}`).then((r) => r.data);
export const createProfile = (payload) => http.post('/profiles', payload).then((r) => r.data);
export const updateProfile = (id, payload) =>
  http.put(`/profiles/${id}`, payload).then((r) => r.data);
export const deleteProfile = (id) => http.delete(`/profiles/${id}`).then((r) => r.data);
export const toggleActive = (id) =>
  http.post(`/profiles/${id}/toggle-active`).then((r) => ({ activeIds: asArray(asObject(r.data).activeIds) }));

// ── Analytics & jobs ────────────────────────────────────────────────────────
export const getAnalytics = () => http.get('/analytics').then((r) => normalizeAnalytics(r.data));
export const getJobs = (params) => http.get('/jobs', { params }).then((r) => normalizeJobs(r.data));
export const setApplied = (id, applied) =>
  http.post(`/jobs/${id}/applied`, null, { params: { applied } }).then((r) => r.data);

export default http;

