import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { AlertTriangle, CheckCircle2, Clock, Filter, RefreshCw, Scan, ShieldAlert, XCircle } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { a11yProps, Field, inputClass } from '../components/ui/Field';
import { Modal } from '../components/ui/Modal';
import { Pagination } from '../components/ui/Pagination';
import { TableSkeleton } from '../components/ui/Skeleton';
import { useToast } from '../components/ui/Toaster';
import { formatTime, humanize } from '../lib/format';
import { REJECTION_REASONS, RejectionForm, rejectionSchema, ScanForm, scanSchema } from '../schemas/clinical';
import { api } from '../services/api';
import type { PageResponse, SampleStatus, SpecimenSample } from '../types';

const PAGE_SIZE = 25;
const STATUS_FILTERS: { value: SampleStatus | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'COLLECTED', label: 'Collected' },
  { value: 'RECEIVED', label: 'Received' },
  { value: 'REJECTED', label: 'Rejected' },
];

const AccessioningPage: React.FC = () => {
  const toast = useToast();
  const { can } = useAuth();
  const canAccession = can('accession');

  const [statusFilter, setStatusFilter] = useState<SampleStatus | ''>('COLLECTED');
  const [pageIndex, setPageIndex] = useState(0);
  const [page, setPage] = useState<PageResponse<SpecimenSample> | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyBarcode, setBusyBarcode] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<SpecimenSample | null>(null);
  const requestSeq = useRef(0);

  const fetchSamples = useCallback(async () => {
    const seq = ++requestSeq.current;
    setLoading(true);
    try {
      const result = await api.getSamples(statusFilter || undefined, { page: pageIndex, size: PAGE_SIZE, sort: 'createdAt,desc' });
      if (seq === requestSeq.current) setPage(result);
    } catch (error) {
      if (seq === requestSeq.current) toast.apiError(error, 'Could not load the specimen worklist');
    } finally {
      if (seq === requestSeq.current) setLoading(false);
    }
  }, [statusFilter, pageIndex, toast]);

  useEffect(() => {
    void fetchSamples();
  }, [fetchSamples]);

  const changeFilter = (value: SampleStatus | '') => {
    setStatusFilter(value);
    setPageIndex(0);
  };

  // ---- Barcode scan receipt ----
  const scanForm = useForm<ScanForm>({ resolver: zodResolver(scanSchema), defaultValues: { barcode: '' } });

  const receive = async (barcode: string) => {
    setBusyBarcode(barcode);
    try {
      const sample = await api.receiveSample(barcode);
      toast.success(`Sample ${sample.barcode} received`, `${sample.specimenType} · ${sample.accessionNumber} is now in the lab chain of custody`);
      await fetchSamples();
      return true;
    } catch (error) {
      toast.apiError(error, `Could not receive ${barcode}`);
      return false;
    } finally {
      setBusyBarcode(null);
    }
  };

  const onScan = async ({ barcode }: ScanForm) => {
    const ok = await receive(barcode);
    if (ok) {
      scanForm.reset({ barcode: '' });
    }
    // Keep the cursor in the scan box: the next barcode scanner read arrives immediately
    scanForm.setFocus('barcode');
  };

  useEffect(() => {
    if (canAccession) scanForm.setFocus('barcode');
  }, [canAccession, scanForm]);

  const samples = page?.content ?? [];

  return (
    <div className="mx-auto max-w-7xl space-y-4 px-4 py-4 sm:space-y-6 sm:py-6">
      <div className="uiverse-card flex flex-col justify-between gap-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 md:flex-row md:items-center">
        <div>
          <div className="flex items-center space-x-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-primary/10">
              <Scan className="h-4 w-4 text-brand-primary" aria-hidden />
            </div>
            <h1 className="text-base font-bold tracking-tight text-slate-900 sm:text-lg">Specimen Accessioning & Chain of Custody</h1>
          </div>
          <p className="mt-1 text-xs text-slate-500">Scan to receive specimens; reject pre-analytical failures with a recorded reason.</p>
        </div>

        {canAccession ? (
          <form onSubmit={scanForm.handleSubmit(onScan)} noValidate className="flex flex-col gap-1">
            <div className="flex flex-col items-stretch gap-2 sm:flex-row sm:items-center">
              <div className="relative flex-1">
                <Scan className="absolute left-3 top-2.5 h-4 w-4 text-brand-secondary" aria-hidden />
                <input
                  {...scanForm.register('barcode')}
                  {...a11yProps('barcode', scanForm.formState.errors.barcode?.message)}
                  aria-label="Specimen barcode"
                  autoComplete="off"
                  placeholder="Scan barcode (e.g. ACC-2026-5001-S01)"
                  className={inputClass(!!scanForm.formState.errors.barcode, 'w-full pl-9 font-mono font-bold tracking-wider sm:w-72')}
                />
              </div>
              <button
                type="submit"
                disabled={scanForm.formState.isSubmitting}
                className="uiverse-btn-primary flex items-center justify-center space-x-1.5 whitespace-nowrap rounded-lg px-4 py-2 text-xs font-bold text-white disabled:opacity-60"
              >
                <Scan className="h-3.5 w-3.5" aria-hidden />
                <span>{scanForm.formState.isSubmitting ? 'Receiving…' : 'Scan receive'}</span>
              </button>
            </div>
            {scanForm.formState.errors.barcode && (
              <p id="barcode-error" className="text-[11px] text-brand-panic" role="alert">
                {scanForm.formState.errors.barcode.message}
              </p>
            )}
          </form>
        ) : (
          <p className="rounded-lg border border-brand-grid bg-slate-50 px-3 py-2 text-[11px] text-slate-500">
            Read-only view: receiving and rejecting specimens requires the technician role.
          </p>
        )}
      </div>

      <div className="uiverse-card overflow-hidden rounded-xl border border-brand-grid/80 bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-brand-grid bg-slate-50/70 p-3 sm:p-4">
          <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Filter by status">
            <span className="mr-1 flex items-center space-x-1 text-slate-500">
              <Filter className="h-3.5 w-3.5" aria-hidden />
              <span className="text-xs font-bold uppercase tracking-wider">Status</span>
            </span>
            {STATUS_FILTERS.map(filter => (
              <button
                key={filter.value || 'ALL'}
                type="button"
                onClick={() => changeFilter(filter.value)}
                aria-pressed={statusFilter === filter.value}
                className={`rounded-lg px-3 py-1 text-xs font-bold transition-all ${
                  statusFilter === filter.value ? 'bg-brand-primary text-white shadow-sm' : 'border border-brand-grid bg-white text-slate-600 hover:bg-slate-100'
                }`}
              >
                {filter.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={() => void fetchSamples()}
            className="flex items-center space-x-1.5 rounded-lg border border-brand-grid bg-white px-2.5 py-1 text-xs font-bold text-slate-600 hover:border-brand-primary/40 hover:text-brand-primary"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin text-brand-primary' : ''}`} aria-hidden />
            <span>Refresh</span>
          </button>
        </div>

        {loading && !page ? (
          <TableSkeleton rows={8} columns={7} />
        ) : samples.length === 0 ? (
          <div className="py-14 text-center text-xs text-slate-400">No specimens match the current filter.</div>
        ) : (
          <>
            {/* Cards below md */}
            <ul className={`divide-y divide-brand-grid/60 md:hidden ${loading ? 'opacity-60' : ''}`}>
              {samples.map(sample => (
                <li key={sample.id} className="space-y-2.5 p-4">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-sm font-bold text-slate-900">{sample.barcode}</span>
                    <StatusBadge status={sample.status} />
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div>
                      <span className="block text-[10px] font-bold uppercase text-slate-400">Accession #</span>
                      <span className="font-mono font-semibold text-slate-700">{sample.accessionNumber}</span>
                    </div>
                    <div>
                      <span className="block text-[10px] font-bold uppercase text-slate-400">Specimen</span>
                      <span className="font-medium text-slate-800">{sample.specimenType}</span>
                    </div>
                  </div>
                  <TubeBadge containerType={sample.containerType} />
                  {sample.status === 'REJECTED' && <RejectionNote sample={sample} />}
                  <div className="flex items-center justify-between border-t border-brand-grid/40 pt-2">
                    <span className="font-mono text-[11px] text-slate-400">
                      {sample.receivedAt ? `Received ${formatTime(sample.receivedAt)}` : 'Not yet received'}
                    </span>
                    {canAccession && (
                      <SampleActions sample={sample} busy={busyBarcode === sample.barcode} onReceive={receive} onReject={setRejecting} />
                    )}
                  </div>
                </li>
              ))}
            </ul>

            {/* Dense worklist table from md */}
            <div className={`hidden overflow-x-auto md:block ${loading ? 'opacity-60' : ''}`}>
              <table className="table-dense sticky-header w-full text-left">
                <thead>
                  <tr>
                    <th scope="col">Barcode</th>
                    <th scope="col">Accession #</th>
                    <th scope="col">Container</th>
                    <th scope="col">Specimen</th>
                    <th scope="col">Status</th>
                    <th scope="col">Rejection</th>
                    <th scope="col">Received</th>
                    {canAccession && (
                      <th scope="col" className="text-right">
                        Actions
                      </th>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {samples.map(sample => (
                    <tr key={sample.id} className="hover:bg-slate-50/80">
                      <td className="font-mono font-bold text-slate-900">{sample.barcode}</td>
                      <td className="font-mono font-semibold text-slate-600">{sample.accessionNumber}</td>
                      <td>
                        <TubeBadge containerType={sample.containerType} />
                      </td>
                      <td className="font-medium text-slate-700">{sample.specimenType}</td>
                      <td>
                        <StatusBadge status={sample.status} />
                      </td>
                      <td>{sample.status === 'REJECTED' ? <RejectionNote sample={sample} compact /> : <span className="text-[11px] text-slate-400">—</span>}</td>
                      <td className="font-mono text-[11px] text-slate-500">{formatTime(sample.receivedAt)}</td>
                      {canAccession && (
                        <td className="whitespace-nowrap text-right">
                          <SampleActions sample={sample} busy={busyBarcode === sample.barcode} onReceive={receive} onReject={setRejecting} />
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        {page && <Pagination page={page} onPageChange={setPageIndex} disabled={loading} noun="specimens" />}
      </div>

      <RejectDialog
        sample={rejecting}
        onClose={() => setRejecting(null)}
        onRejected={async sample => {
          setRejecting(null);
          toast.warning(`Sample ${sample.barcode} rejected`, `${humanize(sample.rejectionReason ?? '')}: recollection required`);
          await fetchSamples();
        }}
      />
    </div>
  );
};

const SampleActions: React.FC<{
  sample: SpecimenSample;
  busy: boolean;
  onReceive: (barcode: string) => Promise<boolean>;
  onReject: (sample: SpecimenSample) => void;
}> = ({ sample, busy, onReceive, onReject }) => {
  if (sample.status === 'COLLECTED') {
    return (
      <span className="inline-flex items-center gap-1.5">
        <button
          type="button"
          disabled={busy}
          onClick={() => void onReceive(sample.barcode)}
          className="uiverse-btn-emerald rounded-md px-3 py-1 text-xs font-bold text-white disabled:opacity-60"
        >
          {busy ? 'Receiving…' : 'Receive'}
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => onReject(sample)}
          className="rounded-md border border-red-300 px-2.5 py-1 text-xs font-bold text-brand-panic hover:bg-red-50 disabled:opacity-60"
        >
          Reject
        </button>
      </span>
    );
  }
  if (sample.status === 'RECEIVED') {
    return (
      <button type="button" onClick={() => onReject(sample)} className="px-2 py-0.5 text-[11px] font-medium text-slate-400 hover:text-brand-panic">
        Flag / reject
      </button>
    );
  }
  return null;
};

const RejectDialog: React.FC<{
  sample: SpecimenSample | null;
  onClose: () => void;
  onRejected: (sample: SpecimenSample) => void | Promise<void>;
}> = ({ sample, onClose, onRejected }) => {
  const toast = useToast();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<RejectionForm>({
    resolver: zodResolver(rejectionSchema),
    defaultValues: { rejectionReason: 'HEMOLYZED', rejectionNotes: '' },
  });

  useEffect(() => {
    if (sample) reset({ rejectionReason: 'HEMOLYZED', rejectionNotes: '' });
  }, [sample, reset]);

  const onSubmit = async (values: RejectionForm) => {
    if (!sample) return;
    try {
      const rejected = await api.rejectSample(sample.barcode, values.rejectionReason, values.rejectionNotes || undefined);
      await onRejected(rejected);
    } catch (error) {
      toast.apiError(error, 'Rejection failed');
    }
  };

  return (
    <Modal
      open={!!sample}
      onClose={onClose}
      busy={isSubmitting}
      tone="danger"
      title={
        <span className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 text-brand-panic" aria-hidden />
          Reject specimen {sample?.barcode}
        </span>
      }
      footer={
        <>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="rounded-lg border border-brand-grid px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            form="reject-form"
            disabled={isSubmitting}
            className="uiverse-btn-panic rounded-lg px-5 py-2 text-xs font-bold text-white shadow-sm disabled:opacity-60"
          >
            {isSubmitting ? 'Rejecting…' : 'Confirm rejection'}
          </button>
        </>
      }
    >
      <form id="reject-form" onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-3.5">
        <div className="space-y-1 rounded-lg border border-red-200 bg-red-50/80 p-3 text-xs text-red-900">
          <p className="flex items-center gap-1.5 font-bold">
            <ShieldAlert className="h-4 w-4 text-brand-panic" aria-hidden />
            Pre-analytical rejection
          </p>
          <p className="text-[11px] text-red-700">
            This is recorded in the immutable audit trail and the order is flagged for recollection. It cannot be undone.
          </p>
        </div>
        <Field label="Rejection reason" htmlFor="rejectionReason" required error={errors.rejectionReason?.message}>
          <select
            {...register('rejectionReason')}
            {...a11yProps('rejectionReason', errors.rejectionReason?.message)}
            data-autofocus
            className={inputClass(!!errors.rejectionReason)}
          >
            {REJECTION_REASONS.map(reason => (
              <option key={reason.value} value={reason.value}>
                {reason.label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Technician notes" htmlFor="rejectionNotes" error={errors.rejectionNotes?.message}>
          <textarea
            {...register('rejectionNotes')}
            {...a11yProps('rejectionNotes', errors.rejectionNotes?.message)}
            rows={3}
            maxLength={1000}
            placeholder="e.g. Gross hemolysis after centrifugation; recollection advised."
            className={inputClass(!!errors.rejectionNotes)}
          />
        </Field>
      </form>
    </Modal>
  );
};

const RejectionNote: React.FC<{ sample: SpecimenSample; compact?: boolean }> = ({ sample, compact }) => (
  <div className={compact ? 'text-[11px] font-bold text-brand-panic' : 'rounded-lg border border-red-200 bg-red-50 p-2.5 text-xs text-brand-panic'}>
    <span className="font-bold">{humanize(sample.rejectionReason ?? 'REJECTED')}</span>
    {sample.rejectionNotes && <p className="mt-0.5 text-[10px] font-normal italic text-slate-500">“{sample.rejectionNotes}”</p>}
  </div>
);

export const StatusBadge: React.FC<{ status: SampleStatus }> = ({ status }) => {
  if (status === 'RECEIVED') {
    return (
      <span className="inline-flex items-center space-x-1 rounded-full border border-emerald-300 bg-emerald-100 px-2.5 py-1 text-[11px] font-bold text-emerald-800">
        <CheckCircle2 className="h-3 w-3 text-emerald-600" aria-hidden />
        <span>Received</span>
      </span>
    );
  }
  if (status === 'REJECTED') {
    return (
      <span className="inline-flex items-center space-x-1 rounded-full border border-red-300 bg-red-100 px-2.5 py-1 text-[11px] font-bold text-brand-panic">
        <XCircle className="h-3 w-3" aria-hidden />
        <span>Rejected</span>
      </span>
    );
  }
  return (
    <span className="inline-flex items-center space-x-1 rounded-full border border-amber-300 bg-amber-100 px-2.5 py-1 text-[11px] font-bold text-amber-800">
      <Clock className="h-3 w-3 text-amber-600" aria-hidden />
      <span>Collected</span>
    </span>
  );
};

const TUBE_STYLES: { match: RegExp; label: string; badge: string; dot: string }[] = [
  { match: /edta|lavender/i, label: 'EDTA (Lavender)', badge: 'bg-purple-100 text-purple-900 border-purple-200', dot: 'bg-purple-600' },
  { match: /fluoride|grey|gray/i, label: 'Fluoride (Grey)', badge: 'bg-slate-200 text-slate-800 border-slate-300', dot: 'bg-slate-500' },
  { match: /citrate|blue/i, label: 'Citrate (Blue)', badge: 'bg-sky-100 text-sky-800 border-sky-200', dot: 'bg-sky-500' },
  { match: /heparin|green/i, label: 'Heparin (Green)', badge: 'bg-green-100 text-green-800 border-green-200', dot: 'bg-green-600' },
];

const TubeBadge: React.FC<{ containerType: string }> = ({ containerType }) => {
  const style = TUBE_STYLES.find(s => s.match.test(containerType)) ?? {
    label: containerType.match(/sst|plain|red/i) ? 'Plain / SST (Red)' : humanize(containerType),
    badge: 'bg-rose-100 text-rose-800 border-rose-200',
    dot: 'bg-rose-500',
  };
  return (
    <span className={`inline-flex items-center rounded border px-2 py-0.5 text-[11px] font-bold ${style.badge}`}>
      <span className={`mr-1.5 h-2 w-2 rounded-full ${style.dot}`} aria-hidden />
      {style.label}
    </span>
  );
};

export default AccessioningPage;
