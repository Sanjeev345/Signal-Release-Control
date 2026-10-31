import type {
  Edit,
  Episode,
  EpisodeSummary,
  Excerpt,
  ExcerptRequest,
  InAppNotification,
  Release,
  Session,
  Task,
  WithdrawalResult,
} from './types';

export const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
export const KEYCLOAK_URL =
  import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180';
export const KEYCLOAK_REALM =
  import.meta.env.VITE_KEYCLOAK_REALM ?? 'podcast-demo';
export const KEYCLOAK_CLIENT_ID =
  import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'podcast-dashboard';

const SESSION_KEY = 'podcast.demo.session';
const TOKEN_URL = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`;

interface JwtClaims {
  sub?: string;
  preferred_username?: string;
  exp?: number;
  realm_access?: { roles?: string[] };
}

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, message: string, code = 'REQUEST_FAILED') {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
  const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function claimsFromToken(token: string): JwtClaims {
  const payload = token.split('.')[1];
  if (!payload) {
    throw new Error('Keycloak returned an opaque access token');
  }
  return JSON.parse(decodeBase64Url(payload)) as JwtClaims;
}

export function sessionFromToken(token: string): Session {
  const claims = claimsFromToken(token);
  return {
    token,
    username: claims.preferred_username ?? 'demo-user',
    subject: claims.sub ?? '',
    roles: claims.realm_access?.roles ?? [],
    expiresAt: (claims.exp ?? Math.floor(Date.now() / 1000) + 3600) * 1000,
  };
}

export function loadSession(): Session | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    const session = JSON.parse(raw) as Session;
    if (session.expiresAt <= Date.now() + 30_000) {
      localStorage.removeItem(SESSION_KEY);
      return null;
    }
    return session;
  } catch {
    localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function saveSession(session: Session): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY);
}

export async function login(
  username: string,
  password: string,
): Promise<Session> {
  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: KEYCLOAK_CLIENT_ID,
      username,
      password,
      scope: 'openid profile email',
    }),
  });

  const payload = (await response.json().catch(() => ({}))) as {
    access_token?: string;
    error_description?: string;
    error?: string;
  };

  if (!response.ok || !payload.access_token) {
    throw new Error(
      payload.error_description ??
        payload.error ??
        'Keycloak rejected the sign-in attempt',
    );
  }

  return sessionFromToken(payload.access_token);
}

async function request<T>(
  path: string,
  session: Session,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${session.token}`,
      ...(init.headers ?? {}),
    },
  });

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as {
      detail?: string;
      title?: string;
      code?: string;
    };
    throw new ApiError(
      response.status,
      problem.detail ?? problem.title ?? `Request failed (${response.status})`,
      problem.code,
    );
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function listEpisodes(session: Session): Promise<Episode[]> {
  return request('/api/excerpts/episodes', session);
}

export function listExcerpts(
  session: Session,
  episodeId: string,
): Promise<Excerpt[]> {
  return request(`/api/excerpts/episodes/${episodeId}/excerpts`, session);
}

export function listEpisodeReleases(
  session: Session,
  episodeId: string,
): Promise<Release[]> {
  return request(`/api/releases?episodeId=${episodeId}`, session);
}

export function listMyReleases(session: Session): Promise<Release[]> {
  return request('/api/releases/me', session);
}

export function listEdits(
  session: Session,
  episodeId: string,
): Promise<Edit[]> {
  return request(`/api/distribution/edits?episodeId=${episodeId}`, session);
}

export function listTasks(
  session: Session,
  excerptId: string,
): Promise<Task[]> {
  return request(
    `/api/distribution/tasks?excerptId=${encodeURIComponent(excerptId)}`,
    session,
  );
}

export function updateTaskStatus(
  session: Session,
  taskId: string,
  status: 'IN_PROGRESS' | 'DONE',
): Promise<Task> {
  return request(`/api/distribution/tasks/${taskId}/status`, session, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export function withdraw(
  session: Session,
  releaseId: string,
  excerptId: string,
  reason: string,
): Promise<WithdrawalResult> {
  return request(
    `/api/releases/${releaseId}/scopes/${excerptId}/withdrawals`,
    session,
    {
      method: 'POST',
      body: JSON.stringify({ reason }),
    },
  );
}

export interface SubmitExcerptRequestPayload {
  episodeId: string;
  title: string;
  description?: string;
  startMs: number;
  endMs: number;
}

export function listAvailableEpisodes(
  session: Session,
): Promise<EpisodeSummary[]> {
  return request('/api/excerpts/episodes/available', session);
}

export function submitExcerptRequest(
  session: Session,
  payload: SubmitExcerptRequestPayload,
): Promise<ExcerptRequest> {
  return request('/api/excerpts/requests', session, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function listMyExcerptRequests(
  session: Session,
): Promise<ExcerptRequest[]> {
  return request('/api/excerpts/requests/me', session);
}

export function listProducerExcerptRequests(
  session: Session,
): Promise<ExcerptRequest[]> {
  return request('/api/excerpts/requests', session);
}

export function approveExcerptRequest(
  session: Session,
  requestId: string,
): Promise<ExcerptRequest> {
  return request(`/api/excerpts/requests/${requestId}/approve`, session, {
    method: 'POST',
  });
}

export function rejectExcerptRequest(
  session: Session,
  requestId: string,
  reason?: string,
): Promise<ExcerptRequest> {
  return request(`/api/excerpts/requests/${requestId}/reject`, session, {
    method: 'POST',
    body: JSON.stringify({ reason: reason?.trim() || null }),
  });
}

export function listNotifications(
  session: Session,
  unreadOnly = false,
): Promise<InAppNotification[]> {
  return request(
    `/api/notifications${unreadOnly ? '?unreadOnly=true' : ''}`,
    session,
  );
}

export function markNotificationRead(
  session: Session,
  notificationId: string,
): Promise<InAppNotification> {
  return request(`/api/notifications/${notificationId}/read`, session, {
    method: 'PATCH',
  });
}

export function markAllNotificationsRead(
  session: Session,
): Promise<{ updated: number }> {
  return request('/api/notifications/read-all', session, {
    method: 'POST',
  });
}

export interface TaskFilters {
  episodeId?: string;
  provider?: string;
  status?: string;
}

export function listProducerTasks(
  session: Session,
  filters: TaskFilters = {},
): Promise<Task[]> {
  const params = new URLSearchParams();
  if (filters.episodeId) params.set('episodeId', filters.episodeId);
  if (filters.provider) params.set('provider', filters.provider);
  if (filters.status) params.set('status', filters.status);
  const query = params.toString();
  return request(`/api/distribution/tasks${query ? `?${query}` : ''}`, session);
}

export interface UpdateTaskPayload {
  status?: 'IN_PROGRESS' | 'DONE';
  notes?: string;
  evidenceUrl?: string;
}

export function updateTask(
  session: Session,
  taskId: string,
  payload: UpdateTaskPayload,
): Promise<Task> {
  return request(`/api/distribution/tasks/${taskId}`, session, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

