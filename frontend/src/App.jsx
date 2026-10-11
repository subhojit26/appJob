import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import ProfilesPage from './pages/ProfilesPage.jsx';
import ProfileFormPage from './pages/ProfileFormPage.jsx';
import AnalyticsPage from './pages/AnalyticsPage.jsx';
import JobsPage from './pages/JobsPage.jsx';

export default function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/profiles" replace />} />
        <Route path="/profiles" element={<ProfilesPage />} />
        <Route path="/profiles/new" element={<ProfileFormPage />} />
        <Route path="/profiles/:id/edit" element={<ProfileFormPage />} />
        <Route path="/analytics" element={<AnalyticsPage />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="*" element={<Navigate to="/profiles" replace />} />
      </Routes>
    </Layout>
  );
}

