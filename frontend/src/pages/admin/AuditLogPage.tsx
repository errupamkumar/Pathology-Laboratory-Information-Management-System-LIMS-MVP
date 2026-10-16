import React, { useCallback, useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, Filter, RefreshCw } from 'lucide-react';
import { Pagination } from '../../components/ui/Pagination';
import { TableSkeleton } from '../../components/ui/Skeleton';
import { useToast } from '../../components/ui/Toaster';
import { formatDateTime } from '../../lib/format';
import { api } from '../../services/api';
import type { AuditLogEntry, PageResponse } from '../../types';
import { AdminHeader } from './AdminTabs';

const PAGE_SIZE = 50;

/**
 * Simple class names written by AuditEntityListener for @Auditable entities, plus `AppUser` for
 * sign-in/lockout security events (entity id = username).
 */
const AUDITED_ENTITIES = [
  'AppUser',
  'Patient',
  'DiagnosticOrder',
  'OrderItem',
  'SpecimenSample',
  'TestResult',
  'OrderAuthorization',
  'TestCatalog',
  'TestParameter',
  'ParameterReferenceRange',
];

/** Keyed by com.lims.common.enums.AuditAction. */
const ACTION_STYLES: Record<string, string> = {
  INSERT: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  UPDATE: 'border-blue-200 bg-blue-50 text-blue-800',
  DELETE: 'border-red-200 bg-red-50 text-brand-panic',
  AUTHORIZE: 'border-purple-200 bg-purple-50 text-purple-800',
  REJECT: 'border-amber-200 bg-amber-50 text-amber-800',
  LOGIN_SUCCESS: 'border-slate-200 bg-slate-50 text-slate-700',
  LOGIN_FAILURE: 'border-amber-200 bg-amber-50 text-amber-800',
  ACCOUNT_LOCKED: 'border-red-200 bg-red-50 text-brand-panic',
};

function prettyJson(raw: string | null | undefined): string {
  if (!raw) return '—';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

const AuditLogPage: React.FC = () => {
  const toast = useToast();
  const [entityName, setEntityName] = useState('');
  const [entityId, setEntityId] = useState('');
  const [applied, setApplied] = useState<{ entityName?: string; entityId?: string }>({});
  const [pageIndex, setPageIndex] = useState(0);
  const [page, setPage] = useState<PageResponse<AuditLogEntry> | null>(null);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setPage(await api.getAuditLogs(applied, { page: pageIndex, size: PAGE_SIZE, sort: 'timestamp,desc' }));
    } catch (error) {
      toast.apiError(error, 'Could not load the audit trail');
    } finally {
      setLoading(false);
    }
  }, [applied, pageIndex, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const applyFilter = (event: React.FormEvent) => {
    event.preventDefault();
    setPageIndex(0);
    // The API filters by entity id only together with an entity name
    setApplied({ entityName: entityName.trim() || undefined, entityId: entityName.trim() ? entityId.trim() || undefined : undefined });
  };

  const entries = page?.content ?? [];

  return (
    <div className="mx-auto max-w-7xl space-y-4 px-4 py-4 sm:space-y-6 sm:py-6">
      <AdminHeader />

      <div className="overflow-hidden rounded-xl border border-brand-grid/80 bg-white shadow-sm">
        <form onSubmit={applyFilter} className="flex flex-wrap items-end gap-2 border-b border-brand-grid bg-slate-50/70 p-3 sm:p-4">
          <div>
            <label htmlFor="audit-entity" className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-500">
              Entity
            </label>
            <input
              id="audit-entity"
              list="audit-entities"
              value={entityName}
              onChange={e => setEntityName(e.target.value)}
              placeholder="Any entity"
              className="w-48 rounded-lg border border-brand-grid bg-white px-2.5 py-1.5 text-xs outline-none focus:border-brand-primary"
            />
            <datalist id="audit-entities">
              {AUDITED_ENTITIES.map(name => (
                <option key={name} value={name} />
              ))}
            </datalist>
          </div>
          <div>
            <label htmlFor="audit-entity-id" className="mb-1 block text-[10px] font-bold uppercase tracking-wider text-slate-500">
              Entity ID
            </label>
            <input
              id="audit-entity-id"
              value={entityId}
              onChange={e => setEntityId(e.target.value)}
              disabled={!entityName.trim()}
              placeholder={entityName.trim() ? (entityName.trim() === 'AppUser' ? 'Username' : 'UUID') : 'Choose an entity first'}
              className="w-72 rounded-lg border border-brand-grid bg-white px-2.5 py-1.5 font-mono text-xs outline-none focus:border-brand-primary disabled:bg-slate-100"
            />
          </div>
          <button type="submit" className="uiverse-btn-primary flex items-center gap-1.5 rounded-lg px-3.5 py-1.5 text-xs font-bold text-white">
            <Filter className="h-3.5 w-3.5" aria-hidden />
            Apply
          </button>
          <button
            type="button"
            onClick={() => void load()}
            className="ml-auto flex items-center gap-1.5 rounded-lg border border-brand-grid bg-white px-2.5 py-1.5 text-xs font-bold text-slate-600 hover:text-brand-primary"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} aria-hidden />
            Refresh
          </button>
        </form>

        {loading && !page ? (
          <TableSkeleton rows={10} columns={6} />
        ) : entries.length === 0 ? (
          <p className="py-14 text-center text-xs text-slate-400">No audit events match the filter.</p>
        ) : (
          <div className={`overflow-x-auto ${loading ? 'opacity-60' : ''}`}>
            <table className="table-dense w-full min-w-[900px] text-left">
              <thead>
                <tr>
                  <th scope="col" className="w-8" />
                  <th scope="col">Timestamp</th>
                  <th scope="col">Action</th>
                  <th scope="col">Entity</th>
                  <th scope="col">Entity ID</th>
                  <th scope="col">Changed by</th>
                  <th scope="col">IP address</th>
                </tr>
              </thead>
              <tbody>
                {entries.map(entry => {
                  const open = expanded === entry.id;
                  return (
                    <React.Fragment key={entry.id}>
                      <tr className="hover:bg-slate-50">
                        <td>
                          <button
                            type="button"
                            onClick={() => setExpanded(open ? null : entry.id)}
                            aria-expanded={open}
                            aria-label={open ? 'Hide snapshot' : 'Show snapshot'}
                            className="rounded p-0.5 text-slate-400 hover:text-brand-primary"
                          >
                            {open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
                          </button>
                        </td>
                        <td className="whitespace-nowrap font-mono text-[11px]">{formatDateTime(entry.timestamp)}</td>
                        <td>
                          <span className={`rounded-full border px-2 py-0.5 text-[10px] font-bold ${ACTION_STYLES[entry.actionType] ?? 'border-slate-200 bg-slate-50 text-slate-700'}`}>
                            {entry.actionType}
                          </span>
                        </td>
                        <td className="font-semibold">{entry.entityName}</td>
                        <td className="font-mono text-[11px] text-slate-500">{entry.entityId}</td>
                        <td className="font-mono text-[11px]">{entry.changedBy ?? 'system'}</td>
                        <td className="font-mono text-[11px] text-slate-500">{entry.ipAddress ?? '—'}</td>
                      </tr>
                      {open && (
                        <tr>
                          <td colSpan={7} className="bg-slate-50">
                            <pre className="max-h-72 overflow-auto rounded-lg bg-slate-900 p-3 font-mono text-[11px] text-emerald-300 select-text">{prettyJson(entry.newData)}</pre>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {page && <Pagination page={page} onPageChange={setPageIndex} disabled={loading} noun="events" />}
      </div>
    </div>
  );
};

export default AuditLogPage;
