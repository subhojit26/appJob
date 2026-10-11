import axios from 'axios';

/**
 * Thin API client for the JobWatch REST endpoints. In dev, Vite proxies `/api`
 * to the Spring Boot app; in prod the SPA is served from the same origin.
 */
const http = axios.create({ baseURL: '/api' });

// ── Profiles ────────────────────────────────────────────────────────────────
export const getProfiles = () => http.get('/profiles').then((r) => r.data);
export const getProfile = (id) => http.get(`/profiles/${id}`).then((r) => r.data);
export const createProfile = (payload) => http.post('/profiles', payload).then((r) => r.data);
export const updateProfile = (id, payload) =>
  http.put(`/profiles/${id}`, payload).then((r) => r.data);
export const deleteProfile = (id) => http.delete(`/profiles/${id}`).then((r) => r.data);
export const toggleActive = (id) =>
  http.post(`/profiles/${id}/toggle-active`).then((r) => r.data);

// ── Analytics & jobs ────────────────────────────────────────────────────────
export const getAnalytics = () => http.get('/analytics').then((r) => r.data);
export const getJobs = (params) => http.get('/jobs', { params }).then((r) => r.data);
export const setApplied = (id, applied) =>
  http.post(`/jobs/${id}/applied`, null, { params: { applied } }).then((r) => r.data);

export default http;

