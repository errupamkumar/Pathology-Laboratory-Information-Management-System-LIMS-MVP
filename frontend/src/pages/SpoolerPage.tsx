import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Code, Cpu, FileText, RefreshCw, Terminal } from 'lucide-react';
import { CardListSkeleton } from '../components/ui/Skeleton';
import { useToast } from '../components/ui/Toaster';
import { PRINT_TARGET_LABELS, usePrintAgent } from '../hooks/PrintAgentContext';
import { formatTime } from '../lib/format';
import { base64SizeKb, parseZplLabel, stripEscPos } from '../lib/printPayload';
import { api } from '../services/api';
import type { PrintJob, PrintTarget, SpoolerStatus } from '../types';

const FILTERS: { value: PrintTarget | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'BARCODE_THERMAL', label: 'Labels' },
  { value: 'INVOICE_POS', label: 'Receipts' },
  { value: 'REPORT_LASER', label: 'Reports' },
];

const SpoolerPage: React.FC = () => {
  const toast = useToast();
  const { connected, agentCount, liveJobs } = usePrintAgent();
  const [serverJobs, setServerJobs] = useState<PrintJob[]>([]);
  const [status, setStatus] = useState<SpoolerStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [filter, setFilter] = useState<PrintTarget | 'ALL'>('ALL');

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [jobs, spoolerStatus] = await Promise.all([api.getPrintJobs(), api.getSpoolerStatus()]);
      setServerJobs(jobs);
      setStatus(spoolerStatus);
    } catch (error) {
      toast.apiError(error, 'Could not load the print queue');
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Live socket jobs first, then the server's recent history without duplicates
  const allJobs = useMemo(() => {
    const seen = new Set(liveJobs.map(j => j.jobId));
    return [...liveJobs, ...serverJobs.filter(j => !seen.has(j.jobId))];
  }, [liveJobs, serverJobs]);

  const jobs = filter === 'ALL' ? allJobs : allJobs.filter(j => j.target === filter);
  const selected = allJobs.find(j => j.jobId === selectedId) ?? jobs[0] ?? null;

  return (
    <div className="mx-auto max-w-7xl space-y-4 px-4 py-4 sm:space-y-6 sm:py-6">
      <div className="uiverse-card flex flex-col justify-between gap-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 md:flex-row md:items-center">
        <div className="flex items-center space-x-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary/10">
            <Cpu className="h-5 w-5 text-brand-primary" aria-hidden />
          </div>
          <div>
            <h1 className="flex flex-wrap items-center gap-2 text-base font-bold tracking-tight text-slate-900 sm:text-lg">
              <span>Print Agent & Silent Spooler</span>
              <span className="rounded-full bg-cyan-100 px-2 py-0.5 font-mono text-[10px] font-bold text-cyan-900">/ws/print-agent</span>
            </h1>
            <p className="text-xs text-slate-500">Raw ZPL, ESC/POS and PDF streams dispatched to label, receipt and laser printers without browser dialogs.</p>
          </div>
        </div>
        <div className="flex items-center space-x-2 sm:space-x-3">
          <div className="flex items-center space-x-2 rounded-full border border-brand-grid bg-slate-50 px-3 py-1.5 text-xs">
            <span className={`h-2.5 w-2.5 rounded-full ${connected ? 'animate-pulse bg-emerald-500' : 'bg-amber-500'}`} />
            <span className="font-bold text-slate-800">
              {connected ? `This workstation connected` : 'Reconnecting…'}
              {status && ` · ${status.activeBridgeAgents || agentCount} agent${(status.activeBridgeAgents || agentCount) === 1 ? '' : 's'}`}
            </span>
          </div>
          <button
            type="button"
            onClick={() => void refresh()}
            className="rounded-lg border border-brand-grid p-2 text-slate-600 hover:bg-slate-50 hover:text-brand-primary"
            aria-label="Refresh print queue"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:gap-6 lg:grid-cols-12">
        <section className="uiverse-card flex flex-col overflow-hidden rounded-xl border border-brand-grid/80 bg-white shadow-sm lg:col-span-5" aria-label="Print queue">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-brand-grid bg-slate-50/70 p-3 sm:p-4">
            <span className="text-xs font-bold uppercase tracking-wide text-slate-700">Queue ({jobs.length})</span>
            <div className="flex flex-wrap gap-1" role="group" aria-label="Filter by printer">
              {FILTERS.map(f => (
                <button
                  key={f.value}
                  type="button"
                  onClick={() => setFilter(f.value)}
                  aria-pressed={filter === f.value}
                  className={`rounded-lg px-2.5 py-1 text-[10px] font-bold transition-all ${
                    filter === f.value ? 'bg-brand-primary text-white shadow-sm' : 'border border-brand-grid bg-white text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {f.label}
                </button>
              ))}
            </div>
          </div>
          <div className="max-h-[520px] divide-y divide-brand-grid/60 overflow-y-auto">
            {loading && allJobs.length === 0 ? (
              <div className="p-3">
                <CardListSkeleton />
              </div>
            ) : jobs.length === 0 ? (
              <p className="p-8 text-center text-xs text-slate-400">No print jobs in the spool buffer.</p>
            ) : (
              jobs.map(job => {
                const isSelected = selected?.jobId === job.jobId;
                return (
                  <button
                    key={job.jobId}
                    type="button"
                    onClick={() => setSelectedId(job.jobId)}
                    aria-current={isSelected}
                    className={`block w-full space-y-1.5 p-3.5 text-left text-xs transition-colors ${
                      isSelected ? 'border-l-4 border-l-brand-primary bg-brand-primary/10' : 'hover:bg-slate-50/80'
                    }`}
                  >
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-bold text-slate-900">{job.title}</span>
                      <TargetBadge target={job.target} />
                    </span>
                    <span className="flex items-center justify-between font-mono text-[11px] text-slate-500">
                      <span>
                        Acc: <strong>{job.accessionNumber}</strong>
                      </span>
                      <span>{formatTime(job.queuedAt)}</span>
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </section>

        <section className="uiverse-card flex flex-col space-y-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 lg:col-span-7" aria-label="Payload inspector">
          <div className="flex items-center justify-between border-b border-brand-grid pb-3">
            <h2 className="flex items-center space-x-2 text-xs font-bold uppercase tracking-wide text-slate-900">
              <Terminal className="h-4 w-4 text-brand-primary" aria-hidden />
              <span>Payload & print simulation</span>
            </h2>
            {selected && <span className="font-mono text-[10px] font-bold text-slate-400">Job {selected.jobId.slice(0, 8)}</span>}
          </div>
          {selected ? <JobInspector job={selected} /> : <p className="p-12 text-center text-xs text-slate-400">Select a job to inspect its wire payload.</p>}
        </section>
      </div>
    </div>
  );
};

const TargetBadge: React.FC<{ target: PrintTarget }> = ({ target }) => {
  const styles: Record<PrintTarget, string> = {
    BARCODE_THERMAL: 'bg-purple-100 text-purple-900 border-purple-200',
    INVOICE_POS: 'bg-blue-100 text-blue-900 border-blue-200',
    REPORT_LASER: 'bg-emerald-100 text-emerald-900 border-emerald-200',
  };
  return (
    <span className={`shrink-0 rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${styles[target] ?? 'bg-slate-100 text-slate-800'}`}>
      {PRINT_TARGET_LABELS[target] ?? target}
    </span>
  );
};

const JobInspector: React.FC<{ job: PrintJob }> = ({ job }) => (
  <div className="space-y-4">
    <dl className="grid grid-cols-3 gap-2 rounded-xl border border-brand-grid bg-slate-50/80 p-3 text-xs">
      <div>
        <dt className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Target</dt>
        <dd className="mt-0.5 font-bold text-slate-800">{job.target}</dd>
      </div>
      <div>
        <dt className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Content-Type</dt>
        <dd className="mt-0.5 font-mono text-[11px] text-slate-700">{job.contentType}</dd>
      </div>
      <div>
        <dt className="text-[10px] font-bold uppercase tracking-wider text-slate-400">Queued</dt>
        <dd className="mt-0.5 font-mono text-[11px] text-slate-700">{formatTime(job.queuedAt)}</dd>
      </div>
    </dl>

    {job.target === 'BARCODE_THERMAL' && <LabelPreview payload={job.payload} />}

    {job.target === 'INVOICE_POS' && (
      <div className="space-y-2">
        <span className="block text-xs font-bold uppercase tracking-wide text-slate-700">80mm receipt (control codes stripped)</span>
        <pre className="max-h-64 max-w-[360px] overflow-y-auto whitespace-pre-wrap rounded-xl border border-amber-200 bg-amber-50/40 p-4 font-mono text-[11px] leading-tight text-slate-800 select-text">
          {stripEscPos(job.payload)}
        </pre>
      </div>
    )}

    {job.target === 'REPORT_LASER' && (
      <div className="space-y-2 rounded-xl border border-emerald-200 bg-emerald-50/80 p-4 text-xs">
        <p className="flex items-center space-x-2 font-bold text-emerald-800">
          <FileText className="h-5 w-5 text-emerald-600" aria-hidden />
          <span>A4 laser report stream</span>
        </p>
        <p className="text-[11px] text-emerald-800">Base64-encoded PDF (~{base64SizeKb(job.payload)} KB) ready for direct spooling to the laser printer.</p>
      </div>
    )}

    <div className="space-y-1.5">
      <span className="flex items-center space-x-1 text-xs font-bold uppercase tracking-wide text-slate-700">
        <Code className="h-3.5 w-3.5 text-slate-500" aria-hidden />
        <span>Raw wire payload</span>
      </span>
      <textarea
        readOnly
        rows={6}
        aria-label="Raw wire payload"
        value={job.target === 'REPORT_LASER' && job.payload.length > 4000 ? `${job.payload.slice(0, 4000)}…` : job.payload}
        className="w-full rounded-xl border border-slate-800 bg-slate-900 p-3 font-mono text-[11px] text-emerald-400 outline-none select-all"
      />
    </div>
  </div>
);

const LabelPreview: React.FC<{ payload: string }> = ({ payload }) => {
  const label = parseZplLabel(payload);
  const [title, patient, ...rest] = label.textFields;
  const footer = rest[rest.length - 1];
  return (
    <div className="space-y-2">
      <span className="block text-xs font-bold uppercase tracking-wide text-slate-700">2" × 1" thermal tube label</span>
      <div className="flex h-[170px] max-w-[340px] flex-col justify-between rounded-xl border-2 border-slate-900 bg-white p-3 font-sans shadow-md">
        <div className="border-b border-slate-300 pb-1 text-[11px] font-black tracking-tight text-slate-900">{title ?? 'APEX PATH LAB'}</div>
        {patient && <div className="truncate text-[10px] font-semibold text-slate-700">{patient}</div>}
        <div className="my-auto space-y-1 text-center">
          <div className="flex h-10 items-end justify-center space-x-0.5 px-4" aria-hidden>
            {Array.from({ length: 42 }).map((_, i) => (
              <div key={i} className="bg-black" style={{ width: i % 3 === 0 ? '3px' : '1.5px', height: i % 4 === 0 ? '34px' : '28px' }} />
            ))}
          </div>
          <span className="block font-mono text-xs font-bold tracking-widest text-slate-900">{label.barcode ?? '—'}</span>
        </div>
        {footer && <div className="truncate border-t border-slate-200 pt-1 text-[10px] font-semibold text-slate-600">{footer}</div>}
      </div>
    </div>
  );
};

export default SpoolerPage;
