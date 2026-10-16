import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  ArrowRight,
  Award,
  CheckCircle2,
  FileCheck,
  Lock,
  Save,
  ShieldAlert,
  TrendingDown,
  TrendingUp,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { OrderPicker } from '../components/OrderPicker';
import { a11yProps, Field, inputClass } from '../components/ui/Field';
import { Modal } from '../components/ui/Modal';
import { TableSkeleton } from '../components/ui/Skeleton';
import { useToast } from '../components/ui/Toaster';
import { useAltHotkey } from '../hooks/useHotkey';
import { useOrderWorksheet, Worksheet } from '../hooks/useOrderWorksheet';
import { errorMessage } from '../lib/apiError';
import { evaluateObservedValue, formatReferenceInterval, ResultRow } from '../lib/clinical';
import { formatDateTime, humanize } from '../lib/format';
import { RESULT_NOTES_MAX, RESULT_VALUE_MAX, SignOffForm, signOffSchema } from '../schemas/clinical';
import { api } from '../services/api';
import type { DiagnosticOrder, ResultInput } from '../types';

type Inputs = Record<string, { value: string; notes: string }>;

function initialInputs(worksheet: Worksheet): Inputs {
  const inputs: Inputs = {};
  for (const section of worksheet.sections) {
    for (const row of section.rows) {
      inputs[row.parameterId] = { value: row.saved?.observedValue ?? '', notes: row.saved?.technicianNotes ?? '' };
    }
  }
  return inputs;
}

function isRowDirty(row: ResultRow, input: { value: string; notes: string } | undefined): boolean {
  if (!input) return false;
  return input.value.trim() !== (row.saved?.observedValue ?? '') || input.notes.trim() !== (row.saved?.technicianNotes ?? '');
}

