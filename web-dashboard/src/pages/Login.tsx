import { useState, type FormEvent } from 'react';
import { login } from '../api';
import type { Session } from '../types';
import { ArrowIcon, ShieldIcon, WaveMark } from '../components/Icons';

interface LoginProps {
  onLogin: (session: Session) => void;
}

const DEMO_ACCOUNTS = [
  {
    label: 'Guest',
    name: 'Ava Rao',
    username: 'ava',
    password: 'guest123',
    detail: 'Withdraw an excerpt consent',
  },
  {
    label: 'Producer',
    name: 'Priya Shah',
    username: 'priya',
    password: 'producer123',
    detail: 'Audit release + distribution state',
  },
];

export default function Login({ onLogin }: LoginProps) {
  const [username, setUsername] = useState('ava');
  const [password, setPassword] = useState('guest123');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function signIn(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session = await login(username.trim(), password);
      onLogin(session);
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Could not reach the identity provider',
      );
    } finally {
      setBusy(false);
    }
  }

  function useDemoAccount(account: (typeof DEMO_ACCOUNTS)[number]) {
    setUsername(account.username);
    setPassword(account.password);
    setError(null);
  }

  return (
    <div className="login-page">
      <section className="login-story">
        <div className="brand-lockup">
          <span className="brand-mark">
            <WaveMark size={34} />
          </span>
          <span>
            <strong>Signal</strong>
            <small>Release Control</small>
          </span>
        </div>

        <div className="story-copy">
          <span className="eyebrow">
            <ShieldIcon size={15} />
            Consent-aware distribution
          </span>
          <h1>
            One excerpt withdrawn.
            <br />
            Every placement accounted for.
          </h1>
          <p>
            A guest can revoke a single tagged excerpt without touching the rest
            of the episode. Signal fans the event out to every distribution edit
            that carries it, then tracks the cleanup work in one place.
          </p>
        </div>

        <div className="story-flow">
          <div>
            <span>01</span>
            <strong>Scoped consent</strong>
            <p>The release scope is tied to one excerpt, not the whole episode.</p>
          </div>
          <div>
            <span>02</span>
            <strong>Event outbox</strong>
            <p>The withdrawal is committed once and published reliably.</p>
          </div>
          <div>
            <span>03</span>
            <strong>One task per carrying edit</strong>
            <p>Only the edits actually carrying that excerpt are queued.</p>
          </div>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <div className="login-heading">
            <span className="eyebrow">Demo access</span>
            <h2>Sign in with Keycloak</h2>
            <p>The identity provider issues the realm roles used by the gateway.</p>
          </div>

          <form onSubmit={signIn} className="login-form">
            <label>
              Username
              <input
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                required
              />
            </label>
            <label>
              Password
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                required
              />
            </label>
            {error && <p className="form-error">{error}</p>}
            <button type="submit" className="button button-primary" disabled={busy}>
              {busy ? 'Signing in...' : 'Enter release control'}
              {!busy && <ArrowIcon />}
            </button>
          </form>

          <div className="demo-divider">
            <span>or use a demo identity</span>
          </div>

          <div className="demo-accounts">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                type="button"
                key={account.username}
                className="demo-account"
                onClick={() => useDemoAccount(account)}
              >
                <span className={`avatar avatar-${account.label.toLowerCase()}`}>
                  {account.name.charAt(0)}
                </span>
                <span>
                  <strong>{account.name}</strong>
                  <small>{account.detail}</small>
                </span>
                <span className="demo-role">{account.label}</span>
              </button>
            ))}
          </div>

          <p className="login-footnote">
            Demo credentials are local-only. Production deployments would use
            Authorization Code + PKCE.
          </p>
        </div>
      </section>
    </div>
  );
}
