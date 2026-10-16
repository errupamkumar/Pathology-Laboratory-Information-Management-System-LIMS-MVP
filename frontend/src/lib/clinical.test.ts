import { describe, expect, it } from 'vitest';
import type { ReferenceRange, TestCatalog, TestResult } from '../types';
import { buildResultSections, evaluateObservedValue, formatReferenceInterval, selectReferenceRange } from './clinical';

const range = (overrides: Partial<ReferenceRange>): ReferenceRange => ({
  id: crypto.randomUUID(),
  gender: 'ALL',
  minAge: 0,
  maxAge: 150,
  ...overrides,
});

describe('selectReferenceRange', () => {
  const ranges = [
    range({ gender: 'ALL', normalLow: 12, normalHigh: 16 }),
    range({ gender: 'MALE', minAge: 18, maxAge: 150, normalLow: 13.5, normalHigh: 17.5 }),
    range({ gender: 'FEMALE', minAge: 18, maxAge: 150, normalLow: 12, normalHigh: 15.5 }),
  ];

  it('prefers the gender-specific interval over ALL', () => {
    expect(selectReferenceRange(ranges, 40, 'MALE')?.normalLow).toBe(13.5);
    expect(selectReferenceRange(ranges, 40, 'FEMALE')?.normalHigh).toBe(15.5);
  });

  it('falls back to ALL when no gender-specific interval covers the age', () => {
    expect(selectReferenceRange(ranges, 10, 'MALE')?.normalLow).toBe(12);
  });

  it('returns undefined when nothing matches', () => {
    expect(selectReferenceRange([range({ minAge: 0, maxAge: 12 })], 40, 'MALE')).toBeUndefined();
    expect(selectReferenceRange(undefined, 40, 'MALE')).toBeUndefined();
  });
});

describe('evaluateObservedValue (mirrors CriticalRangeEvaluator)', () => {
  const hb = { normalLow: 13.5, normalHigh: 17.5, criticalLow: 7, criticalHigh: 20 };

  it.each([
    ['15.2', false, false],
    ['12.9', true, false],
    ['18', true, false],
    ['6.5', true, true],
    ['21', true, true],
    ['', false, false],
  ])('%s → outOfRange=%s critical=%s', (value, isOutOfRange, isCritical) => {
    expect(evaluateObservedValue(hb, value)).toEqual({ isOutOfRange, isCritical });
  });

  it('compares qualitative results case-insensitively with the text normal value', () => {
    const urine = { textNormalVal: 'Negative' };
    expect(evaluateObservedValue(urine, 'negative').isOutOfRange).toBe(false);
    expect(evaluateObservedValue(urine, 'Positive (++)').isOutOfRange).toBe(true);
  });

  it('does not flag when no interval is established', () => {
    expect(evaluateObservedValue(undefined, '99')).toEqual({ isOutOfRange: false, isCritical: false });
  });

  it('formats intervals', () => {
    expect(formatReferenceInterval(hb)).toBe('13.5 – 17.5');
    expect(formatReferenceInterval({ normalHigh: 200 })).toBe('≤ 200');
    expect(formatReferenceInterval({ textNormalVal: 'Negative' })).toBe('Negative');
    expect(formatReferenceInterval(undefined)).toBe('Not established');
  });
});

describe('buildResultSections', () => {
  const catalog: TestCatalog[] = [
    {
      id: 'cbc',
      testCode: 'CBC',
      testName: 'Complete Blood Count',
      department: 'Hematology',
      specimenType: 'Whole Blood',
      containerType: 'EDTA',
      price: 450,
      tatMinutes: 240,
      isActive: true,
      parameters: [
        { id: 'wbc', parameterName: 'WBC', unit: '10^3/uL', displayOrder: 2, referenceRanges: [range({ normalLow: 4, normalHigh: 11 })] },
        { id: 'hb', parameterName: 'Hemoglobin', unit: 'g/dL', displayOrder: 1, referenceRanges: [range({ normalLow: 12, normalHigh: 16 })] },
      ],
    },
  ];
  const order = { items: [{ id: 'i1', testCatalogId: 'cbc', testCode: 'CBC', testName: 'Complete Blood Count', price: 450 }] };

  it('lists every ordered parameter by display order and overlays saved results', () => {
    const saved: TestResult[] = [
      { orderId: 'o1', parameterId: 'wbc', parameterName: 'WBC', observedValue: '12.5', isOutOfRange: true, normalLow: 4, normalHigh: 11 },
    ];

    const [section] = buildResultSections(order, catalog, saved, 40, 'MALE');

    expect(section.testCode).toBe('CBC');
    expect(section.rows.map(r => r.parameterId)).toEqual(['hb', 'wbc']);
    expect(section.rows[0].saved).toBeUndefined();
    expect(section.rows[0].range?.normalLow).toBe(12);
    expect(section.rows[1].saved?.observedValue).toBe('12.5');
  });

  it('keeps saved results whose parameter is no longer in the catalog', () => {
    const saved: TestResult[] = [{ orderId: 'o1', parameterId: 'retired', parameterName: 'Old analyte', observedValue: '1' }];

    const sections = buildResultSections(order, catalog, saved, 40, 'MALE');

    expect(sections).toHaveLength(2);
    expect(sections[1].rows[0].parameterName).toBe('Old analyte');
  });
});
