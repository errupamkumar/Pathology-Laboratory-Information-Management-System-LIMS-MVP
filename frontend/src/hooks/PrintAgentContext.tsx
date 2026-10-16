import React, { createContext, useCallback, useContext } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../components/ui/Toaster';
import type { PrintJob, PrintTarget } from '../types';
import { PrintAgentState, usePrintAgentSocket } from './usePrintAgentSocket';

const PrintAgentContext = createContext<PrintAgentState | null>(null);

export const PRINT_TARGET_LABELS: Record<PrintTarget, string> = {
  BARCODE_THERMAL: 'ZPL tube label',
  INVOICE_POS: 'ESC/POS receipt',
  REPORT_LASER: 'A4 laser report',
};

/** One print-agent connection per signed-in tab, shared by the navbar badge and the spooler monitor. */
export const PrintAgentProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { accessToken } = useAuth();
  const toast = useToast();

  const onJob = useCallback(
    (job: PrintJob) => toast.info(`Spooled: ${PRINT_TARGET_LABELS[job.target] ?? job.target}`, `${job.title} · ${job.accessionNumber}`),
    [toast],
  );

  const state = usePrintAgentSocket(accessToken, onJob);
  return <PrintAgentContext.Provider value={state}>{children}</PrintAgentContext.Provider>;
};

export function usePrintAgent(): PrintAgentState {
  const context = useContext(PrintAgentContext);
  if (!context) {
    throw new Error('usePrintAgent must be used inside <PrintAgentProvider>');
  }
  return context;
}
