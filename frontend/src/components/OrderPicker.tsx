import React, { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { useToast } from './ui/Toaster';
import { api } from '../services/api';
import type { DiagnosticOrder } from '../types';

interface OrderPickerProps {
  selectedId?: string;
  /** Label for the selected order when it is older than the recent-orders page. */
  selectedLabel?: string;
  onSelect: (orderId: string) => void;
  /** Called once with the most recent orders, e.g. to auto-open the newest one. */
  onRecentLoaded?: (orders: DiagnosticOrder[]) => void;
}

const RECENT_COUNT = 25;

/** Recent-orders dropdown plus accession-number lookup for anything older. */
export const OrderPicker: React.FC<OrderPickerProps> = ({ selectedId, selectedLabel, onSelect, onRecentLoaded }) => {
  const toast = useToast();
  const [recent, setRecent] = useState<DiagnosticOrder[]>([]);
  const [accession, setAccession] = useState('');
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api
      .getOrders({ page: 0, size: RECENT_COUNT, sort: 'createdAt,desc' })
      .then(page => {
        if (cancelled) return;
        setRecent(page.content);
        onRecentLoaded?.(page.content);
      })
      .catch(error => !cancelled && toast.apiError(error, 'Could not load recent orders'));
    return () => {
      cancelled = true;
    };
    // Load once per mount; the callbacks are not part of the data dependency
  }, []);

  const lookup = async (event: React.FormEvent) => {
    event.preventDefault();
    const value = accession.trim().toUpperCase();
    if (!/^[A-Z0-9-]{1,64}$/.test(value)) {
      toast.warning('Enter a valid accession number', 'For example ACC-2026-5001');
      return;
    }
    setSearching(true);
    try {
      const order = await api.getOrderByAccession(value);
      setAccession('');
      onSelect(order.id);
    } catch (error) {
      toast.apiError(error, `Order ${value} not found`);
    } finally {
      setSearching(false);
    }
  };

  const selectedInRecent = recent.some(o => o.id === selectedId);

  return (
    <div className="flex w-full flex-col gap-2 sm:flex-row sm:items-center md:w-auto">
      <label htmlFor="order-select" className="sr-only">
        Recent orders
      </label>
      <select
        id="order-select"
        value={selectedId ?? ''}
        onChange={e => e.target.value && onSelect(e.target.value)}
        className="w-full rounded-lg border border-brand-grid bg-slate-50/50 px-3 py-2 font-mono text-xs font-bold outline-none focus:border-brand-primary focus:ring-2 focus:ring-brand-primary/20 md:w-72"
      >
        {!selectedId && <option value="">Select a recent order…</option>}
        {selectedId && !selectedInRecent && <option value={selectedId}>{selectedLabel ?? 'Selected order'}</option>}
        {recent.map(order => (
          <option key={order.id} value={order.id}>
            {order.accessionNumber} · {order.patient.fullName} ({order.status})
          </option>
        ))}
      </select>
      <form onSubmit={lookup} className="relative flex">
        <Search className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" aria-hidden />
        <input
          value={accession}
          onChange={e => setAccession(e.target.value)}
          placeholder="Accession #"
          aria-label="Find order by accession number"
          className="w-full rounded-l-lg border border-brand-grid py-2 pl-8 pr-2 font-mono text-xs outline-none focus:border-brand-primary sm:w-36"
        />
        <button
          type="submit"
          disabled={searching}
          className="rounded-r-lg border border-l-0 border-brand-grid bg-white px-3 text-xs font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
        >
          {searching ? '…' : 'Go'}
        </button>
      </form>
    </div>
  );
};
