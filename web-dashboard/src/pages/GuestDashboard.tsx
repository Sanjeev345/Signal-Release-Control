import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { ApiError, listMyReleases, listTasks, withdraw } from '../api';
import type { Release, Scope, Session, Task, WithdrawalResult } from '../types';
import {
  badgeTone,
  formatClock,
  formatDateTime,
  shortId,
  titleCase,
} from '../utils';
import {
  CheckIcon,
  ClockIcon,
  CloseIcon,
  LinkIcon,
  RadioIcon,
  RefreshIcon,
  ShieldIcon,
} from '../components/Icons';

interface GuestDashboardProps {
  session: Session;
  onExpired: () => void;
}

interface WithdrawalTarget {
  release: Release;
  scope: Scope;
}

interface WithdrawalNotice {
  result: WithdrawalResult;
  excerptCode: string;
}

const DEFAULT_REASON = 'Withdrawing my consent for this excerpt.';

export default function GuestDashboard({
  session,
  onExpired,
}: GuestDashboardProps) {
  const [releases, setReleases] = useState<Release[]>([]);
  const [tasksByExcerpt, setTasksByExcerpt] = useState<Record<string, Task[]>>({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [target, setTarget] = useState<WithdrawalTarget | null>(null);
  const [reason, setReason] = useState(DEFAULT_REASON);
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<WithdrawalNotice | null>(null);
  const timers = useRef<number[]>([]);

  useEffect(() => {
    return () => {
      timers.current.forEach((timer) => window.clearTimeout(timer));
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    listMyReleases(session)
      .then((result) => {
        if (!cancelled) setReleases(result);
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setError(cause instanceof Error ? cause.message : 'Could not load releases');
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
  }, [onExpired, refreshKey, session]);

  const excerptIds = useMemo(
    () =>
      Array.from(
        new Set(
          releases.flatMap((release) => release.scopes.map((scope) => scope.excerptId)),
        ),
      ),
    [releases],
  );

  useEffect(() => {
    if (excerptIds.length === 0) return;
    let cancelled = false;
    Promise.all(
      excerptIds.map((excerptId) =>
        listTasks(session, excerptId)
          .then((tasks) => [excerptId, tasks] as const)
          .catch(() => [excerptId, [] as Task[]] as const),
      ),
    ).then((entries) => {
      if (cancelled) return;
      setTasksByExcerpt(Object.fromEntries(entries));
    });
    return () => {
      cancelled = true;
    };
  }, [excerptIds, session]);

  const allTasks = Object.values(tasksByExcerpt).flat();
  const activeScopes = releases
    .flatMap((release) => release.scopes)
    .filter((scope) => scope.status === 'ACTIVE');
  const withdrawnScopes = releases
    .flatMap((release) => release.scopes)
    .filter((scope) => scope.status === 'WITHDRAWN');
  const lastEventId = allTasks[0]?.eventId ?? notice?.result.eventId;

  function refresh() {
    setRefreshing(true);
    setRefreshKey((key) => key + 1);
  }

  function reloadTasks(excerptId: string) {
    listTasks(session, excerptId)
      .then((tasks) =>
        setTasksByExcerpt((current) => ({ ...current, [excerptId]: tasks })),
      )
      .catch((cause) => {
        if (cause instanceof ApiError && cause.status === 401) onExpired();
      });
  }

  function pollTasks(excerptId: string) {
    timers.current.forEach((timer) => window.clearTimeout(timer));
    timers.current = [700, 1600, 3000].map((delay) =>
      window.setTimeout(() => reloadTasks(excerptId), delay),
    );
  }

  function openWithdrawal(release: Release, scope: Scope) {
    setTarget({ release, scope });
    setReason(DEFAULT_REASON);
    setActionError(null);
  }

  async function submitWithdrawal(event: FormEvent) {
    event.preventDefault();
    if (!target) return;
    setSubmitting(true);
    setActionError(null);
    try {
      const result = await withdraw(
        session,
        target.release.id,
        target.scope.excerptId,
        reason.trim(),
      );
      setNotice({ result, excerptCode: target.scope.excerptCode });
      setTarget(null);
      setRefreshKey((key) => key + 1);
      pollTasks(result.excerptId);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setActionError(
        cause instanceof Error ? cause.message : 'Withdrawal could not be accepted',
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="page">
        <div className="loading-panel">
          <span className="spinner" />
          Loading your clearances...
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page">
        <div className="empty-panel">
          <h2>Clearances unavailable</h2>
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
      <header className="page-heading guest-heading">
        <div>
          <span className="eyebrow">Guest control</span>
          <h1>Your voice, your boundaries.</h1>
          <p>
            Withdraw one tagged excerpt at a time. Your other excerpts and the
            rest of the episode stay exactly as they are.
          </p>
        </div>
        <div className="heading-meta">
          <span className="guest-chip">
            <ShieldIcon size={15} />
            {session.username}
          </span>
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

      <section className="stat-grid guest-stats" aria-label="Your clearance summary">
        <article className="stat-card">
          <span className="stat-icon"><ShieldIcon /></span>
          <strong>{releases.length}</strong>
          <span>Releases</span>
          <small>Across your guest appearances</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><LinkIcon /></span>
          <strong>{activeScopes.length}</strong>
          <span>Active clearances</span>
          <small>{withdrawnScopes.length} already withdrawn</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><RadioIcon /></span>
          <strong>{allTasks.length}</strong>
          <span>Removal tasks</span>
          <small>Created from withdrawal events</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><ClockIcon /></span>
          <strong>{notice ? 'Accepted' : 'Ready'}</strong>
          <span>Last event</span>
          <small>{lastEventId ? shortId(lastEventId) : 'No withdrawals yet'}</small>
        </article>
      </section>

      {notice && (
        <section className="notice-panel">
          <span className="notice-icon"><CheckIcon size={20} /></span>
          <div>
            <strong>
              Withdrawal accepted for {notice.excerptCode}
            </strong>
            <p>
              Event {shortId(notice.result.eventId)} is published. Distribution
              is creating removal tasks for every edit that carries this excerpt.
            </p>
          </div>
          <span className="badge badge-positive">202 · {titleCase(notice.result.status)}</span>
        </section>
      )}

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Guest releases</span>
            <h2>Excerpt clearances</h2>
          </div>
          <span className="count-chip">{releases.length}</span>
        </div>

        <div className="guest-release-list">
          {releases.map((release) => {
            const hasOtherActive = release.scopes.some(
              (scope) =>
                scope.status === 'ACTIVE' &&
                (!target || scope.excerptId !== target.scope.excerptId),
            );
            return (
              <article className="release-card" key={release.id}>
                <div className="release-card-head">
                  <div>
                    <span className="episode-code">
                      Episode {shortId(release.episodeId)}
                    </span>
                    <small>Release {shortId(release.id)}</small>
                  </div>
                  <span className={`badge badge-${badgeTone(release.status)}`}>
                    {titleCase(release.status)}
                  </span>
                </div>

                <div className="scope-stack">
                  {release.scopes.map((scope) => {
                    const tasks = tasksByExcerpt[scope.excerptId] ?? [];
                    return (
                      <div
                        className={`scope-card ${scope.status === 'WITHDRAWN' ? 'is-withdrawn' : ''}`}
                        key={scope.id}
                      >
                        <div className="scope-main">
                          <div className="scope-title">
                            <span className="code-pill">{scope.excerptCode}</span>
                            <span className={`badge badge-${badgeTone(scope.status)}`}>
                              {titleCase(scope.status)}
                            </span>
                          </div>
                          <div className="scope-meta">
                            <span>
                              <ClockIcon />
                              {formatClock(scope.startMs)}–{formatClock(scope.endMs)}
                            </span>
                            <span>{titleCase(scope.scopeType)}</span>
                            <span>
                              {tasks.length} removal task{tasks.length === 1 ? '' : 's'}
                            </span>
                          </div>
                          {scope.status === 'WITHDRAWN' && (
                            <div className="withdrawal-evidence">
                              <strong>Withdrawn {formatDateTime(scope.withdrawnAt)}</strong>
                              <p>{scope.withdrawalReason ?? 'No reason supplied'}</p>
                            </div>
                          )}
                        </div>
                        {scope.status === 'ACTIVE' ? (
                          <button
                            className="button button-danger-ghost"
                            onClick={() => openWithdrawal(release, scope)}
                          >
                            Withdraw excerpt
                          </button>
                        ) : (
                          <span className="completed-mark">
                            <CheckIcon />
                            Removed from scope
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>

                {hasOtherActive && (
                  <p className="scope-note">
                    <ShieldIcon size={15} />
                    Other excerpts in this release remain active. This action is
                    scoped to a single excerpt.
                  </p>
                )}
              </article>
            );
          })}
          {releases.length === 0 && (
            <p className="empty-copy">No guest releases are linked to your account.</p>
          )}
        </div>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Delivery queue</span>
            <h2>Removal tasks</h2>
          </div>
          <span className="count-chip">{allTasks.length}</span>
        </div>
        <div className="removal-grid">
          {allTasks.map((task) => (
            <article className="removal-card" key={task.id}>
              <div className="removal-top">
                <span className="provider provider-generic">
                  {task.provider?.charAt(0) ?? '?'}
                </span>
                <div>
                  <strong>{task.provider ?? 'Distribution edit'}</strong>
                  <small>{task.editCode ?? shortId(task.editId)}</small>
                </div>
                <span className={`badge badge-${badgeTone(task.status)}`}>
                  {titleCase(task.status)}
                </span>
              </div>
              <div className="removal-meta">
                <span>{titleCase(task.taskType)}</span>
                <span>Event {shortId(task.eventId)}</span>
                <span>{formatDateTime(task.createdAt)}</span>
              </div>
            </article>
          ))}
          {allTasks.length === 0 && (
            <p className="empty-copy">
              No removal work yet. Withdraw an excerpt and the matching edits will
              appear here automatically.
            </p>
          )}
        </div>
      </section>

      {target && (
        <div
          className="modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="withdraw-title"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget && !submitting) setTarget(null);
          }}
        >
          <form className="modal-card" onSubmit={submitWithdrawal}>
            <div className="modal-heading">
              <div>
                <span className="eyebrow">Scoped withdrawal</span>
                <h2 id="withdraw-title">Withdraw {target.scope.excerptCode}?</h2>
              </div>
              <button
                type="button"
                className="icon-button"
                aria-label="Close"
                onClick={() => setTarget(null)}
                disabled={submitting}
              >
                <CloseIcon />
              </button>
            </div>
            <p className="modal-copy">
              This revokes your consent for this excerpt only. The episode and
              your other excerpts stay live. Distribution will remove only the
              placements that carry it.
            </p>
            <div className="modal-scope">
              <span className="code-pill">{target.scope.excerptCode}</span>
              <span>
                {formatClock(target.scope.startMs)}–{formatClock(target.scope.endMs)}
              </span>
              <span>{titleCase(target.scope.scopeType)}</span>
            </div>
            <label className="reason-field">
              Reason for withdrawal
              <textarea
                value={reason}
                maxLength={2000}
                rows={3}
                onChange={(event) => setReason(event.target.value)}
                required
                autoFocus
              />
              <small>{reason.length}/2000</small>
            </label>
            {actionError && <p className="form-error">{actionError}</p>}
            <div className="modal-actions">
              <button
                type="button"
                className="button button-secondary"
                onClick={() => setTarget(null)}
                disabled={submitting}
              >
                Keep consent
              </button>
              <button
                type="submit"
                className="button button-danger"
                disabled={submitting || reason.trim().length === 0}
              >
                {submitting ? 'Publishing event...' : 'Confirm withdrawal'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
