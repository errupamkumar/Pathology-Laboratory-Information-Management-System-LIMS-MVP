import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Cpu, Download, ExternalLink, FileText, FlaskConical, Printer, QrCode, ZoomIn, ZoomOut } from 'lucide-react';
import { OrderPicker } from '../components/OrderPicker';
import { PageSkeleton } from '../components/ui/Skeleton';
import { useToast } from '../components/ui/Toaster';
import { useOrderWorksheet, Worksheet } from '../hooks/useOrderWorksheet';
import { errorMessage } from '../lib/apiError';
import { formatReferenceInterval } from '../lib/clinical';
import { formatDateTime, humanize } from '../lib/format';
import { api } from '../services/api';
import type { DiagnosticOrder, SpecimenSample } from '../types';

const ReportPage: React.FC = () => {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { worksheet, loading, error, reload } = useOrderWorksheet(orderId);
  const [samples, setSamples] = useState<SpecimenSample[]>([]);
  const [zoom, setZoom] = useState(100);
  const [pdfBusy, setPdfBusy] = useState(false);
  const [spooling, setSpooling] = useState(false);

  useEffect(() => {
    if (!orderId) return;
    let cancelled = false;
    api
      .getSamplesByOrder(orderId)
      .then(result => !cancelled && setSamples(result))
      .catch(() => !cancelled && setSamples([]));
    return () => {
      cancelled = true;
    };
  }, [orderId]);

  const onRecentLoaded = useCallback(
    (orders: DiagnosticOrder[]) => {
      if (!orderId && orders.length > 0) navigate(`/reports/${orders[0].id}`, { replace: true });
    },
    [orderId, navigate],
  );

  const header = worksheet?.header;
  const fileName = `lab-report-${header?.accessionNumber ?? orderId}.pdf`;

  const openPdf = async (mode: 'view' | 'download') => {
    if (!orderId) return;
    // Open the tab inside the click handler so popup blockers allow it, then point it at the blob
    const tab = mode === 'view' ? window.open('', '_blank') : null;
    setPdfBusy(true);
    try {
      const blob = await api.getReportPdf(orderId);
      const url = URL.createObjectURL(blob);
      if (tab) {
        tab.location.href = url;
      } else {
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
      }
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      tab?.close();
      toast.apiError(err, 'Could not generate the PDF report');
    } finally {
      setPdfBusy(false);
    }
  };

  const spool = async () => {
    if (!orderId) return;
    setSpooling(true);
    try {
      await api.spoolLaserReport(orderId);
      toast.success('Report sent to the laser printer', 'The connected print agent will print it silently.');
    } catch (err) {
      toast.apiError(err, 'Spooling failed');
    } finally {
      setSpooling(false);
    }
  };

  return (
    <div className="mx-auto max-w-7xl space-y-4 px-4 py-4 sm:space-y-6 sm:py-6">
      <div className="no-print uiverse-card flex flex-col justify-between gap-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 lg:flex-row lg:items-center">
        <div className="flex items-center space-x-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-primary/10">
            <FileText className="h-5 w-5 text-brand-primary" aria-hidden />
          </div>
          <div>
            <h1 className="text-base font-bold tracking-tight text-slate-900 sm:text-lg">Diagnostic Report</h1>
            <p className="text-xs text-slate-500">A4 preview. The signed PDF with its QR verification code is the official record.</p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <OrderPicker
            selectedId={orderId}
            selectedLabel={header ? `${header.accessionNumber} · ${header.patientName}` : undefined}
            onSelect={id => navigate(`/reports/${id}`)}
            onRecentLoaded={onRecentLoaded}
          />
          <div className="hidden items-center space-x-1 rounded-lg border border-brand-grid bg-white p-1 sm:flex">
            <button type="button" onClick={() => setZoom(z => Math.max(70, z - 10))} className="p-1 text-slate-500 hover:text-brand-primary" aria-label="Zoom out">
              <ZoomOut className="h-3.5 w-3.5" />
            </button>
            <span className="px-1 font-mono text-[11px] font-semibold">{zoom}%</span>
            <button type="button" onClick={() => setZoom(z => Math.min(130, z + 10))} className="p-1 text-slate-500 hover:text-brand-primary" aria-label="Zoom in">
              <ZoomIn className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>

      {worksheet && (
        <div className="no-print flex flex-wrap items-center justify-end gap-2">
          <button
            type="button"
            onClick={() => void openPdf('view')}
            disabled={pdfBusy}
            className="uiverse-btn-outline flex items-center space-x-1.5 rounded-lg px-3 py-2 text-xs font-bold disabled:opacity-60"
          >
            <ExternalLink className="h-3.5 w-3.5" aria-hidden />
            <span>{pdfBusy ? 'Generating…' : 'Open official PDF'}</span>
          </button>
          <button
            type="button"
            onClick={() => void openPdf('download')}
            disabled={pdfBusy}
            className="uiverse-btn-outline flex items-center space-x-1.5 rounded-lg px-3 py-2 text-xs font-bold disabled:opacity-60"
          >
            <Download className="h-3.5 w-3.5" aria-hidden />
            <span>Download</span>
          </button>
          <button
            type="button"
            onClick={() => void spool()}
            disabled={spooling}
            className="uiverse-btn-emerald flex items-center space-x-1.5 rounded-lg px-3.5 py-2 text-xs font-bold text-white shadow-sm disabled:opacity-60"
          >
            <Cpu className="h-3.5 w-3.5" aria-hidden />
            <span>{spooling ? 'Spooling…' : 'Spool to printer'}</span>
          </button>
          <button
            type="button"
            onClick={() => window.print()}
            className="uiverse-btn-primary flex items-center space-x-1.5 rounded-lg px-3.5 py-2 text-xs font-bold text-white shadow-sm"
          >
            <Printer className="h-3.5 w-3.5" aria-hidden />
            <span>Print preview</span>
          </button>
        </div>
      )}

      {error !== undefined && !worksheet && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center text-xs text-red-900" role="alert">
          {errorMessage(error, 'Could not load this report.')}
          <button type="button" onClick={() => void reload()} className="ml-2 font-bold underline">
            Retry
          </button>
        </div>
      )}

      {orderId && loading && !worksheet && <PageSkeleton />}

      {worksheet && (
        <div className="overflow-x-auto pb-6">
          <div style={{ transform: `scale(${zoom / 100})`, transformOrigin: 'top center' }} className="transition-transform duration-200">
            <ReportSheet worksheet={worksheet} samples={samples} />
          </div>
        </div>
      )}
    </div>
  );
};

