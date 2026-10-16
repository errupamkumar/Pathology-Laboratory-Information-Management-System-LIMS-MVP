/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** REST base path or absolute URL, e.g. `/api/v1` (same origin) or `https://lims.example.com/api/v1`. */
  readonly VITE_API_BASE_URL?: string;
  /** Print-agent socket URL. Defaults to `ws(s)://<current host>/ws/print-agent`. */
  readonly VITE_PRINT_AGENT_WS_URL?: string;
  /** Label shown in the navbar badge (e.g. DEV, UAT). Hidden when `production`. */
  readonly VITE_APP_ENV?: string;
  /** `true` shows the seeded demo accounts on the sign-in page. Never enable in production. */
  readonly VITE_SHOW_DEMO_ACCOUNTS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
