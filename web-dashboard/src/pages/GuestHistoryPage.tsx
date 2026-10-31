import { useEffect, useMemo, useState } from 'react';
import { ApiError, listMyExcerptRequests, listMyReleases } from '../api';
import type { ExcerptRequest, Release, Session } from '../types';
import {
  badgeTone,
  formatClock,
  formatDateTime,
  shortId,
  titleCase,
} from '../utils';
import { ClockIcon, RefreshIcon, ShieldIcon } from '../components/Icons';
import { SegmentedFilter } from '../components/Controls';

interface GuestHistoryPageProps {
  session: Session;
  onExpired: () => void;
}

type HistoryFilter = 'ALL' | 'GRANTED' | 'WITHDRAWN' | 'AWAITING';
type HistoryKind = 'GRANTED' | 'WITHDRAWN' | 'AWAITING';

interface HistoryEntry {
  id: string;
  kind: HistoryKind;
  label: string;
  excerptCode: string;
  releaseId: string | null;
  at: string;
  reason: string | null;
  window: string;
}

export default function GuestHistoryPage({
  session,
  onExpired,
}: GuestHistoryPageProps) {
  const [releases, setReleases] = useState<Release[]>([]);
  const [requests, setRequests] = useState<ExcerptRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<HistoryFilter>('ALL');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    Promise.all([listMyReleases(session), listMyExcerptRequests(session)])
      .then(([releaseResult, requestResult]) => {
        if (cancelled) return;
        setReleases(releaseResult);
        setRequests(requestResult);
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setError(
          cause instanceof Error
            ? cause.message
            : 'Could not load your consent history',
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

  const scopes = useMemo(
    () => releases.flatMap((release) => release.scopes.map((scope) => ({ release, scope }))),
    [releases],
  );

  const entries = useMemo(() => {
    const list: HistoryEntry[] = [];
    for (const { release, scope } of scopes) {
      const window = `${formatClock(scope.startMs)}–${formatClock(scope.endMs)}`;
      list.push({
        id: `${scope.id}-granted`,
        kind: 'GRANTED',
        label: 'Consent granted',
        excerptCode: scope.excerptCode,
        releaseId: release.id,
        at: scope.grantedAt,
        reason: null,
        window,
      });
      if (scope.withdrawnAt) {
        list.push({
          id: `${scope.id}-withdrawn`,
          kind: 'WITHDRAWN',
          label: 'Consent withdrawn',
          excerptCode: scope.excerptCode,
          releaseId: release.id,
          at: scope.withdrawnAt,
          reason: scope.withdrawalReason,
          window,
        });
      }
    }
    const scopedExcerptIds = new Set(scopes.map(({ scope }) => scope.excerptId));
    for (const request of requests) {
      if (
        request.status !== 'APPROVED' ||
        !request.excerptId ||
        scopedExcerptIds.has(request.excerptId)
      ) {
        continue;
      }
      list.push({
        id: `${request.id}-awaiting`,
        kind: 'AWAITING',
        label: 'Excerpt approved · awaiting release',
        excerptCode: `excerpt ${shortId(request.excerptId)}`,
        releaseId: null,
        at: request.decidedAt ?? request.updatedAt,
        reason: null,
        window: `${formatClock(request.startMs)}–${formatClock(request.endMs)}`,
      });
    }
    return list.sort(
      (left, right) => new Date(right.at).getTime() - new Date(left.at).getTime(),
    );
  }, [requests, scopes]);

  const activeCount = scopes.filter(({ scope }) => scope.status === 'ACTIVE').length;
  const withdrawnCount = scopes.filter(
    ({ scope }) => scope.status === 'WITHDRAWN',
  ).length;
  const awaitingCount = entries.filter((entry) => entry.kind === 'AWAITING').length;

  const visible = entries.filter(
    (entry) => filter === 'ALL' || entry.kind === filter,
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
          Loading consent history...
        </div>
      </div>
    );
  }

  if (error && releases.length === 0) {
    return (
      <div className="page">
        <div className="empty-panel">
          <h2>History unavailable</h2>
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
          <span className="eyebrow">Consent history</span>
          <h1>What you granted. What you withdrew.</h1>
          <p>
            Consent is tracked per excerpt. Withdrawing one excerpt never changes
            the rest of the episode or your other clearances.
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

      <section className="stat-grid" aria-label="Consent summary">
        <article className="stat-card">
          <span className="stat-icon"><ShieldIcon /></span>
          <strong>{activeCount}</strong>
          <span>Active consents</span>
          <small>Excerpts still cleared</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><ClockIcon /></span>
          <strong>{withdrawnCount}</strong>
          <span>Withdrawals</span>
          <small>Excerpts you revoked</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><ShieldIcon /></span>
          <strong>{releases.length}</strong>
          <span>Releases</span>
          <small>Guest appearances on record</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><ClockIcon /></span>
          <strong>{awaitingCount}</strong>
          <span>Awaiting release</span>
          <small>Approved excerpts not yet cleared</small>
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Audit trail</span>
            <h2>Consent timeline</h2>
          </div>
          <SegmentedFilter
            ariaLabel="Filter consent history"
            value={filter}
            onChange={(value) => setFilter(value as HistoryFilter)}
            options={[
              { value: 'ALL', label: 'All events', count: entries.length },
              {
                value: 'GRANTED',
                label: 'Grants',
                count: entries.filter((entry) => entry.kind === 'GRANTED').length,
              },
              {
                value: 'WITHDRAWN',
                label: 'Withdrawals',
                count: entries.filter((entry) => entry.kind === 'WITHDRAWN').length,
              },
              {
                value: 'AWAITING',
                label: 'Awaiting release',
                count: awaitingCount,
              },
            ]}
          />
        </div>

        <div className="timeline">
          {visible.map((entry) => (
            <article className={`timeline-item is-${entry.kind.toLowerCase()}`} key={entry.id}>
              <span className="timeline-marker" />
              <div className="timeline-content">
                <div className="timeline-head">
                  <div>
                    <strong>{entry.label}</strong>
                    <small>
                      {formatDateTime(entry.at)}
                      {entry.releaseId
                        ? ` · Release ${shortId(entry.releaseId)}`
                        : ' · Not in a release yet'}
                    </small>
                  </div>
                  <span
                    className={`badge badge-${badgeTone(
                      entry.kind === 'GRANTED'
                        ? 'ACTIVE'
                        : entry.kind === 'WITHDRAWN'
                          ? 'WITHDRAWN'
                          : 'PENDING',
                    )}`}
                  >
                    {entry.kind === 'AWAITING'
                      ? 'Awaiting release'
                      : titleCase(entry.kind)}
                  </span>
                </div>
                <div className="timeline-meta">
                  <span className="code-pill subtle">{entry.excerptCode}</span>
                  <span>{entry.window}</span>
                </div>
                {entry.reason && (
                  <p className="timeline-reason">{entry.reason}</p>
                )}
              </div>
            </article>
          ))}
          {visible.length === 0 && (
            <p className="empty-copy">
              No consent events recorded for this filter.
            </p>
          )}
        </div>
      </section>
    </div>
  );
}
