import { Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout.jsx';

const ProfilesPage = lazy(() => import('./pages/ProfilesPage.jsx'));
const ProfileFormPage = lazy(() => import('./pages/ProfileFormPage.jsx'));
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage.jsx'));
const JobsPage = lazy(() => import('./pages/JobsPage.jsx'));

const loadingFallback = (
  <div style={{ padding: '2rem', color: '#94a3b8', fontSize: '0.95rem' }}>
    Loading…
  </div>
);

export default function App() {
  return (
    <Layout>
      <Suspense fallback={loadingFallback}>
        <Routes>
          <Route path="/" element={<Navigate to="/profiles" replace />} />
          <Route path="/profiles" element={<ProfilesPage />} />
          <Route path="/profiles/new" element={<ProfileFormPage />} />
          <Route path="/profiles/:id/edit" element={<ProfileFormPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
          <Route path="/jobs" element={<JobsPage />} />
          <Route path="*" element={<Navigate to="/profiles" replace />} />
        </Routes>
      </Suspense>
    </Layout>
  );
}
