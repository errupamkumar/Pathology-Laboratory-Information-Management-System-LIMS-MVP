import { z } from 'zod';
import { ageFromDob, formatCurrency, fromPaise, toIsoDate, toPaise } from '../lib/format';
import type { BookOrderRequest } from '../types';
import { MONEY_REGEX, optionalEmail, PHONE_REGEX } from './common';

const MAX_AGE_YEARS = 150; // upper bound of the catalog's reference-interval ages

/**
 * Registration + billing form. Amounts stay strings while editing (no NaN from empty inputs) and
 * are converted by {@link toBookOrderRequest}. Bill limits depend on catalog prices, hence a factory.
 */
export function makeBookingSchema(priceById: ReadonlyMap<string, number>) {
  return z
    .object({
      /** Set when the phone lookup matched an existing patient; demographics are then not required. */
      patientId: z.string().nullable(),
      phone: z.string().trim().min(1, 'Phone number is mandatory').regex(PHONE_REGEX, 'Phone number format is invalid'),
      firstName: z.string().trim().max(100, 'First name must be at most 100 characters'),
      lastName: z.string().trim().max(100, 'Last name must be at most 100 characters'),
      dob: z.string(),
      gender: z.enum(['MALE', 'FEMALE', 'OTHER'], { errorMap: () => ({ message: 'Gender is mandatory' }) }),
      email: optionalEmail,
      referringDoctor: z.string().trim().max(150, 'Referring doctor must be at most 150 characters'),
      testCatalogIds: z
        .array(z.string())
        .min(1, 'Select at least one laboratory test')
        .max(50, 'At most 50 tests can be booked on one order'),
      discountAmount: z
        .string()
        .trim()
        .refine(value => value === '' || MONEY_REGEX.test(value), 'Enter a valid amount (max 2 decimals)'),
      paidAmount: z.string().trim().min(1, 'Paid amount is required').regex(MONEY_REGEX, 'Enter a valid amount (max 2 decimals)'),
    })
    .superRefine((values, ctx) => {
      if (!values.patientId) {
        if (!values.firstName) ctx.addIssue({ code: 'custom', path: ['firstName'], message: 'First name is mandatory' });
        if (!values.lastName) ctx.addIssue({ code: 'custom', path: ['lastName'], message: 'Last name is mandatory' });
        const dobIssue = validateDob(values.dob);
        if (dobIssue) ctx.addIssue({ code: 'custom', path: ['dob'], message: dobIssue });
      }

      const grossPaise = billGrossPaise(values.testCatalogIds, priceById);
      const discountPaise = MONEY_REGEX.test(values.discountAmount) ? toPaise(values.discountAmount) : 0;
      if (discountPaise > grossPaise) {
        ctx.addIssue({
          code: 'custom',
          path: ['discountAmount'],
          message: `Discount cannot exceed the gross bill (${formatCurrency(fromPaise(grossPaise))})`,
        });
        return;
      }
      const netPaise = grossPaise - discountPaise;
      if (MONEY_REGEX.test(values.paidAmount) && toPaise(values.paidAmount) > netPaise) {
        ctx.addIssue({
          code: 'custom',
          path: ['paidAmount'],
          message: `Paid amount cannot exceed the net payable (${formatCurrency(fromPaise(netPaise))})`,
        });
      }
    });
}

export type BookingForm = z.infer<ReturnType<typeof makeBookingSchema>>;

export const EMPTY_BOOKING_FORM: BookingForm = {
  patientId: null,
  phone: '',
  firstName: '',
  lastName: '',
  dob: '',
  gender: 'MALE',
  email: '',
  referringDoctor: '',
  testCatalogIds: [],
  discountAmount: '',
  paidAmount: '0.00',
};

export function billGrossPaise(testIds: readonly string[], priceById: ReadonlyMap<string, number>): number {
  return testIds.reduce((sum, id) => sum + toPaise(priceById.get(id) ?? 0), 0);
}

function validateDob(dob: string): string | null {
  if (!dob) return 'Date of birth is mandatory';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dob) || Number.isNaN(new Date(dob).getTime())) return 'Enter a valid date';
  if (dob >= toIsoDate(new Date())) return 'Date of birth must be in the past';
  if (ageFromDob(dob) > MAX_AGE_YEARS) return `Age cannot exceed ${MAX_AGE_YEARS} years`;
  return null;
}

/** Payload for POST /orders/book. A matched patient is referenced by id only. */
export function toBookOrderRequest(values: BookingForm): BookOrderRequest {
  const billing = {
    testCatalogIds: values.testCatalogIds,
    discountAmount: values.discountAmount ? Number(values.discountAmount) : 0,
    paidAmount: Number(values.paidAmount),
    referringDoctor: values.referringDoctor || null,
  };
  if (values.patientId) {
    return { patientId: values.patientId, ...billing };
  }
  return {
    firstName: values.firstName,
    lastName: values.lastName,
    dob: values.dob,
    gender: values.gender,
    phone: values.phone,
    email: values.email || null,
    ...billing,
  };
}
