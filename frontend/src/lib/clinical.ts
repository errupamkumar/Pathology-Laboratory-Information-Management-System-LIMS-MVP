import type { DiagnosticOrder, PatientGender, ReferenceRange, TestCatalog, TestResult } from '../types';

/**
 * Mirrors ParameterReferenceRangeRepository.findBestMatchingRange: the patient's age must fall
 * inside [minAge, maxAge] and a gender-specific interval wins over an `ALL` interval.
 */
export function selectReferenceRange(
  ranges: readonly ReferenceRange[] | undefined,
  age: number,
  gender: PatientGender | string,
): ReferenceRange | undefined {
  if (!ranges || ranges.length === 0) return undefined;
  const candidates = ranges.filter(
    r => age >= r.minAge && age <= r.maxAge && (r.gender === gender || r.gender === 'ALL'),
  );
  return candidates.find(r => r.gender === gender) ?? candidates[0];
}

interface RangeBounds {
  normalLow?: number | null;
  normalHigh?: number | null;
  criticalLow?: number | null;
  criticalHigh?: number | null;
  textNormalVal?: string | null;
}

export interface Evaluation {
  isOutOfRange: boolean;
  isCritical: boolean;
}

// Same grammar as java.math.BigDecimal(String) for the plain decimal values technicians type
const NUMERIC = /^[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?$/;

export function isNumericValue(value: string): boolean {
  return NUMERIC.test(value.trim());
}

/**
 * Live preview of CriticalRangeEvaluator so technicians see flags while typing. The server
 * re-evaluates on save and its flags are authoritative.
 */
export function evaluateObservedValue(range: RangeBounds | undefined, observed: string): Evaluation {
  const value = observed.trim();
  if (!value || !range) return { isOutOfRange: false, isCritical: false };

  if (!isNumericValue(value)) {
    const expected = range.textNormalVal?.trim();
    return { isOutOfRange: !!expected && value.toLowerCase() !== expected.toLowerCase(), isCritical: false };
  }

  const num = Number(value);
  const below = (bound?: number | null) => bound != null && num < Number(bound);
  const above = (bound?: number | null) => bound != null && num > Number(bound);
  return {
    isOutOfRange: below(range.normalLow) || above(range.normalHigh),
    isCritical: below(range.criticalLow) || above(range.criticalHigh),
  };
}

export function formatReferenceInterval(range: RangeBounds | undefined): string {
  if (!range) return 'Not established';
  if (range.normalLow != null && range.normalHigh != null) return `${range.normalLow} – ${range.normalHigh}`;
  if (range.normalLow != null) return `≥ ${range.normalLow}`;
  if (range.normalHigh != null) return `≤ ${range.normalHigh}`;
  if (range.textNormalVal) return range.textNormalVal;
  return 'Not established';
}

export interface ResultRow {
  parameterId: string;
  parameterName: string;
  unit?: string | null;
  testCode: string;
  testName: string;
  range?: RangeBounds;
  saved?: TestResult;
}

export interface ResultSection {
  testCode: string;
  testName: string;
  rows: ResultRow[];
}

/**
 * The results endpoint only returns parameters that already have a value. Build the full
 * worksheet from the ordered tests' catalog parameters and overlay the saved results, so a
 * partially entered order still shows every pending parameter.
 */
export function buildResultSections(
  order: Pick<DiagnosticOrder, 'items'>,
  catalog: readonly TestCatalog[],
  saved: readonly TestResult[],
  patientAge: number,
  patientGender: PatientGender | string,
): ResultSection[] {
  const savedByParameter = new Map(saved.map(r => [r.parameterId, r]));
  const catalogById = new Map(catalog.map(t => [t.id, t]));
  const placed = new Set<string>();
  const sections: ResultSection[] = [];

  for (const item of order.items) {
    const test = catalogById.get(item.testCatalogId);
    const parameters = [...(test?.parameters ?? [])].sort((a, b) => a.displayOrder - b.displayOrder);
    const rows: ResultRow[] = [];
    for (const parameter of parameters) {
      if (placed.has(parameter.id)) continue;
      placed.add(parameter.id);
      const result = savedByParameter.get(parameter.id);
      rows.push({
        parameterId: parameter.id,
        parameterName: parameter.parameterName,
        unit: parameter.unit,
        testCode: item.testCode,
        testName: item.testName,
        // The server resolves the interval when it stores a result; prefer its copy
        range: result && hasBounds(result) ? result : selectReferenceRange(parameter.referenceRanges, patientAge, patientGender),
        saved: result,
      });
    }
    if (rows.length > 0) {
      sections.push({ testCode: item.testCode, testName: item.testName, rows });
    }
  }

  // Results whose parameter is no longer in the catalog (e.g. a retired parameter) must still be visible
  const orphans = saved.filter(r => !placed.has(r.parameterId));
  if (orphans.length > 0) {
    sections.push({
      testCode: 'OTHER',
      testName: 'Other recorded results',
      rows: orphans.map(r => ({
        parameterId: r.parameterId,
        parameterName: r.parameterName,
        unit: r.unit,
        testCode: 'OTHER',
        testName: 'Other recorded results',
        range: r,
        saved: r,
      })),
    });
  }
  return sections;
}

function hasBounds(r: RangeBounds): boolean {
  return r.normalLow != null || r.normalHigh != null || r.criticalLow != null || r.criticalHigh != null || !!r.textNormalVal;
}
