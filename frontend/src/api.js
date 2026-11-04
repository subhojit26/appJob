import axios from 'axios';

/**
 * Thin API client for the JobWatch REST endpoints. In dev, Vite proxies `/api`
 * to the Spring Boot app; in prod the SPA is served from the same origin.
 */
const envBase = (import.meta.env.VITE_API_BASE_URL || '').trim();
const normalizedBase = envBase.replace(/\/$/, '');
const apiBase = normalizedBase ? `${normalizedBase}/api` : '/api';
const http = axios.create({ baseURL: apiBase, timeout: 20000 });

// ── Cold-start resilience ─────────────────────────────────────────────────────
// The backend (Fly) can be cold/booting on first load, returning 502/503/504 or a
// network error (no CORS headers on the proxy error page). Instead of surfacing a
// confusing CORS/"failed to load" error, transparently retry a few times with
// exponential backoff so the first page load self-heals once the app is up.
const MAX_RETRIES = 4;
const RETRYABLE_STATUS = new Set([429, 502, 503, 504]);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Lightweight console logging to help debug prod (enable/disable via VITE_DEBUG_API).
const DEBUG = String(import.meta.env.VITE_DEBUG_API ?? 'true') !== 'false';
const log = (...args) => { if (DEBUG) console.log('[api]', ...args); };

http.interceptors.request.use((config) => {
  config.__start = Date.now();
  log('→', (config.method || 'get').toUpperCase(), config.baseURL + config.url,
    config.params ? { params: config.params } : '');
  return config;
});

http.interceptors.response.use(
  (response) => {
    const ms = response.config.__start ? Date.now() - response.config.__start : '?';
    const d = response.data;
    const summary = d && typeof d === 'object'
      ? { total: d.total, jobs: Array.isArray(d.jobs) ? d.jobs.length : undefined,
          profiles: Array.isArray(d.profiles) ? d.profiles.length : undefined,
          profileFacet: Array.isArray(d.profileFacet) ? d.profileFacet.length : undefined }
      : d;
    log('←', response.status, response.config.url, `${ms}ms`, summary);
    return response;
  },
  async (error) => {
    const config = error.config;
    if (!config) {
      log('✗ no-config error', error.message);
      return Promise.reject(error);
    }
    // No response at all (network/CORS-on-error) or a retryable gateway status.
    const status = error.response?.status;
    const isRetryable = status == null || RETRYABLE_STATUS.has(status);
    config.__retryCount = config.__retryCount || 0;

    if (isRetryable && config.__retryCount < MAX_RETRIES) {
      config.__retryCount += 1;
      // 0.8s, 1.6s, 3.2s, 6.4s (+ jitter) — covers typical JVM cold-start windows.
      const backoff = 800 * 2 ** (config.__retryCount - 1);
      const jitter = Math.random() * 300;
      log(`⟳ retry ${config.__retryCount}/${MAX_RETRIES} after ${Math.round(backoff)}ms`,
        config.url, 'status=', status ?? 'network/CORS');
      await sleep(backoff + jitter);
      return http(config);
    }
    log('✗ giving up', config.url, 'status=', status ?? 'network/CORS', error.message);
    return Promise.reject(error);
  },
);

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
export const runJobsReload = () => http.post('/jobs/reload').then((r) => r.data);
export const getJobsReloadStatus = () => http.get('/jobs/reload/status').then((r) => r.data);
export const setApplied = (id, applied) =>
  http.post(`/jobs/${id}/applied`, null, { params: { applied } }).then((r) => r.data);

export default http;

