import { useEffect, useMemo, useState } from 'react';
import { ApiError, listMyExcerptRequests } from '../api';
import type { ExcerptRequest, Session } from '../types';
import {
  badgeTone,
  formatClock,
  formatDateTime,
  shortId,
  titleCase,
} from '../utils';
import { ClockIcon, RefreshIcon } from '../components/Icons';
import { SegmentedFilter } from '../components/Controls';

interface GuestRequestsPageProps {
  session: Session;
  onExpired: () => void;
}

type StatusFilter = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED';

export default function GuestRequestsPage({
  session,
  onExpired,
}: GuestRequestsPageProps) {
  const [requests, setRequests] = useState<ExcerptRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    listMyExcerptRequests(session)
      .then((result) => {
        if (!cancelled) setRequests(result);
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setError(
          cause instanceof Error ? cause.message : 'Could not load your requests',
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

  const counts = useMemo(
    () => ({
      ALL: requests.length,
      PENDING: requests.filter((item) => item.status === 'PENDING').length,
      APPROVED: requests.filter((item) => item.status === 'APPROVED').length,
      REJECTED: requests.filter((item) => item.status === 'REJECTED').length,
    }),
    [requests],
  );

  const visible = requests.filter(
    (item) => filter === 'ALL' || item.status === filter,
  );

  function refresh() {
    setRefreshing(true);
    setReloadKey((key) => key + 1);
  }

  if (loading) {
    return (
      <div className="page">
        <div className="loading-panel">
          <span className="spinner" />
          Loading your requests...
        </div>
      </div>
    );
  }

  if (error && requests.length === 0) {
    return (
      <div className="page">
        <div className="empty-panel">
          <h2>Requests unavailable</h2>
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
          <span className="eyebrow">My requests</span>
          <h1>Every request, every decision.</h1>
          <p>
            Track what you asked for and what the producer decided. Approved
            requests link to the excerpt they created.
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

      <div className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Request history</span>
            <h2>Requests</h2>
          </div>
          <SegmentedFilter
            ariaLabel="Filter requests by status"
            value={filter}
            onChange={(value) => setFilter(value as StatusFilter)}
            options={[
              { value: 'ALL', label: 'All', count: counts.ALL },
              { value: 'PENDING', label: 'Pending', count: counts.PENDING },
              { value: 'APPROVED', label: 'Approved', count: counts.APPROVED },
              { value: 'REJECTED', label: 'Rejected', count: counts.REJECTED },
            ]}
          />
        </div>

        <div className="request-list">
          {visible.map((request) => (
            <article className="request-card" key={request.id}>
              <div className="request-card-head">
                <div>
                  <span className="episode-code">{request.episodeTitle}</span>
                  <h3>{request.title}</h3>
                </div>
                <span className={`badge badge-${badgeTone(request.status)}`}>
                  {titleCase(request.status)}
                </span>
              </div>

              <p className="request-description">
                {request.description ?? 'No description supplied.'}
              </p>

              <div className="request-meta">
                <span>
                  <ClockIcon />
                  {formatClock(request.startMs)}–{formatClock(request.endMs)}
                </span>
                <span>Requested {formatDateTime(request.createdAt)}</span>
                {request.decidedAt && (
                  <span>Decided {formatDateTime(request.decidedAt)}</span>
                )}
                {request.excerptId && (
                  <span className="code-pill subtle">
                    Excerpt {shortId(request.excerptId)}
                  </span>
                )}
              </div>

              {request.status === 'REJECTED' && (
                <div className="withdrawal-evidence">
                  <strong>Rejection reason</strong>
                  <p>{request.rejectionReason ?? 'No reason supplied'}</p>
                </div>
              )}

              {request.status === 'APPROVED' && (
                <p className="scope-note">
                  This request became a tagged excerpt. Consent stays at excerpt
                  scope and can be withdrawn from your dashboard.
                </p>
              )}
            </article>
          ))}

          {visible.length === 0 && (
            <p className="empty-copy">
              {requests.length === 0
                ? 'You have not submitted any excerpt requests yet.'
                : `No ${filter.toLowerCase()} requests.`}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
