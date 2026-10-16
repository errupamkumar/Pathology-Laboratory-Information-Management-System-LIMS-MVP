/**
 * Environment-specific configuration, resolved from Vite `VITE_*` variables at build time
 * (see `.env.example`). Everything has a same-origin default so a reverse-proxied deployment
 * needs no configuration at all.
 */

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '');

function defaultPrintAgentUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/print-agent`;
}

export const env = {
  apiBaseUrl: trimTrailingSlash(import.meta.env.VITE_API_BASE_URL || '/api/v1'),
  appEnv: import.meta.env.VITE_APP_ENV || import.meta.env.MODE,
  showDemoAccounts: import.meta.env.VITE_SHOW_DEMO_ACCOUNTS === 'true',
  printAgentUrl(): string {
    return import.meta.env.VITE_PRINT_AGENT_WS_URL || defaultPrintAgentUrl();
  },
};
