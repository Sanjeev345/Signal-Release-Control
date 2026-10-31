import { useCallback, useEffect, useState } from 'react';
import {
  ApiError,
  clearSession,
  listNotifications,
  loadSession,
  saveSession,
} from './api';
import type { Session } from './types';
import Login from './pages/Login';
import ProducerDashboard from './pages/ProducerDashboard';
import GuestDashboard from './pages/GuestDashboard';
import GuestRequestPage from './pages/GuestRequestPage';
import GuestRequestsPage from './pages/GuestRequestsPage';
import GuestHistoryPage from './pages/GuestHistoryPage';
import ProducerRequestsPage from './pages/ProducerRequestsPage';
import ProducerTasksPage from './pages/ProducerTasksPage';
import NotificationsPage from './pages/NotificationsPage';
import { LogOutIcon, ShieldIcon, WaveMark } from './components/Icons';

type PageKey =
  | 'dashboard'
  | 'request'
  | 'requests'
  | 'history'
  | 'producer-requests'
  | 'tasks'
  | 'notifications';

interface Tab {
  key: PageKey;
  label: string;
}

const GUEST_TABS: Tab[] = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'request', label: 'Request excerpt' },
  { key: 'requests', label: 'My requests' },
  { key: 'history', label: 'Consent history' },
  { key: 'notifications', label: 'Notifications' },
];

const PRODUCER_TABS: Tab[] = [
  { key: 'dashboard', label: 'Overview' },
  { key: 'producer-requests', label: 'Requests' },
  { key: 'tasks', label: 'Distribution tasks' },
  { key: 'notifications', label: 'Notifications' },
];

export default function App() {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [page, setPage] = useState<PageKey>('dashboard');
  const [unreadCount, setUnreadCount] = useState(0);
  const [unreadKey, setUnreadKey] = useState(0);

  const handleLogin = (next: Session) => {
    saveSession(next);
    setSession(next);
    setPage('dashboard');
  };

  const handleLogout = useCallback(() => {
    clearSession();
    setSession(null);
    setUnreadCount(0);
  }, []);

  const isProducer = session?.roles.includes('podcastProducer') ?? false;
  const tabs = isProducer ? PRODUCER_TABS : GUEST_TABS;
  const activePage = tabs.some((tab) => tab.key === page) ? page : 'dashboard';

  useEffect(() => {
    if (!session) return;
    let cancelled = false;
    const load = () => {
      listNotifications(session, true)
        .then((result) => {
          if (!cancelled) setUnreadCount(result.length);
        })
        .catch((cause) => {
          if (cancelled) return;
          if (cause instanceof ApiError && cause.status === 401) handleLogout();
        });
    };
    load();
    const timer = window.setInterval(load, 60_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [handleLogout, session, unreadKey]);

  if (!session) {
    return <Login onLogin={handleLogin} />;
  }

  const roleLabel = isProducer ? 'Producer' : 'Guest';

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand-lockup">
          <span className="brand-mark">
            <WaveMark size={30} />
          </span>
          <span>
            <strong>Signal</strong>
            <small>Release Control</small>
          </span>
        </div>

        <div className="header-right">
          <span className="environment-pill">
            <i />
            Demo environment
          </span>
          <div className="identity-chip">
            <span className={`avatar avatar-${roleLabel.toLowerCase()}`}>
              {session.username.charAt(0).toUpperCase()}
            </span>
            <span>
              <strong>{session.username}</strong>
              <small>
                <ShieldIcon size={12} />
                {roleLabel}
              </small>
            </span>
          </div>
          <button
            className="icon-button"
            onClick={handleLogout}
            aria-label="Sign out"
            title="Sign out"
          >
            <LogOutIcon />
          </button>
        </div>
      </header>

      <nav className="app-nav" aria-label="Primary">
        <div className="app-nav-inner">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              type="button"
              className={`nav-tab ${activePage === tab.key ? 'is-active' : ''}`}
              aria-current={activePage === tab.key ? 'page' : undefined}
              onClick={() => setPage(tab.key)}
            >
              {tab.label}
              {tab.key === 'notifications' && unreadCount > 0 && (
                <span
                  className="nav-badge"
                  aria-label={`${unreadCount} unread notifications`}
                >
                  {unreadCount}
                </span>
              )}
            </button>
          ))}
        </div>
      </nav>

      <main>
        {isProducer ? (
          <>
            {activePage === 'dashboard' && (
              <ProducerDashboard session={session} onExpired={handleLogout} />
            )}
            {activePage === 'producer-requests' && (
              <ProducerRequestsPage session={session} onExpired={handleLogout} />
            )}
            {activePage === 'tasks' && (
              <ProducerTasksPage session={session} onExpired={handleLogout} />
            )}
            {activePage === 'notifications' && (
              <NotificationsPage
                session={session}
                onExpired={handleLogout}
                onUnreadChange={() => setUnreadKey((key) => key + 1)}
              />
            )}
          </>
        ) : (
          <>
            {activePage === 'dashboard' && (
              <GuestDashboard session={session} onExpired={handleLogout} />
            )}
            {activePage === 'request' && (
              <GuestRequestPage session={session} onExpired={handleLogout} />
            )}
            {activePage === 'requests' && (
              <GuestRequestsPage session={session} onExpired={handleLogout} />
            )}
            {activePage === 'history' && (
              <GuestHistoryPage session={session} onExpired={handleLogout} />
            )}
            {activePage === 'notifications' && (
              <NotificationsPage
                session={session}
                onExpired={handleLogout}
                onUnreadChange={() => setUnreadKey((key) => key + 1)}
              />
            )}
          </>
        )}
      </main>

      <footer className="app-footer">
        <span>Signal demo · excerpt-scoped consent</span>
        <span>
          Keycloak · Spring Cloud Gateway · Kafka · PostgreSQL
        </span>
      </footer>
    </div>
  );
}
