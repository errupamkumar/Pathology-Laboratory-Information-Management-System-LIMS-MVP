import { useCallback, useEffect, useRef, useState } from 'react';
import { buildResultSections, ResultSection } from '../lib/clinical';
import { api } from '../services/api';
import type { DiagnosticOrder, OrderWithResults } from '../types';

export interface Worksheet {
  header: OrderWithResults;
  order: DiagnosticOrder;
  sections: ResultSection[];
}

type WorksheetState =
  | { status: 'idle' }
  | { status: 'loading'; worksheet?: Worksheet }
  | { status: 'ready'; worksheet: Worksheet }
  | { status: 'error'; error: unknown };

/**
 * Loads everything the result grid and report preview need for one order: demographics, saved
 * results and authorization (diagnostics), the ordered tests (orders) and their parameters (catalog).
 */
export function useOrderWorksheet(orderId: string | undefined) {
  const [state, setState] = useState<WorksheetState>({ status: 'idle' });
  const requestSeq = useRef(0);

  const load = useCallback(
    async (options: { silent?: boolean } = {}) => {
      if (!orderId) {
        setState({ status: 'idle' });
        return;
      }
      const seq = ++requestSeq.current;
      // A silent reload (after save / sign-off) keeps the current sheet on screen
      setState(current => ({
        status: 'loading',
        worksheet: options.silent && 'worksheet' in current ? current.worksheet : undefined,
      }));
      try {
        const [header, order, catalog] = await Promise.all([
          api.getOrderResults(orderId),
          api.getOrderById(orderId),
          api.getCatalogTests(),
        ]);
        if (seq !== requestSeq.current) return;
        const sections = buildResultSections(order, catalog, header.results ?? [], header.patientAge, header.patientGender);
        setState({ status: 'ready', worksheet: { header, order, sections } });
      } catch (error) {
        if (seq !== requestSeq.current) return;
        setState({ status: 'error', error });
      }
    },
    [orderId],
  );

  useEffect(() => {
    void load();
  }, [load]);

  const worksheet = state.status === 'ready' || state.status === 'loading' ? state.worksheet : undefined;
  return {
    worksheet,
    loading: state.status === 'loading',
    error: state.status === 'error' ? state.error : undefined,
    reload: load,
  };
}
