import { useEffect, useState } from 'react';
import {
  ApiError,
  listEdits,
  listEpisodeReleases,
  listEpisodes,
  listExcerpts,
  updateTaskStatus,
} from '../api';
import type { Edit, Episode, Excerpt, Release, Session, Task } from '../types';
import {
  badgeTone,
  formatClock,
  formatDateTime,
  shortId,
  titleCase,
} from '../utils';
import {
  ClockIcon,
  LinkIcon,
  RadioIcon,
  RefreshIcon,
  ShieldIcon,
} from '../components/Icons';

interface ProducerDashboardProps {
  session: Session;
  onExpired: () => void;
}

export default function ProducerDashboard({
  session,
  onExpired,
}: ProducerDashboardProps) {
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [excerpts, setExcerpts] = useState<Excerpt[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [edits, setEdits] = useState<Edit[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [taskActionId, setTaskActionId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listEpisodes(session)
      .then((result) => {
        if (cancelled) return;
        setEpisodes(result);
        setSelectedId((current) => current || result[0]?.id || '');
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setError(cause instanceof Error ? cause.message : 'Could not load episodes');
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [onExpired, session]);

  useEffect(() => {
    if (!selectedId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([
      listExcerpts(session, selectedId),
      listEpisodeReleases(session, selectedId),
      listEdits(session, selectedId),
    ])
      .then(([nextExcerpts, nextReleases, nextEdits]) => {
        if (cancelled) return;
        setExcerpts(nextExcerpts);
        setReleases(nextReleases);
        setEdits(nextEdits);
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setError(cause instanceof Error ? cause.message : 'Could not load episode');
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
  }, [onExpired, refreshKey, selectedId, session]);

  const episode = episodes.find((item) => item.id === selectedId);
  const activeScopes = releases
    .flatMap((release) => release.scopes)
    .filter((scope) => scope.status === 'ACTIVE');
  const tasks = edits.flatMap((edit) => edit.tasks);
  const openTasks = tasks.filter(
    (task) => task.status === 'OPEN' || task.status === 'IN_PROGRESS',
  );
  const excerptMap = new Map(excerpts.map((excerpt) => [excerpt.id, excerpt]));

  function refresh() {
    setRefreshing(true);
    setRefreshKey((key) => key + 1);
  }

  async function advanceTask(task: Task, status: 'IN_PROGRESS' | 'DONE') {
    setTaskActionId(task.id);
    setActionError(null);
    try {
      const updated = await updateTaskStatus(session, task.id, status);
      setEdits((current) =>
        current.map((edit) => ({
          ...edit,
          tasks: edit.tasks.map((item) => (item.id === updated.id ? updated : item)),
        })),
      );
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setActionError(
        cause instanceof Error ? cause.message : 'Task status could not be updated',
      );
    } finally {
      setTaskActionId(null);
    }
  }

  if (loading && episodes.length === 0) {
    return (
      <div className="page">
        <div className="loading-panel">
          <span className="spinner" />
          Loading the producer workspace...
        </div>
      </div>
    );
  }

  if (error && episodes.length === 0) {
    return (
      <div className="page">
        <div className="empty-panel">
          <h2>Workspace unavailable</h2>
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
          <span className="eyebrow">Producer workspace</span>
          <h1>{episode?.title ?? 'Episode inventory'}</h1>
          <p>
            Track excerpts, guest-scoped clearances, and every downstream edit
            that carries them.
          </p>
        </div>
        <div className="heading-meta">
          <span className="episode-code">{episode?.code ?? '—'}</span>
          <span className="muted">Signed in as {session.username}</span>
        </div>
      </header>

      <div className="toolbar">
        <label className="select-wrap">
          <span>Episode</span>
          <select
            value={selectedId}
            onChange={(event) => setSelectedId(event.target.value)}
          >
            {episodes.map((item) => (
              <option key={item.id} value={item.id}>
                {item.code} · {item.title}
              </option>
            ))}
          </select>
        </label>
        <button
          className="button button-secondary"
          onClick={refresh}
          disabled={refreshing}
        >
          <RefreshIcon className={refreshing ? 'spin' : undefined} />
          {refreshing ? 'Refreshing' : 'Refresh data'}
        </button>
      </div>

      {error && <div className="inline-error">{error}</div>}

      <section className="stat-grid" aria-label="Episode summary">
        <article className="stat-card">
          <span className="stat-icon"><RadioIcon /></span>
          <strong>{episodes.length}</strong>
          <span>Published episodes</span>
          <small>Owned by this producer</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><LinkIcon /></span>
          <strong>{excerpts.length}</strong>
          <span>Tagged excerpts</span>
          <small>Individually addressable</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><ShieldIcon /></span>
          <strong>{activeScopes.length}</strong>
          <span>Active clearances</span>
          <small>Across all guest releases</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><ClockIcon /></span>
          <strong>{openTasks.length}</strong>
          <span>Open removal tasks</span>
          <small>{tasks.length} total tasks tracked</small>
        </article>
      </section>

      <div className="producer-grid">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Episode inventory</span>
              <h2>Tagged excerpts</h2>
            </div>
            <span className="count-chip">{excerpts.length}</span>
          </div>
          <div className="excerpt-list">
            {excerpts.map((excerpt) => {
              const removalTasks = edits
                .flatMap((edit) => edit.tasks)
                .filter(
                  (task) =>
                    task.excerptId === excerpt.id &&
                    task.taskType === 'EXCERPT_REMOVAL',
                );
              return (
                <article className="excerpt-card" key={excerpt.id}>
                  <div className="excerpt-topline">
                    <span className="code-pill">{excerpt.code}</span>
                    <span className={`badge badge-${badgeTone(excerpt.tagStatus)}`}>
                      {titleCase(excerpt.tagStatus)}
                    </span>
                  </div>
                  <p className="transcript">“{excerpt.transcript}”</p>
                  <div className="excerpt-meta">
                    <span>
                      <ClockIcon />
                      {formatClock(excerpt.startMs)}–{formatClock(excerpt.endMs)}
                    </span>
                    <span>Guest {shortId(excerpt.guestId)}</span>
                    <span>
                      {removalTasks.length > 0
                        ? `${removalTasks.length} removal task${removalTasks.length === 1 ? '' : 's'}`
                        : 'No removal work queued'}
                    </span>
                  </div>
                </article>
              );
            })}
            {!loading && excerpts.length === 0 && (
              <p className="empty-copy">No tagged excerpts for this episode.</p>
            )}
          </div>
        </section>

        <section className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">Distribution surface</span>
              <h2>Edits and tasks</h2>
            </div>
            <span className="count-chip">{edits.length}</span>
          </div>
          {actionError && <div className="inline-error">{actionError}</div>}
          <div className="edit-list">
            {edits.map((edit) => (
              <article className="edit-card" key={edit.id}>
                <div className="edit-heading">
                  <span className={`provider provider-${edit.provider.toLowerCase()}`}>
                    {edit.provider.charAt(0)}
                  </span>
                  <div>
                    <strong>{edit.provider}</strong>
                    <small>{edit.externalReference}</small>
                  </div>
                  <span
                    className={`badge badge-${badgeTone(edit.distributionStatus)}`}
                  >
                    {titleCase(edit.distributionStatus)}
                  </span>
                </div>
                <div className="edit-meta">
                  <span className="code-pill subtle">{edit.code}</span>
                  <span>Distributed {formatDateTime(edit.distributedAt)}</span>
                </div>
                {edit.tasks.length > 0 ? (
                  <div className="task-list">
                    {edit.tasks.map((task) => (
                      <div className="task-row" key={task.id}>
                        <span className="task-dot" />
                        <div>
                          <strong>{titleCase(task.taskType)}</strong>
                          <small>
                            Excerpt{' '}
                            {excerptMap.get(task.excerptId)?.code ??
                              shortId(task.excerptId)}
                          </small>
                        </div>
                        <span className={`badge badge-${badgeTone(task.status)}`}>
                          {titleCase(task.status)}
                        </span>
                        {task.status === 'OPEN' && (
                          <button
                            type="button"
                            className="task-action"
                            onClick={() => advanceTask(task, 'IN_PROGRESS')}
                            disabled={taskActionId === task.id}
                          >
                            {taskActionId === task.id ? 'Starting' : 'Start'}
                          </button>
                        )}
                        {task.status === 'IN_PROGRESS' && (
                          <button
                            type="button"
                            className="task-action"
                            onClick={() => advanceTask(task, 'DONE')}
                            disabled={taskActionId === task.id}
                          >
                            {taskActionId === task.id ? 'Completing' : 'Complete'}
                          </button>
                        )}
                        {task.completedAt && (
                          <small className="task-completed">
                            {formatDateTime(task.completedAt)}
                          </small>
                        )}
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="empty-copy compact">No removal work queued.</p>
                )}
              </article>
            ))}
            {!loading && edits.length === 0 && (
              <p className="empty-copy">No distribution edits for this episode.</p>
            )}
          </div>
        </section>
      </div>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Authorization ledger</span>
            <h2>Guest releases</h2>
          </div>
          <span className="count-chip">{releases.length}</span>
        </div>
        <div className="release-table-wrap">
          <table className="release-table">
            <thead>
              <tr>
                <th>Guest / release</th>
                <th>Excerpt</th>
                <th>Window</th>
                <th>Scope</th>
                <th>State</th>
                <th>Evidence</th>
              </tr>
            </thead>
            <tbody>
              {releases.flatMap((release) =>
                release.scopes.map((scope) => (
                  <tr key={scope.id}>
                    <td>
                      <strong>{shortId(release.guestId)}</strong>
                      <small>{shortId(release.id)}</small>
                    </td>
                    <td>
                      <span className="code-pill subtle">{scope.excerptCode}</span>
                    </td>
                    <td>
                      {formatClock(scope.startMs)}–{formatClock(scope.endMs)}
                    </td>
                    <td>{titleCase(scope.scopeType)}</td>
                    <td>
                      <span className={`badge badge-${badgeTone(scope.status)}`}>
                        {titleCase(scope.status)}
                      </span>
                    </td>
                    <td className="evidence">
                      {scope.status === 'WITHDRAWN'
                        ? scope.withdrawalReason ?? 'Withdrawn'
                        : `Granted ${formatDateTime(scope.grantedAt)}`}
                    </td>
                  </tr>
                )),
              )}
            </tbody>
          </table>
          {releases.length === 0 && (
            <p className="empty-copy">No guest releases recorded yet.</p>
          )}
        </div>
      </section>
    </div>
  );
}