const ReportSheet: React.FC<{ worksheet: Worksheet; samples: SpecimenSample[] }> = ({ worksheet, samples }) => {
  const { header, order } = worksheet;
  const authorization = header.authorization;
  const received = samples.filter(s => s.receivedAt).map(s => s.receivedAt!).sort();
  const verifyUrl = authorization ? `${window.location.origin}/verify/${authorization.verificationToken}` : null;

  return (
    <article className="print-sheet relative mx-auto flex min-h-[297mm] max-w-[210mm] flex-col justify-between border border-brand-grid bg-white p-6 font-sans text-slate-800 shadow-xl sm:p-10">
      {!authorization && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center overflow-hidden" aria-hidden>
          <span className="-rotate-[30deg] select-none text-[96px] font-black tracking-widest text-slate-200/70">DRAFT</span>
        </div>
      )}

      <div className="relative">
        <header className="mb-4 flex items-start justify-between border-b-2 border-brand-primary pb-4">
          <div className="flex items-center space-x-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-primary text-white shadow-md">
              <FlaskConical className="h-7 w-7" aria-hidden />
            </div>
            <div>
              <h2 className="text-xl font-black tracking-tight text-brand-primary">APEX CLINICAL REFERENCE LAB</h2>
              <p className="text-xs font-bold uppercase tracking-wide text-slate-600">ISO 15189:2022 Medical Laboratory</p>
              <p className="text-[11px] text-slate-500">100 Healthcare Blvd, Medical Enclave · Tel: +91 98765 43210</p>
            </div>
          </div>
          <span
            className={`inline-block rounded-lg border px-3 py-1 text-xs font-black uppercase tracking-wider ${
              authorization ? 'border-brand-primary/20 bg-brand-primary/10 text-brand-primary' : 'border-amber-300 bg-amber-50 text-amber-800'
            }`}
          >
            {authorization ? 'Final report' : 'Draft · not for clinical use'}
          </span>
        </header>

        <dl className="mb-5 grid grid-cols-2 gap-3 rounded-xl border border-brand-grid bg-slate-50/80 p-4 text-xs sm:grid-cols-4">
          <Meta label="Patient name" value={header.patientName} strong />
          <Meta label="UHID / MRN" value={header.patientUhid} mono />
          <Meta label="Age / gender" value={`${header.patientAge} Y / ${humanize(header.patientGender)}`} />
          <Meta label="Accession no." value={header.accessionNumber} mono />
          <Meta label="Referring doctor" value={header.referringDoctor || 'Self / walk-in'} />
          <Meta label="Registered" value={formatDateTime(order.createdAt)} />
          <Meta label="Sample received" value={received.length ? formatDateTime(received[0]) : 'Not received'} />
          <Meta label="Report released" value={authorization ? formatDateTime(authorization.authorizedAt) : 'Pending authorization'} />
        </dl>

        <table className="w-full border-collapse border border-brand-grid text-left text-xs">
          <thead>
            <tr className="bg-brand-primary text-white">
              <th scope="col" className="border-r border-white/20 px-3 py-2.5 font-bold">TEST PARAMETER</th>
              <th scope="col" className="w-32 border-r border-white/20 px-3 py-2.5 text-center font-bold">RESULT</th>
              <th scope="col" className="w-24 border-r border-white/20 px-3 py-2.5 text-center font-bold">UNIT</th>
              <th scope="col" className="w-44 border-r border-white/20 px-3 py-2.5 text-center font-bold">BIOLOGICAL REF. INTERVAL</th>
              <th scope="col" className="w-24 px-3 py-2.5 text-center font-bold">FLAG</th>
            </tr>
          </thead>
          <tbody>
            {worksheet.sections.map(section => (
              <React.Fragment key={section.testCode}>
                <tr className="bg-slate-100">
                  <td colSpan={5} className="px-3 py-1.5 text-[11px] font-bold uppercase tracking-wider text-brand-primary">
                    {section.testName}
                  </td>
                </tr>
                {section.rows.map(row => {
                  const result = row.saved;
                  const abnormal = !!(result?.isOutOfRange || result?.isCritical);
                  return (
                    <tr key={row.parameterId} className={`border-t border-brand-grid ${result?.isCritical ? 'bg-red-50/60' : ''}`}>
                      <td className="border-r border-brand-grid px-3 py-2 font-bold text-slate-900">
                        {row.parameterName}
                        {result?.technicianNotes && <span className="block text-[10px] font-normal italic text-slate-400">Note: {result.technicianNotes}</span>}
                      </td>
                      <td
                        className={`border-r border-brand-grid px-3 py-2 text-center font-mono text-sm ${
                          abnormal ? 'font-bold text-brand-panic' : 'font-semibold text-slate-900'
                        }`}
                      >
                        {result?.observedValue || <span className="text-[11px] font-normal italic text-slate-400">Pending</span>}
                      </td>
                      <td className="border-r border-brand-grid px-3 py-2 text-center font-mono text-[11px] text-slate-600">{row.unit || '—'}</td>
                      <td className="border-r border-brand-grid px-3 py-2 text-center font-mono text-[11px] text-slate-700">
                        {formatReferenceInterval(row.range)}
                      </td>
                      <td className="px-3 py-2 text-center text-[11px]">
                        {result?.isCritical ? (
                          <span className="font-black tracking-wide text-brand-panic">CRITICAL</span>
                        ) : result?.isOutOfRange ? (
                          <span className="font-bold text-brand-panic">ABNORMAL</span>
                        ) : result ? (
                          <span className="font-medium text-slate-600">Normal</span>
                        ) : null}
                      </td>
                    </tr>
                  );
                })}
              </React.Fragment>
            ))}
          </tbody>
        </table>
        <p className="mt-3 text-center text-[10px] font-medium text-slate-500">*** End of report ***</p>

        <div className="mt-5 space-y-1 rounded-xl border border-brand-grid/80 bg-slate-50/80 p-3.5 text-[10px] leading-relaxed text-slate-600">
          <p className="font-bold text-slate-800">Interpretation notes</p>
          <p>1. Results should be correlated clinically by the treating physician with the patient's history and other findings.</p>
          <p>2. Values outside the biological reference interval are shown in bold red; critical values were notified per laboratory policy.</p>
        </div>
      </div>

      <footer className="relative mt-8 flex flex-wrap items-end justify-between gap-4 border-t-2 border-brand-grid pt-4">
        <div className="flex items-center space-x-3">
          <div className="flex h-14 w-14 items-center justify-center rounded-xl border border-brand-grid bg-white">
            <QrCode className="h-9 w-9 text-brand-primary" aria-hidden />
          </div>
          <div className="text-[10px] text-slate-500">
            <span className="block text-xs font-bold text-slate-800">Report authenticity</span>
            {verifyUrl ? (
              <>
                <span>
                  Code: <strong className="font-mono text-brand-primary">{authorization!.verificationToken}</strong>
                </span>
                <span className="block break-all font-mono text-[9px] text-slate-400">{verifyUrl}</span>
              </>
            ) : (
              <span>Issued after pathologist sign-off</span>
            )}
          </div>
        </div>
        <div className="text-right">
          {authorization ? (
            <div className="inline-block rounded-xl border border-dashed border-emerald-400 bg-emerald-50/70 p-2.5 text-right">
              <span className="block text-[9px] font-bold uppercase tracking-wider text-emerald-800">✓ Digitally signed</span>
              <span className="mt-0.5 block text-xs font-bold text-slate-900">{authorization.pathologistName}</span>
              <span className="block text-[10px] text-slate-600">Consultant Pathologist</span>
              <span className="block font-mono text-[9px] text-slate-500">Reg. no. {authorization.licenseNumber}</span>
              <span className="mt-0.5 block max-w-[220px] truncate font-mono text-[8px] text-slate-400">SHA-256: {authorization.digitalSignatureHash}</span>
            </div>
          ) : (
            <div className="inline-block rounded-xl border border-dashed border-amber-300 bg-amber-50 p-2.5 text-[10px] font-semibold text-amber-800">
              Awaiting pathologist signature
            </div>
          )}
        </div>
      </footer>
    </article>
  );
};

const Meta: React.FC<{ label: string; value: string; mono?: boolean; strong?: boolean }> = ({ label, value, mono, strong }) => (
  <div>
    <dt className="text-[10px] font-bold uppercase tracking-wider text-slate-400">{label}</dt>
    <dd className={`mt-0.5 ${mono ? 'font-mono font-bold' : ''} ${strong ? 'text-sm font-bold text-slate-900' : 'text-slate-800'}`}>{value}</dd>
  </div>
);

export default ReportPage;
