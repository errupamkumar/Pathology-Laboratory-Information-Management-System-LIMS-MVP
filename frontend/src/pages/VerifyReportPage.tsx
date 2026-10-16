import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { BadgeCheck, FlaskConical, ShieldX } from 'lucide-react';
import { UiverseLoader } from '../components/ui/UiverseLoader';
import { ApiError } from '../lib/apiError';
import { formatDateTime } from '../lib/format';
import { api } from '../services/api';
import type { ReportVerification } from '../types';

type State =
  | { kind: 'loading' }
  | { kind: 'verified'; data: ReportVerification }
  | { kind: 'invalid'; message: string };

const TOKEN_PATTERN = /^VERIFY-[A-Za-z0-9]{4,32}$/;

/**
 * Public landing page of the report QR code. The endpoint returns signature details only,
 * never patient data, so it is safe without a session.
 */
const VerifyReportPage: React.FC = () => {
  const { token = '' } = useParams();
  const [state, setState] = useState<State>({ kind: 'loading' });

  useEffect(() => {
    if (!TOKEN_PATTERN.test(token)) {
      setState({ kind: 'invalid', message: 'This verification code is not in a recognised format.' });
      return;
    }
    let cancelled = false;
    setState({ kind: 'loading' });
    api
      .verifyReport(token)
      .then(data => {
        if (cancelled) return;
        setState(data.valid ? { kind: 'verified', data } : { kind: 'invalid', message: 'No signed report matches this code.' });
      })
      .catch(error => {
        if (cancelled) return;
        const notFound = error instanceof ApiError && (error.status === 404 || error.status === 400);
        setState({
          kind: 'invalid',
          message: notFound ? 'No signed report matches this code.' : 'Verification is temporarily unavailable. Please try again later.',
        });
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-brand-surface px-4 py-10">
      <div className="mb-5 flex items-center space-x-2">
        <FlaskConical className="h-6 w-6 text-brand-primary" aria-hidden />
        <span className="text-base font-extrabold uppercase tracking-tight text-brand-primary">ApexPath Report Verification</span>
      </div>

      <div className="w-full max-w-md rounded-xl border border-brand-grid bg-white p-6 shadow-sm">
        {state.kind === 'loading' && <UiverseLoader message="Checking digital signature…" />}

        {state.kind === 'invalid' && (
          <div className="text-center" role="alert">
            <ShieldX className="mx-auto h-10 w-10 text-brand-panic" aria-hidden />
            <h1 className="mt-2 text-base font-bold text-slate-900">Report could not be verified</h1>
            <p className="mt-1 text-xs text-slate-500">{state.message}</p>
            <p className="mt-3 font-mono text-[11px] text-slate-400 break-all">{token}</p>
          </div>
        )}

        {state.kind === 'verified' && (
          <div>
            <div className="text-center">
              <BadgeCheck className="mx-auto h-10 w-10 text-emerald-600" aria-hidden />
              <h1 className="mt-2 text-base font-bold text-slate-900">Authentic signed report</h1>
              <p className="mt-1 text-xs text-slate-500">
                This report was digitally signed by an authorised pathologist and has not been altered since.
              </p>
            </div>
            <dl className="mt-5 space-y-2.5 rounded-lg border border-brand-grid bg-slate-50 p-4 text-xs">
              <Row label="Signed by" value={state.data.pathologistName} />
              <Row label="Registration no." value={state.data.licenseNumber} mono />
              <Row label="Signed at" value={formatDateTime(state.data.authorizedAt)} />
              <Row label="Verification code" value={state.data.verificationToken} mono />
              <div>
                <dt className="text-[10px] font-bold uppercase tracking-wider text-slate-400">SHA-256 signature</dt>
                <dd className="mt-0.5 break-all font-mono text-[10px] text-slate-600 select-text">{state.data.digitalSignatureHash}</dd>
              </div>
            </dl>
          </div>
        )}
      </div>
      <p className="mt-4 text-[11px] text-slate-400">For clinical questions contact the issuing laboratory.</p>
    </div>
  );
};

const Row: React.FC<{ label: string; value: string; mono?: boolean }> = ({ label, value, mono }) => (
  <div className="flex items-center justify-between gap-3">
    <dt className="text-[10px] font-bold uppercase tracking-wider text-slate-400">{label}</dt>
    <dd className={`text-right font-semibold text-slate-800 ${mono ? 'font-mono' : ''}`}>{value}</dd>
  </div>
);

export default VerifyReportPage;
