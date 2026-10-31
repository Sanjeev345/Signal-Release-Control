import type { ReactNode } from 'react';
import { CloseIcon } from './Icons';

interface ConfirmDialogProps {
  eyebrow?: string;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  tone?: 'primary' | 'danger';
  busy?: boolean;
  error?: string | null;
  formId?: string;
  onCancel: () => void;
  onConfirm?: () => void;
}

export function ConfirmDialog({
  eyebrow,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Cancel',
  tone = 'primary',
  busy = false,
  error,
  formId,
  onCancel,
  onConfirm,
}: ConfirmDialogProps) {
  return (
    <div
      className="modal-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !busy) onCancel();
      }}
    >
      <div className="modal-card">
        <div className="modal-heading">
          <div>
            {eyebrow && <span className="eyebrow">{eyebrow}</span>}
            <h2>{title}</h2>
          </div>
          <button
            type="button"
            className="icon-button"
            aria-label="Close"
            onClick={onCancel}
            disabled={busy}
          >
            <CloseIcon />
          </button>
        </div>
        <div className="dialog-body">{children}</div>
        {error && <p className="form-error">{error}</p>}
        <div className="modal-actions">
          <button
            type="button"
            className="button button-secondary"
            onClick={onCancel}
            disabled={busy}
          >
            {cancelLabel}
          </button>
          <button
            type={formId ? 'submit' : 'button'}
            className={`button ${tone === 'danger' ? 'button-danger' : 'button-primary'}`}
            form={formId}
            onClick={formId ? undefined : onConfirm}
            disabled={busy}
          >
            {busy ? 'Working...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

export interface FilterOption {
  value: string;
  label: string;
  count?: number;
}

interface SegmentedFilterProps {
  options: FilterOption[];
  value: string;
  onChange: (value: string) => void;
  ariaLabel: string;
}

export function SegmentedFilter({
  options,
  value,
  onChange,
  ariaLabel,
}: SegmentedFilterProps) {
  return (
    <div className="segmented" role="group" aria-label={ariaLabel}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className={`segmented-item ${value === option.value ? 'is-active' : ''}`}
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
          {typeof option.count === 'number' && (
            <span className="segmented-count">{option.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}
