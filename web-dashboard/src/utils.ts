export function formatClock(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export function formatDateTime(value: string | null): string {
  if (!value) return '—';
  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

export function shortId(value: string): string {
  return value.slice(0, 8);
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function badgeTone(value: string): string {
  const normalized = value.toUpperCase();
  if (
    ['ACTIVE', 'DISTRIBUTED', 'DONE', 'SUCCEEDED', 'APPROVED'].includes(normalized)
  ) {
    return 'positive';
  }
  if (['WITHDRAWN', 'FAILED', 'CANCELLED', 'REJECTED'].includes(normalized)) {
    return 'negative';
  }
  if (
    ['PENDING', 'IN_PROGRESS', 'PROCESSING', 'QUEUED', 'OPEN'].includes(normalized)
  ) {
    return 'pending';
  }
  return 'neutral';
}

export function notificationTone(eventType: string): string {
  switch (eventType) {
    case 'REQUEST_APPROVED':
    case 'TASK_COMPLETED':
      return 'positive';
    case 'REQUEST_REJECTED':
    case 'CONSENT_WITHDRAWN':
      return 'negative';
    case 'REQUEST_SUBMITTED':
      return 'pending';
    default:
      return 'neutral';
  }
}

