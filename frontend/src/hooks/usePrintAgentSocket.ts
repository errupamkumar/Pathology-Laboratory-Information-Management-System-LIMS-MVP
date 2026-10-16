import { useEffect, useRef, useState } from 'react';
import { env } from '../config/env';
import type { PrintJob } from '../types';

export interface PrintAgentState {
  connected: boolean;
  agentCount: number;
  liveJobs: PrintJob[];
}

const MAX_LIVE_JOBS = 100;
const MAX_BACKOFF_MS = 30_000;

/** Browsers cannot set headers on a WebSocket upgrade, so the backend accepts the token as a query parameter. */
export function printAgentUrl(token: string, base: string = env.printAgentUrl()): string {
  return `${base}${base.includes('?') ? '&' : '?'}access_token=${encodeURIComponent(token)}`;
}

function isPrintJob(value: unknown): value is PrintJob {
  const job = value as Partial<PrintJob> | null;
  return !!job && typeof job.jobId === 'string' && typeof job.target === 'string' && typeof job.payload === 'string';
}

/**
 * Connects this workstation to `/ws/print-agent` while a session exists. A failed or rejected
 * connection retries with exponential backoff (1s → 30s) instead of hammering the server.
 */
export function usePrintAgentSocket(token: string | null, onJob?: (job: PrintJob) => void): PrintAgentState {
  const [connected, setConnected] = useState(false);
  const [agentCount, setAgentCount] = useState(0);
  const [liveJobs, setLiveJobs] = useState<PrintJob[]>([]);
  const onJobRef = useRef(onJob);
  onJobRef.current = onJob;

  useEffect(() => {
    if (!token) {
      setConnected(false);
      setAgentCount(0);
      setLiveJobs([]);
      return;
    }

    let socket: WebSocket | null = null;
    let retryTimer: number | undefined;
    let attempt = 0;
    let disposed = false;

    const scheduleReconnect = () => {
      if (disposed) return;
      const delay = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** attempt) + Math.random() * 500;
      attempt += 1;
      retryTimer = window.setTimeout(connect, delay);
    };

    function connect() {
      if (disposed) return;
      try {
        socket = new WebSocket(printAgentUrl(token!));
      } catch {
        scheduleReconnect();
        return;
      }

      socket.onopen = () => {
        attempt = 0;
        setConnected(true);
      };

      socket.onmessage = event => {
        let data: unknown;
        try {
          data = JSON.parse(event.data);
        } catch {
          return; // non-JSON frames are not part of the protocol
        }
        const message = data as { type?: string; activeAgents?: number };
        if (message.type === 'PRINT_AGENT_CONNECTED') {
          setAgentCount(message.activeAgents ?? 1);
        } else if (isPrintJob(data)) {
          const job = data;
          setLiveJobs(current => [job, ...current.filter(j => j.jobId !== job.jobId)].slice(0, MAX_LIVE_JOBS));
          onJobRef.current?.(job);
        }
      };

      socket.onclose = () => {
        setConnected(false);
        scheduleReconnect();
      };

      // onclose always follows onerror; reconnection is handled there
      socket.onerror = () => socket?.close();
    }

    connect();

    return () => {
      disposed = true;
      window.clearTimeout(retryTimer);
      if (socket) {
        socket.onclose = null;
        socket.close();
      }
      setConnected(false);
    };
  }, [token]);

  return { connected, agentCount, liveJobs };
}
