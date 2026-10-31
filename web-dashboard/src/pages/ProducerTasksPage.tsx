import { useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  ApiError,
  listEpisodes,
  listProducerTasks,
  updateTask,
} from '../api';
import type { Episode, Session, Task } from '../types';
import {
  badgeTone,
  formatDateTime,
  shortId,
  titleCase,
} from '../utils';
import {
  CheckIcon,
  ClockIcon,
  LinkIcon,
  RefreshIcon,
} from '../components/Icons';
import { ConfirmDialog, SegmentedFilter } from '../components/Controls';

interface ProducerTasksPageProps {
  session: Session;
  onExpired: () => void;
}

type StatusFilter = 'ALL' | 'OPEN' | 'IN_PROGRESS' | 'DONE';

type DialogMode = 'complete' | 'edit';

const MAX_NOTES = 4000;
const MAX_EVIDENCE = 2048;

export default function ProducerTasksPage({
  session,
  onExpired,
}: ProducerTasksPageProps) {
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [episodeFilter, setEpisodeFilter] = useState('');
  const [providerFilter, setProviderFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [search, setSearch] = useState('');

  const [busyTaskId, setBusyTaskId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialogMode, setDialogMode] = useState<DialogMode | null>(null);
  const [dialogTask, setDialogTask] = useState<Task | null>(null);
  const [dialogNotes, setDialogNotes] = useState('');
  const [dialogEvidence, setDialogEvidence] = useState('');
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [dialogBusy, setDialogBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    listEpisodes(session)
      .then((result) => {
        if (!cancelled) setEpisodes(result);
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
  }, [onExpired, reloadKey, session]);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    listProducerTasks(session, {
      episodeId: episodeFilter || undefined,
    })
      .then((result) => {
        if (!cancelled) setTasks(result);
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setError(cause instanceof Error ? cause.message : 'Could not load tasks');
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
  }, [episodeFilter, onExpired, reloadKey, session]);

  const providers = useMemo(
    () =>
      Array.from(new Set(tasks.map((task) => task.provider).filter(Boolean))).sort(
        (left, right) => left.localeCompare(right),
      ),
    [tasks],
  );

  const counts = useMemo(
    () => ({
      ALL: tasks.length,
      OPEN: tasks.filter((task) => task.status === 'OPEN').length,
      IN_PROGRESS: tasks.filter((task) => task.status === 'IN_PROGRESS').length,
      DONE: tasks.filter((task) => task.status === 'DONE').length,
    }),
    [tasks],
  );

  const normalizedSearch = search.trim().toLowerCase();
  const visible = tasks.filter((task) => {
    if (providerFilter !== 'ALL' && task.provider !== providerFilter) return false;
    if (statusFilter !== 'ALL' && task.status !== statusFilter) return false;
    if (!normalizedSearch) return true;
    return [
      task.editCode,
      task.provider,
      task.taskType,
      task.excerptId,
      task.id,
      task.notes ?? '',
    ]
      .join(' ')
      .toLowerCase()
      .includes(normalizedSearch);
  });

  function refresh() {
    setRefreshing(true);
    setReloadKey((key) => key + 1);
  }

  function replaceTask(updated: Task) {
    setTasks((current) =>
      current.map((task) => (task.id === updated.id ? updated : task)),
    );
  }

  function openDialog(task: Task, mode: DialogMode) {
    setDialogTask(task);
    setDialogMode(mode);
    setDialogNotes(task.notes ?? '');
    setDialogEvidence(task.evidenceUrl ?? '');
    setDialogError(null);
    setActionError(null);
  }

  function closeDialog() {
    setDialogTask(null);
    setDialogMode(null);
    setDialogError(null);
  }

  async function startTask(task: Task) {
    setBusyTaskId(task.id);
    setActionError(null);
    setNotice(null);
    try {
      const updated = await updateTask(session, task.id, { status: 'IN_PROGRESS' });
      replaceTask(updated);
      setNotice(
        `Task ${shortId(task.id)} moved to in progress. The update records you as the actor.`,
      );
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setActionError(
        cause instanceof Error ? cause.message : 'Task could not be started',
      );
    } finally {
      setBusyTaskId(null);
    }
  }

  async function submitDialog(event: FormEvent) {
    event.preventDefault();
    if (!dialogTask || !dialogMode) return;
    const notes = dialogNotes.trim();
    const evidenceUrl = dialogEvidence.trim();
    if (evidenceUrl && !/^https?:\/\//i.test(evidenceUrl)) {
      setDialogError('Evidence URL must start with http:// or https://');
      return;
    }
    if (dialogMode === 'edit' && !notes && !evidenceUrl) {
      setDialogError('Add completion notes or an evidence URL to update.');
      return;
    }
    setDialogBusy(true);
    setDialogError(null);
    try {
      const updated = await updateTask(session, dialogTask.id, {
        status: dialogMode === 'complete' ? 'DONE' : undefined,
        notes: notes || undefined,
        evidenceUrl: evidenceUrl || undefined,
      });
      replaceTask(updated);
      setDialogTask(null);
      setDialogMode(null);
      setNotice(
        dialogMode === 'complete'
          ? `Completion recorded for task ${shortId(updated.id)}. This records producer completion only — no external platform API was called.`
          : `Task ${shortId(updated.id)} details updated.`,
      );
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setDialogError(
        cause instanceof Error ? cause.message : 'Task could not be updated',
      );
    } finally {
      setDialogBusy(false);
    }
  }

  if (loading) {
    return (
      <div className="page">
        <div className="loading-panel">
          <span className="spinner" />
          Loading distribution tasks...
        </div>
      </div>
    );
  }

  if (error && tasks.length === 0) {
    return (
      <div className="page">
        <div className="empty-panel">
          <h2>Tasks unavailable</h2>
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
          <span className="eyebrow">Distribution tasks</span>
          <h1>Removal work, tracked.</h1>
          <p>
            Follow removal tasks created by consent withdrawals, record progress,
            and attach the evidence you used. Completion is recorded here only.
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

      <section className="info-panel">
        <span className="info-icon"><ClockIcon size={18} /></span>
        <div>
          <strong>DONE means the producer recorded completion.</strong>
          <p>
            External platform removals are simulated through these tracked tasks.
            Signal never claims that content was actually removed from Spotify,
            TikTok, YouTube or any other platform.
          </p>
        </div>
      </section>

      {notice && (
        <section className="notice-panel" role="status">
          <span className="notice-icon"><CheckIcon size={20} /></span>
          <div>
            <strong>Task updated</strong>
            <p>{notice}</p>
          </div>
        </section>
      )}

      {actionError && <div className="inline-error">{actionError}</div>}

      <section className="stat-grid" aria-label="Task summary">
        <article className="stat-card">
          <span className="stat-icon"><ClockIcon /></span>
          <strong>{counts.OPEN}</strong>
          <span>Open tasks</span>
          <small>Waiting to be started</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><ClockIcon /></span>
          <strong>{counts.IN_PROGRESS}</strong>
          <span>In progress</span>
          <small>Work started by a producer</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><CheckIcon /></span>
          <strong>{counts.DONE}</strong>
          <span>Completion recorded</span>
          <small>Not an external confirmation</small>
        </article>
        <article className="stat-card">
          <span className="stat-icon"><LinkIcon /></span>
          <strong>{tasks.filter((task) => task.evidenceUrl).length}</strong>
          <span>Evidence attached</span>
          <small>Across the current episode scope</small>
        </article>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Work queue</span>
            <h2>Removal tasks</h2>
          </div>
          <SegmentedFilter
            ariaLabel="Filter tasks by status"
            value={statusFilter}
            onChange={(value) => setStatusFilter(value as StatusFilter)}
            options={[
              { value: 'ALL', label: 'All', count: counts.ALL },
              { value: 'OPEN', label: 'Open', count: counts.OPEN },
              { value: 'IN_PROGRESS', label: 'In progress', count: counts.IN_PROGRESS },
              { value: 'DONE', label: 'Done', count: counts.DONE },
            ]}
          />
        </div>

        <div className="filter-bar">
          <label className="filter-field">
            Episode
            <select
              value={episodeFilter}
              onChange={(event) => setEpisodeFilter(event.target.value)}
            >
              <option value="">All episodes</option>
              {episodes.map((episode) => (
                <option key={episode.id} value={episode.id}>
                  {episode.code} · {episode.title}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field">
            Platform
            <select
              value={providerFilter}
              onChange={(event) => setProviderFilter(event.target.value)}
            >
              <option value="ALL">All platforms</option>
              {providers.map((provider) => (
                <option key={provider} value={provider}>
                  {provider}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-field search-field">
            Search
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Edit code, task, notes..."
            />
          </label>
        </div>

        <div className="task-board">
          {visible.map((task) => (
            <article className="task-card" key={task.id}>
              <div className="task-card-head">
                <span
                  className={`provider provider-${task.provider.toLowerCase()}`}
                >
                  {task.provider.charAt(0)}
                </span>
                <div>
                  <strong>{task.provider}</strong>
                  <small>
                    {task.editCode} · Task {shortId(task.id)}
                  </small>
                </div>
                <span className={`badge badge-${badgeTone(task.status)}`}>
                  {titleCase(task.status)}
                </span>
              </div>

              <div className="task-card-meta">
                <span>{titleCase(task.taskType)}</span>
                <span>Excerpt {shortId(task.excerptId)}</span>
                <span>Event {shortId(task.eventId)}</span>
                <span>Created {formatDateTime(task.createdAt)}</span>
                <span>Updated {formatDateTime(task.updatedAt)}</span>
                {task.completedAt && (
                  <span>Completed {formatDateTime(task.completedAt)}</span>
                )}
                {task.updatedBy && <span>By {shortId(task.updatedBy)}</span>}
              </div>

              {task.notes && (
                <div className="task-notes">
                  <strong>Completion notes</strong>
                  <p>{task.notes}</p>
                </div>
              )}

              {task.evidenceUrl && (
                <a
                  className="evidence-link"
                  href={task.evidenceUrl}
                  target="_blank"
                  rel="noreferrer noopener"
                >
                  <LinkIcon size={14} />
                  View evidence
                </a>
              )}

              <div className="task-card-actions">
                {task.status === 'OPEN' && (
                  <button
                    className="button button-secondary"
                    onClick={() => startTask(task)}
                    disabled={busyTaskId === task.id}
                  >
                    {busyTaskId === task.id ? 'Starting...' : 'Start task'}
                  </button>
                )}
                {task.status === 'IN_PROGRESS' && (
                  <button
                    className="button button-primary"
                    onClick={() => openDialog(task, 'complete')}
                  >
                    Record completion
                  </button>
                )}
                {task.status === 'DONE' && (
                  <button
                    className="button button-secondary"
                    onClick={() => openDialog(task, 'edit')}
                  >
                    Edit notes and evidence
                  </button>
                )}
                {task.status !== 'DONE' && (
                  <span className="task-action-hint">
                    {task.status === 'OPEN'
                      ? 'Start the task before recording completion.'
                      : 'Completion records your action only.'}
                  </span>
                )}
              </div>
            </article>
          ))}

          {visible.length === 0 && (
            <p className="empty-copy">
              {tasks.length === 0
                ? 'No removal tasks exist for the selected episode.'
                : 'No tasks match the current filters.'}
            </p>
          )}
        </div>
      </section>

      {dialogTask && dialogMode && (
        <ConfirmDialog
          eyebrow={dialogMode === 'complete' ? 'Record completion' : 'Task details'}
          title={
            dialogMode === 'complete'
              ? `Mark task ${shortId(dialogTask.id)} complete?`
              : `Update task ${shortId(dialogTask.id)}`
          }
          confirmLabel={
            dialogMode === 'complete' ? 'Record completion' : 'Save details'
          }
          formId="task-update-form"
          busy={dialogBusy}
          error={dialogError}
          onCancel={closeDialog}
        >
          <form id="task-update-form" className="task-dialog-form" onSubmit={submitDialog}>
            <p className="modal-copy">
              {dialogMode === 'complete'
                ? 'DONE records that you completed this removal task. It does not call an external platform API.'
                : 'Update the notes and evidence for this completed task. The status stays unchanged.'}
            </p>
            <div className="modal-scope">
              <span>{dialogTask.provider}</span>
              <span className="code-pill subtle">{dialogTask.editCode}</span>
              <span>{titleCase(dialogTask.taskType)}</span>
            </div>
            <label className="reason-field">
              Completion notes
              <textarea
                value={dialogNotes}
                maxLength={MAX_NOTES}
                rows={4}
                placeholder="Removed the clip from the platform dashboard at 14:10."
                onChange={(event) => setDialogNotes(event.target.value)}
              />
              <small>{dialogNotes.length}/{MAX_NOTES}</small>
            </label>
            <label className="reason-field">
              Evidence URL <span className="optional">optional</span>
              <input
                type="url"
                value={dialogEvidence}
                maxLength={MAX_EVIDENCE}
                placeholder="https://..."
                onChange={(event) => setDialogEvidence(event.target.value)}
              />
            </label>
          </form>
        </ConfirmDialog>
      )}
    </div>
  );
}
