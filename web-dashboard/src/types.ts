export interface Scope {
  id: string;
  excerptId: string;
  excerptCode: string;
  startMs: number;
  endMs: number;
  scopeType: string;
  status: string;
  grantedAt: string;
  withdrawnAt: string | null;
  withdrawnBy: string | null;
  withdrawalReason: string | null;
}

export interface Release {
  id: string;
  guestId: string;
  episodeId: string;
  status: string;
  grantedAt: string;
  withdrawnAt: string | null;
  withdrawnBy: string | null;
  withdrawalReason: string | null;
  scopes: Scope[];
}

export interface Excerpt {
  id: string;
  code: string;
  episodeId: string;
  guestId: string;
  startMs: number;
  endMs: number;
  transcript: string;
  tagStatus: string;
  createdAt: string;
}

export interface Episode {
  id: string;
  code: string;
  title: string;
  producerId: string;
  publishedAt: string;
  createdAt: string;
}

export interface Task {
  id: string;
  editId: string;
  editCode: string;
  provider: string;
  excerptId: string;
  eventId: string;
  taskType: string;
  status: string;
  priority: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  notes: string | null;
  evidenceUrl: string | null;
  updatedBy: string | null;
}

export interface Edit {
  id: string;
  code: string;
  episodeId: string;
  provider: string;
  externalReference: string;
  distributionStatus: string;
  distributedAt: string;
  tasks: Task[];
}

export interface WithdrawalResult {
  eventId: string;
  releaseId: string;
  excerptId: string;
  status: string;
}

export interface Session {
  token: string;
  username: string;
  subject: string;
  roles: string[];
  expiresAt: number;
}

export interface EpisodeSummary {
  id: string;
  code: string;
  title: string;
  publishedAt: string | null;
}

export type ExcerptRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ExcerptRequest {
  id: string;
  episodeId: string;
  episodeTitle: string;
  guestId: string;
  title: string;
  description: string | null;
  startMs: number;
  endMs: number;
  status: ExcerptRequestStatus;
  rejectionReason: string | null;
  excerptId: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InAppNotification {
  id: string;
  message: string;
  eventType: string;
  read: boolean;
  relatedId: string | null;
  createdAt: string;
}
