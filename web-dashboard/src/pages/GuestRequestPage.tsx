import { useEffect, useState, type FormEvent } from 'react';
import {
  ApiError,
  listAvailableEpisodes,
  submitExcerptRequest,
} from '../api';
import type { EpisodeSummary, ExcerptRequest, Session } from '../types';
import { formatClock } from '../utils';
import { CheckIcon, ClockIcon, ShieldIcon } from '../components/Icons';

interface GuestRequestPageProps {
  session: Session;
  onExpired: () => void;
}

export default function GuestRequestPage({
  session,
  onExpired,
}: GuestRequestPageProps) {
  const [episodes, setEpisodes] = useState<EpisodeSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [episodeId, setEpisodeId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [startSeconds, setStartSeconds] = useState('');
  const [endSeconds, setEndSeconds] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [created, setCreated] = useState<ExcerptRequest | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    listAvailableEpisodes(session)
      .then((result) => {
        if (cancelled) return;
        setEpisodes(result);
        setEpisodeId((current) => current || result[0]?.id || '');
      })
      .catch((cause) => {
        if (cancelled) return;
        if (cause instanceof ApiError && cause.status === 401) {
          onExpired();
          return;
        }
        setLoadError(
          cause instanceof Error ? cause.message : 'Could not load episodes',
        );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [onExpired, reloadKey, session]);

  const startValue = Number(startSeconds);
  const endValue = Number(endSeconds);
  const timingValid =
    Number.isFinite(startValue) &&
    Number.isFinite(endValue) &&
    startSeconds !== '' &&
    endSeconds !== '' &&
    startValue >= 0 &&
    endValue > startValue &&
    Math.round(endValue * 1000) > Math.round(startValue * 1000);
  const canSubmit =
    !submitting &&
    episodeId.length > 0 &&
    title.trim().length > 0 &&
    timingValid;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setSubmitError(null);
    setCreated(null);
    try {
      const request = await submitExcerptRequest(session, {
        episodeId,
        title: title.trim(),
        description: description.trim() || undefined,
        startMs: Math.round(startValue * 1000),
        endMs: Math.round(endValue * 1000),
      });
      setCreated(request);
      setTitle('');
      setDescription('');
      setStartSeconds('');
      setEndSeconds('');
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setSubmitError(
        cause instanceof Error ? cause.message : 'Request could not be submitted',
      );
    } finally {
      setSubmitting(false);
    }
  }

  const selectedEpisode = episodes.find((episode) => episode.id === episodeId);

  return (
    <div className="page">
      <header className="page-heading">
        <div>
          <span className="eyebrow">Request an excerpt</span>
          <h1>Ask for a moment to be clipped.</h1>
          <p>
            Choose an episode, mark the exact window, and describe what should be
            shared. A producer reviews every request before anything is created.
          </p>
        </div>
        <div className="heading-meta">
          <span className="guest-chip">
            <ShieldIcon size={15} />
            {session.username}
          </span>
        </div>
      </header>

      {created && (
        <section className="notice-panel" role="status">
          <span className="notice-icon"><CheckIcon size={20} /></span>
          <div>
            <strong>Request submitted: {created.title}</strong>
            <p>
              {created.episodeTitle} · {formatClock(created.startMs)}–
              {formatClock(created.endMs)}. It is pending a producer decision and
              does not grant consent on its own.
            </p>
          </div>
          <span className="badge badge-pending">Pending</span>
        </section>
      )}

      <div className="request-layout">
        <section className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">New request</span>
              <h2>Excerpt details</h2>
            </div>
          </div>

          {loading ? (
            <div className="loading-panel flush">
              <span className="spinner" />
              Loading available episodes...
            </div>
          ) : loadError ? (
            <div className="empty-panel flush">
              <h2>Episodes unavailable</h2>
              <p>{loadError}</p>
              <button
                className="button button-secondary"
                onClick={() => setReloadKey((key) => key + 1)}
              >
                Try again
              </button>
            </div>
          ) : episodes.length === 0 ? (
            <div className="empty-panel flush">
              <h2>No published episodes yet</h2>
              <p>A request can only target a published episode.</p>
            </div>
          ) : (
            <form className="request-form" onSubmit={handleSubmit}>
              <label className="form-field">
                Episode
                <select
                  value={episodeId}
                  onChange={(event) => setEpisodeId(event.target.value)}
                  required
                >
                  {episodes.map((episode) => (
                    <option key={episode.id} value={episode.id}>
                      {episode.code} · {episode.title}
                    </option>
                  ))}
                </select>
              </label>

              <label className="form-field">
                Request title
                <input
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  maxLength={255}
                  placeholder="The moment trust clicked"
                  required
                />
                <small>{title.length}/255</small>
              </label>

              <label className="form-field">
                Description <span className="optional">optional</span>
                <textarea
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  maxLength={2000}
                  rows={4}
                  placeholder="Context for the producer and the clip notes."
                />
                <small>{description.length}/2000</small>
              </label>

              <div className="form-row">
                <label className="form-field">
                  Start (seconds)
                  <input
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="0.1"
                    value={startSeconds}
                    onChange={(event) => setStartSeconds(event.target.value)}
                    placeholder="0"
                    required
                  />
                </label>
                <label className="form-field">
                  End (seconds)
                  <input
                    type="number"
                    inputMode="decimal"
                    min={0}
                    step="0.1"
                    value={endSeconds}
                    onChange={(event) => setEndSeconds(event.target.value)}
                    placeholder="45"
                    required
                  />
                </label>
              </div>

              {startSeconds !== '' && endSeconds !== '' && !timingValid && (
                <p className="form-error">
                  End must be greater than start, and start cannot be negative.
                </p>
              )}

              {timingValid && (
                <div className="window-preview">
                  <ClockIcon />
                  <span>
                    {formatClock(Math.round(startValue * 1000))}–
                    {formatClock(Math.round(endValue * 1000))}
                  </span>
                  <span>
                    {(endValue - startValue).toFixed(1)}s selected
                  </span>
                  {selectedEpisode && <span>{selectedEpisode.title}</span>}
                </div>
              )}

              {submitError && <p className="form-error">{submitError}</p>}

              <div className="form-actions">
                <button
                  type="submit"
                  className="button button-primary"
                  disabled={!canSubmit}
                >
                  {submitting ? 'Submitting...' : 'Submit request'}
                </button>
                <span className="muted">
                  Requests stay pending until a producer approves them.
                </span>
              </div>
            </form>
          )}
        </section>

        <aside className="panel request-guide">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">How it works</span>
              <h2>Consent stays yours</h2>
            </div>
          </div>
          <ol className="flow-list">
            <li>
              <span>1</span>
              <div>
                <strong>Request the clip</strong>
                <p>Pick the episode and mark the exact window to share.</p>
              </div>
            </li>
            <li>
              <span>2</span>
              <div>
                <strong>Producer reviews</strong>
                <p>You are notified when the request is approved or rejected.</p>
              </div>
            </li>
            <li>
              <span>3</span>
              <div>
                <strong>Approval creates the excerpt</strong>
                <p>
                  Only an approved request becomes an excerpt. Submitting alone
                  never grants or changes consent.
                </p>
              </div>
            </li>
            <li>
              <span>4</span>
              <div>
                <strong>You can still withdraw</strong>
                <p>
                  Every excerpt stays individually revocable from your dashboard.
                </p>
              </div>
            </li>
          </ol>
        </aside>
      </div>
    </div>
  );
}
