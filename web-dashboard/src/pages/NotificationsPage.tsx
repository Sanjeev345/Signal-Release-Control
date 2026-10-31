import { useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api';
import type { InAppNotification, Session } from '../types';
import {
  formatDateTime,
  notificationTone,
  titleCase,
} from '../utils';
import {
  CheckIcon,
  ClockIcon,
  RefreshIcon,
} from '../components/Icons';
import { SegmentedFilter } from '../components/Controls';

interface NotificationsPageProps {
  session: Session;
  onExpired: () => void;
  onUnreadChange?: () => void;
}

type NotificationFilter = 'ALL' | 'UNREAD';

const EVENT_LABELS: Record<string, string> = {
  REQUEST_SUBMITTED: 'New excerpt request',
  REQUEST_APPROVED: 'Request approved',
  REQUEST_REJECTED: 'Request rejected',
  CONSENT_WITHDRAWN: 'Consent withdrawn',
  TASK_COMPLETED: 'Task completion recorded',
};

export default function NotificationsPage({
  session,
  onExpired,
  onUnreadChange,
}: NotificationsPageProps) {
  const [notifications, setNotifications] = useState<InAppNotification[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<NotificationFilter>('ALL');
  const [reloadKey, setReloadKey] = useState(0);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [markingAll, setMarkingAll] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    listNotifications(session)
      .then((result) => {
        if (!cancelled) setNotifications(result);
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setError(
          cause instanceof Error ? cause.message : 'Could not load notifications',
        );
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
          setRefreshing(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [onExpired, reloadKey, session]);

  const unread = useMemo(
    () => notifications.filter((item) => !item.read),
    [notifications],
  );

  const visible =
    filter === 'UNREAD' ? unread : notifications;

  function refresh() {
    setRefreshing(true);
    setReloadKey((key) => key + 1);
  }

  async function markRead(notification: InAppNotification) {
    if (notification.read) return;
    setBusyId(notification.id);
    setActionError(null);
    try {
      const updated = await markNotificationRead(session, notification.id);
      setNotifications((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      onUnreadChange?.();
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setActionError(
        cause instanceof Error ? cause.message : 'Notification could not be updated',
      );
    } finally {
      setBusyId(null);
    }
  }

  async function markAllRead() {
    setMarkingAll(true);
    setActionError(null);
    try {
      await markAllNotificationsRead(session);
      setNotifications((current) =>
        current.map((item) => ({ ...item, read: true })),
      );
      onUnreadChange?.();
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setActionError(
        cause instanceof Error ? cause.message : 'Notifications could not be updated',
      );
    } finally {
      setMarkingAll(false);
    }
  }

  if (loading) {
    return (
      <div className="page">
        <div className="loading-panel">
          <span className="spinner" />
          Loading notifications...
        </div>
      </div>
    );
  }

  if (error && notifications.length === 0) {
    return (
      <div className="page">
        <div className="empty-panel">
          <h2>Notifications unavailable</h2>
          <p>{error}</p>
          <button className="button button-secondary" onClick={refresh}>
            Try again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <header className="page-heading">
        <div>
          <span className="eyebrow">In-app notifications</span>
          <h1>Only what concerns you.</h1>
          <p>
            Notifications are delivered per recipient from the security context.
            Guests see decisions on their own requests; producers see requests and
            withdrawals for their own episodes.
          </p>
        </div>
        <div className="heading-meta">
          <button
            className="button button-secondary"
            onClick={refresh}
            disabled={refreshing}
          >
            <RefreshIcon className={refreshing ? 'spin' : undefined} />
            {refreshing ? 'Refreshing' : 'Refresh'}
          </button>
        </div>
      </header>

      {error && <div className="inline-error">{error}</div>}
      {actionError && <div className="inline-error">{actionError}</div>}

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Inbox</span>
            <h2>
              Notifications
              {unread.length > 0 && (
                <span className="count-chip inline-chip">{unread.length} unread</span>
              )}
            </h2>
          </div>
          <div className="heading-actions">
            <SegmentedFilter
              ariaLabel="Filter notifications"
              value={filter}
              onChange={(value) => setFilter(value as NotificationFilter)}
              options={[
                { value: 'ALL', label: 'All', count: notifications.length },
                { value: 'UNREAD', label: 'Unread', count: unread.length },
              ]}
            />
            <button
              className="button button-secondary"
              onClick={markAllRead}
              disabled={markingAll || unread.length === 0}
            >
              <CheckIcon />
              {markingAll ? 'Marking...' : 'Mark all read'}
            </button>
          </div>
        </div>

        <div className="notification-list">
          {visible.map((notification) => (
            <article
              className={`notification-row ${notification.read ? '' : 'is-unread'}`}
              key={notification.id}
            >
              <span className="notification-dot" aria-hidden="true" />
              <div className="notification-main">
                <div className="notification-head">
                  <strong>
                    {EVENT_LABELS[notification.eventType] ??
                      titleCase(notification.eventType)}
                  </strong>
                  <span
                    className={`badge badge-${notificationTone(notification.eventType)}`}
                  >
                    {titleCase(notification.eventType)}
                  </span>
                  {!notification.read && (
                    <span className="badge badge-pending">Unread</span>
                  )}
                </div>
                <p>{notification.message}</p>
                <div className="notification-meta">
                  <span>
                    <ClockIcon size={13} />
                    {formatDateTime(notification.createdAt)}
                  </span>
                  {notification.relatedId && (
                    <span className="code-pill subtle">
                      Ref {notification.relatedId.slice(0, 8)}
                    </span>
                  )}
                </div>
              </div>
              <div className="notification-actions">
                {notification.read ? (
                  <span className="completed-mark">
                    <CheckIcon />
                    Read
                  </span>
                ) : (
                  <button
                    className="button button-secondary button-small"
                    onClick={() => markRead(notification)}
                    disabled={busyId === notification.id}
                  >
                    {busyId === notification.id ? 'Saving...' : 'Mark read'}
                  </button>
                )}
              </div>
            </article>
          ))}

          {visible.length === 0 && (
            <p className="empty-copy">
              {notifications.length === 0
                ? 'No notifications yet. They are created when requests and consent change.'
                : 'You are all caught up — no unread notifications.'}
            </p>
          )}
        </div>
      </section>
    </div>
  );
}