const ResultEntryPage: React.FC = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { can } = useAuth();
  const { worksheet, loading, error, reload } = useOrderWorksheet(orderId);

  const [inputs, setInputs] = useState<Inputs>({});
  const [saving, setSaving] = useState(false);
  const [noOrders, setNoOrders] = useState(false);
  const valueRefs = useRef<Record<string, HTMLInputElement | null>>({});

  useEffect(() => {
    if (worksheet) setInputs(initialInputs(worksheet));
  }, [worksheet]);

  const header = worksheet?.header;
  const isAuthorized = header?.status === 'AUTHORIZED' || !!header?.authorization;
  const canEdit = can('enterResults') && !isAuthorized;
  const rows = useMemo(() => worksheet?.sections.flatMap(s => s.rows) ?? [], [worksheet]);
  const dirtyRows = rows.filter(row => isRowDirty(row, inputs[row.parameterId]));
  const savedCount = rows.filter(row => row.saved).length;
  const criticalCount = rows.filter(row => row.saved?.isCritical).length;
  const reviewCount = rows.filter(row => row.saved?.requiresReview).length;

  const selectOrder = (id: string) => navigate(`/results/${id}`);
  const onRecentLoaded = useCallback(
    (orders: DiagnosticOrder[]) => {
      if (orderId) return;
      if (orders.length > 0) navigate(`/results/${orders[0].id}`, { replace: true });
      else setNoOrders(true);
    },
    [orderId, navigate],
  );

  const setInput = (parameterId: string, patch: Partial<{ value: string; notes: string }>) =>
    setInputs(current => {
      const existing: { value: string; notes: string } | undefined = current[parameterId];
      return { ...current, [parameterId]: { ...(existing ?? { value: '', notes: '' }), ...patch } };
    });

  const save = async () => {
    if (!worksheet || !canEdit || saving) return;
    const clearedSaved = dirtyRows.filter(row => row.saved && !inputs[row.parameterId]?.value.trim());
    if (clearedSaved.length > 0) {
      toast.warning('A saved result cannot be blank', `Enter a corrected value for ${clearedSaved.map(r => r.parameterName).join(', ')}.`);
      return;
    }
    const payload: ResultInput[] = dirtyRows
      .filter(row => inputs[row.parameterId]?.value.trim())
      .map(row => ({
        parameterId: row.parameterId,
        parameterName: row.parameterName,
        unit: row.unit ?? null,
        observedValue: inputs[row.parameterId].value.trim(),
        technicianNotes: inputs[row.parameterId].notes.trim() || null,
      }));
    if (payload.length === 0) {
      toast.info('Nothing to save', 'Enter or change at least one observed value.');
      return;
    }
    setSaving(true);
    try {
      const saved = await api.saveBatchResults(worksheet.header.orderId, payload);
      const critical = saved.filter(r => r.isCritical).length;
      if (critical > 0) {
        toast.warning(`${critical} critical value${critical > 1 ? 's' : ''} recorded`, 'Notify the referring clinician per the critical-value policy.');
      } else {
        toast.success(`${saved.length} result${saved.length > 1 ? 's' : ''} saved and evaluated`);
      }
      await reload({ silent: true });
    } catch (err) {
      toast.apiError(err, 'Could not save results');
    } finally {
      setSaving(false);
    }
  };

  useAltHotkey('s', () => void save(), canEdit);

  const focusNext = (index: number) => {
    const next = rows[index + 1];
    if (next) valueRefs.current[next.parameterId]?.focus();
    else void save();
  };

  return (
    <div className="mx-auto max-w-7xl space-y-4 px-4 py-4 sm:space-y-6 sm:py-6">
      <div className="uiverse-card flex flex-col justify-between gap-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 md:flex-row md:items-center">
        <div className="flex items-center space-x-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary/10">
            <Activity className="h-5 w-5 text-brand-primary" aria-hidden />
          </div>
          <div>
            <h1 className="text-base font-bold tracking-tight text-slate-900 sm:text-lg">Result Entry & Delta Check</h1>
            <p className="text-xs text-slate-500">Live reference-interval and panic flags; the server re-evaluates and runs ±25% delta checks on save.</p>
          </div>
        </div>
        <OrderPicker
          selectedId={orderId}
          selectedLabel={header ? `${header.accessionNumber} · ${header.patientName}` : undefined}
          onSelect={selectOrder}
          onRecentLoaded={onRecentLoaded}
        />
      </div>

      {!orderId && noOrders && <EmptyState message="No diagnostic orders have been booked yet." />}

      {error !== undefined && !worksheet && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center text-xs text-red-900" role="alert">
          {errorMessage(error, 'Could not load this order.')}
          <button type="button" onClick={() => void reload()} className="ml-2 font-bold underline">
            Retry
          </button>
        </div>
      )}

      {orderId && loading && !worksheet && (
        <div className="rounded-xl border border-brand-grid/80 bg-white">
          <TableSkeleton rows={10} columns={6} />
        </div>
      )}

      {worksheet && header && (
        <>
          <PatientBanner worksheet={worksheet} onViewReport={() => navigate(`/reports/${header.orderId}`)} />

          {isAuthorized && (
            <div className="flex items-center gap-2 rounded-lg border border-emerald-300 bg-emerald-50 p-3 text-xs text-emerald-900" role="status">
              <Lock className="h-4 w-4 shrink-0 text-emerald-700" aria-hidden />
              This report has been signed off. Results are locked; corrections require an amended report.
            </div>
          )}
          {criticalCount > 0 && (
            <div className="flex items-center gap-2 rounded-lg border border-red-300 bg-red-50 p-3 text-xs font-semibold text-red-900" role="alert">
              <ShieldAlert className="h-4 w-4 shrink-0 text-brand-panic" aria-hidden />
              {criticalCount} critical (panic) value{criticalCount > 1 ? 's' : ''} on this order. Notify the referring clinician and document the call.
            </div>
          )}

          <div className="uiverse-card overflow-hidden rounded-xl border border-brand-grid/80 bg-white shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-brand-grid bg-slate-50/70 p-3 sm:p-4">
              <h2 className="flex items-center space-x-2 text-xs font-bold uppercase tracking-wide text-slate-800">
                <span>Parameters</span>
                <span className="rounded-full bg-brand-primary/10 px-2 py-0.5 font-mono text-[10px] font-bold text-brand-primary">
                  {savedCount}/{rows.length} resulted
                </span>
                {reviewCount > 0 && (
                  <span className="rounded-full border border-red-200 bg-red-50 px-2 py-0.5 text-[10px] font-bold text-brand-panic">{reviewCount} delta review</span>
                )}
              </h2>
              {canEdit && (
                <div className="flex items-center space-x-2">
                  {dirtyRows.length > 0 && (
                    <span className="rounded-lg border border-amber-200 bg-amber-50 px-2.5 py-1 text-[11px] font-bold text-amber-800">
                      {dirtyRows.length} unsaved change{dirtyRows.length > 1 ? 's' : ''}
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => void save()}
                    disabled={saving || dirtyRows.length === 0}
                    className="uiverse-btn-primary flex items-center space-x-1.5 rounded-lg px-4 py-2 text-xs font-bold text-white shadow-sm disabled:opacity-50"
                  >
                    <Save className="h-3.5 w-3.5" aria-hidden />
                    <span>{saving ? 'Evaluating…' : 'Save & evaluate (Alt+S)'}</span>
                  </button>
                </div>
              )}
            </div>

            {rows.length === 0 ? (
              <EmptyState message="The ordered tests have no configured parameters." />
            ) : (
              <div className="overflow-x-auto">
                <table className="table-dense sticky-header w-full min-w-[860px] text-left">
                  <thead>
                    <tr>
                      <th scope="col" className="w-8">#</th>
                      <th scope="col">Parameter</th>
                      <th scope="col" className="w-36">Observed value</th>
                      <th scope="col" className="w-20">Unit</th>
                      <th scope="col" className="w-40">Reference interval</th>
                      <th scope="col" className="w-32">Flag</th>
                      <th scope="col" className="w-32">Delta (±25%)</th>
                      <th scope="col">Technician notes</th>
                    </tr>
                  </thead>
                  <tbody>
                    {worksheet.sections.map(section => (
                      <React.Fragment key={section.testCode}>
                        <tr className="bg-brand-primary/5">
                          <td colSpan={8} className="py-1.5 text-[11px] font-bold uppercase tracking-wider text-brand-primary">
                            {section.testName} <span className="font-mono text-slate-400">({section.testCode})</span>
                          </td>
                        </tr>
                        {section.rows.map(row => {
                          const index = rows.indexOf(row);
                          return (
                            <ResultGridRow
                              key={row.parameterId}
                              index={index}
                              row={row}
                              input={inputs[row.parameterId]}
                              editable={canEdit}
                              onChange={patch => setInput(row.parameterId, patch)}
                              onEnter={() => focusNext(index)}
                              inputRef={el => (valueRefs.current[row.parameterId] = el)}
                            />
                          );
                        })}
                      </React.Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <SignOffPanel worksheet={worksheet} hasUnsavedChanges={dirtyRows.length > 0} savedCount={savedCount} criticalCount={criticalCount} onSigned={() => reload({ silent: true })} />
        </>
      )}
    </div>
  );
};

const ResultGridRow: React.FC<{
  index: number;
  row: ResultRow;
  input?: { value: string; notes: string };
  editable: boolean;
  onChange: (patch: Partial<{ value: string; notes: string }>) => void;
  onEnter: () => void;
  inputRef: (el: HTMLInputElement | null) => void;
}> = ({ index, row, input, editable, onChange, onEnter, inputRef }) => {
  const value = input?.value ?? '';
  const dirty = isRowDirty(row, input);
  // Saved & unchanged: trust the server flags. Edited: show the live client-side evaluation.
  const live = evaluateObservedValue(row.range, value);
  const isCritical = dirty ? live.isCritical : !!row.saved?.isCritical;
  const isOutOfRange = dirty ? live.isOutOfRange : !!row.saved?.isOutOfRange;
  const delta = dirty ? null : row.saved?.deltaChangePercentage;
  const requiresReview = !dirty && !!row.saved?.requiresReview;
  const fieldId = `result-${row.parameterId}`;

  return (
    <tr className={isCritical ? 'border-l-4 border-l-brand-panic bg-red-50/70' : isOutOfRange ? 'bg-amber-50/40' : 'hover:bg-slate-50'}>
      <td className="font-mono text-[10px] text-slate-400">{index + 1}</td>
      <td className="font-bold text-slate-900">
        <label htmlFor={fieldId}>{row.parameterName}</label>
      </td>
      <td>
        <input
          id={fieldId}
          ref={inputRef}
          value={value}
          readOnly={!editable}
          maxLength={RESULT_VALUE_MAX}
          autoComplete="off"
          placeholder={editable ? 'Enter value' : '—'}
          onChange={e => onChange({ value: e.target.value })}
          onKeyDown={e => {
            if (e.key === 'Enter') {
              e.preventDefault();
              onEnter();
            }
          }}
          aria-invalid={isCritical || isOutOfRange || undefined}
          className={`w-full rounded-lg border px-2.5 py-1.5 font-mono text-xs font-bold outline-none transition-all read-only:bg-slate-50 ${
            isCritical
              ? 'border-brand-panic bg-red-50 text-brand-panic ring-2 ring-brand-panic/30'
              : isOutOfRange
                ? 'border-amber-500 bg-amber-50 text-amber-800'
                : 'border-brand-grid text-slate-900 focus:border-brand-primary focus:ring-1 focus:ring-brand-primary'
          }`}
        />
      </td>
      <td className="font-mono text-[11px] text-slate-500">{row.unit || '—'}</td>
      <td className="font-mono text-[11px] text-slate-600">
        {formatReferenceInterval(row.range)}
        {row.range?.criticalLow != null && row.range?.criticalHigh != null && (
          <span className="block font-sans text-[9px] font-semibold text-red-500">
            Panic: &lt;{row.range.criticalLow} or &gt;{row.range.criticalHigh}
          </span>
        )}
      </td>
      <td>
        <FlagBadge value={value} isCritical={isCritical} isOutOfRange={isOutOfRange} pendingSave={dirty && !!value.trim()} />
      </td>
      <td>
        {delta != null ? (
          <span className="flex items-center space-x-1.5">
            {Number(delta) >= 0 ? (
              <TrendingUp className={`h-3.5 w-3.5 ${requiresReview ? 'text-brand-panic' : 'text-slate-500'}`} aria-hidden />
            ) : (
              <TrendingDown className={`h-3.5 w-3.5 ${requiresReview ? 'text-brand-panic' : 'text-slate-500'}`} aria-hidden />
            )}
            <span className={`font-mono text-[11px] font-bold ${requiresReview ? 'text-brand-panic' : 'text-slate-700'}`}>
              {Number(delta) > 0 ? '+' : ''}
              {Number(delta).toFixed(1)}%
            </span>
            {requiresReview && <span className="rounded border border-red-200 bg-red-100 px-1.5 text-[9px] font-bold text-brand-panic">REVIEW</span>}
          </span>
        ) : (
          <span className="text-[10px] text-slate-400">{dirty ? 'On save' : row.saved ? 'No baseline' : '—'}</span>
        )}
      </td>
      <td>
        <input
          value={input?.notes ?? ''}
          readOnly={!editable}
          maxLength={RESULT_NOTES_MAX}
          aria-label={`Notes for ${row.parameterName}`}
          placeholder={editable ? 'Optional notes…' : ''}
          onChange={e => onChange({ notes: e.target.value })}
          className="w-full rounded-lg border border-brand-grid/60 px-2.5 py-1 text-xs text-slate-700 outline-none read-only:bg-slate-50 focus:border-brand-secondary"
        />
      </td>
    </tr>
  );
};

const FlagBadge: React.FC<{ value: string; isCritical: boolean; isOutOfRange: boolean; pendingSave: boolean }> = ({
  value,
  isCritical,
  isOutOfRange,
  pendingSave,
}) => {
  if (!value.trim()) return <span className="text-[10px] text-slate-400">Pending</span>;
  const suffix = pendingSave ? <span className="ml-1 font-normal opacity-70">(unsaved)</span> : null;
  if (isCritical) {
    return (
      <span className="panic-highlight inline-flex items-center space-x-1 rounded-full border border-red-300 bg-red-100 px-2.5 py-0.5 text-[10px] font-bold text-brand-panic">
        <ShieldAlert className="h-3 w-3" aria-hidden />
        <span>PANIC{suffix}</span>
      </span>
    );
  }
  if (isOutOfRange) {
    return (
      <span className="inline-flex items-center space-x-1 rounded-full border border-amber-300 bg-amber-100 px-2.5 py-0.5 text-[10px] font-bold text-amber-800">
        <AlertTriangle className="h-3 w-3 text-amber-600" aria-hidden />
        <span>ABNORMAL{suffix}</span>
      </span>
    );
  }
  return (
    <span className="inline-flex items-center space-x-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-[10px] font-bold text-emerald-700">
      <CheckCircle2 className="h-3 w-3 text-emerald-600" aria-hidden />
      <span>NORMAL{suffix}</span>
    </span>
  );
};

const PatientBanner: React.FC<{ worksheet: Worksheet; onViewReport: () => void }> = ({ worksheet, onViewReport }) => {
  const { header } = worksheet;
  return (
    <div className="grid grid-cols-2 gap-3 rounded-xl border border-brand-grid/80 bg-white p-4 text-xs shadow-sm sm:grid-cols-3 sm:gap-4 sm:p-5 md:grid-cols-6">
      <Info label="Patient" value={header.patientName} strong />
      <Info label="UHID / MRN" value={header.patientUhid} mono />
      <Info label="Age / gender" value={`${header.patientAge} y / ${humanize(header.patientGender)}`} />
      <Info label="Accession no." value={header.accessionNumber} mono accent />
      <div>
        <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Order status</span>
        <OrderStatusBadge status={header.status} />
      </div>
      <div className="col-span-2 flex items-center justify-start sm:col-span-1 md:justify-end">
        <button
          type="button"
          onClick={onViewReport}
          className="uiverse-btn-outline flex w-full items-center justify-center space-x-1.5 rounded-lg px-3 py-1.5 text-xs font-bold md:w-auto"
        >
          <span>View report</span>
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </button>
      </div>
    </div>
  );
};

export const OrderStatusBadge: React.FC<{ status: string }> = ({ status }) => (
  <span
    className={`mt-1 inline-block rounded-full border px-2.5 py-0.5 text-[10px] font-bold ${
      status === 'AUTHORIZED'
        ? 'border-emerald-300 bg-emerald-100 text-emerald-800'
        : status === 'COMPLETED'
          ? 'border-blue-200 bg-blue-100 text-blue-800'
          : 'border-amber-200 bg-amber-100 text-amber-800'
    }`}
  >
    {status}
  </span>
);

const Info: React.FC<{ label: string; value: string; mono?: boolean; strong?: boolean; accent?: boolean }> = ({ label, value, mono, strong, accent }) => (
  <div>
    <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">{label}</span>
    <span
      className={`mt-0.5 block ${mono ? 'font-mono' : ''} ${strong ? 'text-sm font-bold text-slate-900' : 'font-semibold text-slate-800'} ${
        accent ? 'text-brand-primary' : ''
      }`}
    >
      {value}
    </span>
  </div>
);

const EmptyState: React.FC<{ message: string }> = ({ message }) => (
  <div className="rounded-xl border border-dashed border-brand-grid bg-white p-10 text-center text-xs text-slate-400">{message}</div>
);

const SignOffPanel: React.FC<{
  worksheet: Worksheet;
  hasUnsavedChanges: boolean;
  savedCount: number;
  criticalCount: number;
  onSigned: () => void | Promise<void>;
}> = ({ worksheet, hasUnsavedChanges, savedCount, criticalCount, onSigned }) => {
  const { user, can } = useAuth();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const { header } = worksheet;
  const authorization = header.authorization;

  const form = useForm<SignOffForm>({
    resolver: zodResolver(signOffSchema),
    defaultValues: { pathologistName: user?.fullName ?? '', licenseNumber: '', attest: false },
  });

  const submit = async (values: SignOffForm) => {
    try {
      const result = await api.authorizeOrder(header.orderId, values.pathologistName, values.licenseNumber);
      setConfirming(false);
      toast.success(`Report ${header.accessionNumber} authorized`, `Verification code ${result.verificationToken}; PDF spooled to the laser printer.`);
      await onSigned();
    } catch (error) {
      toast.apiError(error, 'Sign-off failed');
    }
  };

  return (
    <section className="space-y-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-6" aria-labelledby="signoff-heading">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-brand-grid pb-3">
        <h2 id="signoff-heading" className="flex items-center space-x-2 text-sm font-bold tracking-tight text-slate-900 sm:text-base">
          <FileCheck className="h-4 w-4 text-emerald-700" aria-hidden />
          <span>Pathologist Verification & Digital Sign-Off</span>
        </h2>
        {authorization && (
          <span className="inline-flex items-center space-x-1.5 rounded-full border border-emerald-300 bg-emerald-100 px-3 py-1 text-xs font-bold text-emerald-800">
            <Award className="h-4 w-4 text-emerald-600" aria-hidden />
            <span>Authorized</span>
          </span>
        )}
      </div>

      {authorization ? (
        <div className="grid grid-cols-1 gap-4 rounded-xl border border-brand-grid bg-slate-50/80 p-4 text-xs sm:p-5 md:grid-cols-3">
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Authorized by</span>
            <span className="mt-0.5 block text-sm font-bold text-slate-900">{authorization.pathologistName}</span>
            <span className="mt-0.5 block text-[11px] text-slate-500">
              Reg. {authorization.licenseNumber} · {formatDateTime(authorization.authorizedAt)}
            </span>
            {authorization.authorizedBy && <span className="block text-[10px] text-slate-400">Account: {authorization.authorizedBy}</span>}
          </div>
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">Verification code</span>
            <span className="mt-0.5 block font-mono text-sm font-bold text-brand-primary">{authorization.verificationToken}</span>
            <a href={`/verify/${authorization.verificationToken}`} target="_blank" rel="noreferrer" className="text-[10px] font-semibold text-brand-secondary underline">
              Open public verification page
            </a>
          </div>
          <div>
            <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">SHA-256 signature</span>
            <span className="mt-1 block break-all rounded-lg border border-brand-grid/60 bg-white p-2 font-mono text-[10px] text-slate-600 select-text">
              {authorization.digitalSignatureHash}
            </span>
          </div>
        </div>
      ) : !can('authorize') ? (
        <p className="text-xs text-slate-500">Awaiting pathologist sign-off. Only users with the pathologist role can authorize reports.</p>
      ) : (
        <form
          onSubmit={async event => {
            event.preventDefault();
            // Step 1 checks the signatory details; the attestation is confirmed in the dialog
            if (await form.trigger(['pathologistName', 'licenseNumber'])) {
              form.setValue('attest', false);
              form.clearErrors('attest');
              setConfirming(true);
            }
          }}
          noValidate
          className="grid grid-cols-1 items-start gap-4 md:grid-cols-12"
        >
          <Field className="md:col-span-5" label="Pathologist name" htmlFor="pathologistName" required error={form.formState.errors.pathologistName?.message}>
            <input
              {...form.register('pathologistName')}
              {...a11yProps('pathologistName', form.formState.errors.pathologistName?.message)}
              className={inputClass(!!form.formState.errors.pathologistName, 'font-bold')}
            />
          </Field>
          <Field className="md:col-span-4" label="Medical council reg. no." htmlFor="licenseNumber" required error={form.formState.errors.licenseNumber?.message}>
            <input
              {...form.register('licenseNumber')}
              {...a11yProps('licenseNumber', form.formState.errors.licenseNumber?.message)}
              placeholder="e.g. MCI-64921-PATH"
              className={inputClass(!!form.formState.errors.licenseNumber, 'font-mono font-semibold')}
            />
          </Field>
          <div className="md:col-span-3 md:pt-5">
            <button
              type="submit"
              disabled={savedCount === 0 || hasUnsavedChanges}
              className="uiverse-btn-emerald flex w-full items-center justify-center space-x-1.5 rounded-lg py-2.5 text-xs font-bold text-white shadow-sm disabled:opacity-50"
            >
              <Award className="h-4 w-4" aria-hidden />
              <span>Review & authorize</span>
            </button>
          </div>
          {(savedCount === 0 || hasUnsavedChanges) && (
            <p className="text-[11px] text-amber-700 md:col-span-12">
              {savedCount === 0 ? 'Save at least one result before sign-off.' : 'Save your result changes before signing off.'}
            </p>
          )}

          <Modal
            open={confirming}
            onClose={() => setConfirming(false)}
            busy={form.formState.isSubmitting}
            title={`Authorize report ${header.accessionNumber}?`}
            footer={
              <>
                <button
                  type="button"
                  onClick={() => setConfirming(false)}
                  disabled={form.formState.isSubmitting}
                  className="rounded-lg border border-brand-grid px-4 py-2 text-xs font-bold text-slate-600 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={form.handleSubmit(submit)}
                  disabled={form.formState.isSubmitting}
                  className="uiverse-btn-emerald rounded-lg px-5 py-2 text-xs font-bold text-white disabled:opacity-60"
                >
                  {form.formState.isSubmitting ? 'Signing…' : 'Sign & release report'}
                </button>
              </>
            }
          >
            <div className="space-y-3 text-xs text-slate-700">
              <p>
                Signing binds <strong>{form.getValues('pathologistName')}</strong> (reg. {form.getValues('licenseNumber')}) and your account to{' '}
                {savedCount} result{savedCount > 1 ? 's' : ''} for <strong>{header.patientName}</strong>. Results are then locked and the report is released.
              </p>
              {criticalCount > 0 && (
                <p className="rounded-lg border border-red-200 bg-red-50 p-2.5 font-semibold text-red-900">
                  This order has {criticalCount} critical value{criticalCount > 1 ? 's' : ''}. Confirm the clinician has been notified.
                </p>
              )}
              <label className="flex items-start gap-2">
                <input type="checkbox" {...form.register('attest')} data-autofocus className="mt-0.5" />
                <span>I have reviewed every result, including flagged and delta-check values, and they are fit for release.</span>
              </label>
              {form.formState.errors.attest && (
                <p className="text-[11px] text-brand-panic" role="alert">
                  {form.formState.errors.attest.message}
                </p>
              )}
            </div>
          </Modal>
        </form>
      )}
    </section>
  );
};

export default ResultEntryPage;
