import { useEffect, useMemo, useState } from 'react';
import {
  ApiError,
  approveExcerptRequest,
  listProducerExcerptRequests,
  rejectExcerptRequest,
} from '../api';
import type { ExcerptRequest, Session } from '../types';
import {
  badgeTone,
  formatClock,
  formatDateTime,
  shortId,
  titleCase,
} from '../utils';
import { CheckIcon, ClockIcon, RefreshIcon } from '../components/Icons';
import { ConfirmDialog, SegmentedFilter } from '../components/Controls';

interface ProducerRequestsPageProps {
  session: Session;
  onExpired: () => void;
}

type StatusFilter = 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED';

const MAX_REASON = 2000;

export default function ProducerRequestsPage({
  session,
  onExpired,
}: ProducerRequestsPageProps) {
  const [requests, setRequests] = useState<ExcerptRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<StatusFilter>('PENDING');
  const [reloadKey, setReloadKey] = useState(0);

  const [approveTarget, setApproveTarget] = useState<ExcerptRequest | null>(null);
  const [rejectTarget, setRejectTarget] = useState<ExcerptRequest | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [actionBusy, setActionBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ request: ExcerptRequest; kind: 'approved' | 'rejected' } | null>(
    null,
  );

  useEffect(() => {
    let cancelled = false;
    setError(null);
    listProducerExcerptRequests(session)
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
          cause instanceof Error ? cause.message : 'Could not load the request queue',
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

  function replaceRequest(updated: ExcerptRequest) {
    setRequests((current) =>
      current.map((item) => (item.id === updated.id ? updated : item)),
    );
  }

  function closeDialogs() {
    setApproveTarget(null);
    setRejectTarget(null);
    setRejectReason('');
    setActionError(null);
  }

  async function confirmApprove() {
    if (!approveTarget) return;
    setActionBusy(true);
    setActionError(null);
    try {
      const updated = await approveExcerptRequest(session, approveTarget.id);
      replaceRequest(updated);
      setNotice({ request: updated, kind: 'approved' });
      closeDialogs();
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setActionError(
        cause instanceof Error ? cause.message : 'Request could not be approved',
      );
    } finally {
      setActionBusy(false);
    }
  }

  async function confirmReject() {
    if (!rejectTarget) return;
    setActionBusy(true);
    setActionError(null);
    try {
      const updated = await rejectExcerptRequest(
        session,
        rejectTarget.id,
        rejectReason,
      );
      replaceRequest(updated);
      setNotice({ request: updated, kind: 'rejected' });
      closeDialogs();
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        onExpired();
        return;
      }
      setActionError(
        cause instanceof Error ? cause.message : 'Request could not be rejected',
      );
    } finally {
      setActionBusy(false);
    }
  }

  if (loading) {
    return (
      <div className="page">
        <div className="loading-panel">
          <span className="spinner" />
          Loading the request queue...
        </div>
      </div>
    );
  }

  if (error && requests.length === 0) {
    return (
      <div className="page">
        <div className="empty-panel">
          <h2>Queue unavailable</h2>
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
          <span className="eyebrow">Producer queue</span>
          <h1>Excerpt requests.</h1>
          <p>
            Review guest requests for your episodes. Approval creates the excerpt
            through the existing excerpt service; rejection needs no reason but a
            short note helps the guest.
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

      {notice && (
        <section className="notice-panel" role="status">
          <span className="notice-icon"><CheckIcon size={20} /></span>
          <div>
            <strong>
              {notice.kind === 'approved' ? 'Request approved' : 'Request rejected'}
              : {notice.request.title}
            </strong>
            <p>
              {notice.kind === 'approved'
                ? `Excerpt ${shortId(notice.request.excerptId ?? '')} created for ${notice.request.episodeTitle}. The guest has been notified.`
                : `${notice.request.episodeTitle} · The guest has been notified with your reason if supplied.`}
            </p>
          </div>
          <span
            className={`badge badge-${badgeTone(notice.request.status)}`}
          >
            {titleCase(notice.request.status)}
          </span>
        </section>
      )}

      <section className="panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">Request management</span>
            <h2>Requests for your episodes</h2>
          </div>
          <SegmentedFilter
            ariaLabel="Filter requests by status"
            value={filter}
            onChange={(value) => setFilter(value as StatusFilter)}
            options={[
              { value: 'PENDING', label: 'Pending', count: counts.PENDING },
              { value: 'APPROVED', label: 'Approved', count: counts.APPROVED },
              { value: 'REJECTED', label: 'Rejected', count: counts.REJECTED },
              { value: 'ALL', label: 'All', count: counts.ALL },
            ]}
          />
        </div>

        <div className="request-list">
          {visible.map((request) => (
            <article className="request-card" key={request.id}>
              <div className="request-card-head">
                <div>
                  <span className="episode-code">
                    {request.episodeTitle} · Guest {shortId(request.guestId)}
                  </span>
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
                <span>Submitted {formatDateTime(request.createdAt)}</span>
                {request.decidedAt && (
                  <span>Decided {formatDateTime(request.decidedAt)}</span>
                )}
                {request.decidedBy && (
                  <span>By {shortId(request.decidedBy)}</span>
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

              <div className="request-actions">
                {request.status === 'PENDING' ? (
                  <>
                    <button
                      className="button button-secondary"
                      onClick={() => {
                        setNotice(null);
                        setActionError(null);
                        setRejectReason('');
                        setRejectTarget(request);
                      }}
                    >
                      Reject
                    </button>
                    <button
                      className="button button-primary"
                      onClick={() => {
                        setNotice(null);
                        setActionError(null);
                        setApproveTarget(request);
                      }}
                    >
                      Approve request
                    </button>
                  </>
                ) : (
                  <span className="completed-mark">
                    <CheckIcon />
                    {request.status === 'APPROVED'
                      ? 'Decision recorded · excerpt created'
                      : 'Decision recorded · no excerpt created'}
                  </span>
                )}
              </div>
            </article>
          ))}

          {visible.length === 0 && (
            <p className="empty-copy">
              {requests.length === 0
                ? 'No guest requests have been submitted for your episodes yet.'
                : `No ${filter.toLowerCase()} requests.`}
            </p>
          )}
        </div>
      </section>

      {approveTarget && (
        <ConfirmDialog
          eyebrow="Approve request"
          title={`Approve ${approveTarget.title}?`}
          confirmLabel="Approve and create excerpt"
          busy={actionBusy}
          error={actionError}
          onCancel={closeDialogs}
          onConfirm={confirmApprove}
        >
          <p className="modal-copy">
            This creates the excerpt through the existing excerpt service and
            notifies the guest. Consent remains scoped to this excerpt and can be
            withdrawn later.
          </p>
          <div className="modal-scope">
            <span className="code-pill">
              {formatClock(approveTarget.startMs)}–{formatClock(approveTarget.endMs)}
            </span>
            <span>{approveTarget.episodeTitle}</span>
            <span>Guest {shortId(approveTarget.guestId)}</span>
          </div>
        </ConfirmDialog>
      )}

      {rejectTarget && (
        <ConfirmDialog
          eyebrow="Reject request"
          title={`Reject ${rejectTarget.title}?`}
          confirmLabel="Reject request"
          tone="danger"
          busy={actionBusy}
          error={actionError}
          onCancel={closeDialogs}
          onConfirm={confirmReject}
        >
          <p className="modal-copy">
            No excerpt will be created. The guest is notified with your reason if
            you provide one.
          </p>
          <div className="modal-scope">
            <span className="code-pill">
              {formatClock(rejectTarget.startMs)}–{formatClock(rejectTarget.endMs)}
            </span>
            <span>{rejectTarget.episodeTitle}</span>
          </div>
          <label className="reason-field">
            Reason <span className="optional">optional</span>
            <textarea
              value={rejectReason}
              maxLength={MAX_REASON}
              rows={3}
              placeholder="The clip contains unverified claims."
              onChange={(event) => setRejectReason(event.target.value)}
              autoFocus
            />
            <small>{rejectReason.length}/{MAX_REASON}</small>
          </label>
        </ConfirmDialog>
      )}
    </div>
  );
}
