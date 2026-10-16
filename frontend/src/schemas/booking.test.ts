import { describe, expect, it } from 'vitest';
import { BookingForm, EMPTY_BOOKING_FORM, makeBookingSchema, toBookOrderRequest } from './booking';

const prices = new Map([
  ['cbc', 450],
  ['lipid', 800.5],
]);
const schema = makeBookingSchema(prices);

const newPatient: BookingForm = {
  ...EMPTY_BOOKING_FORM,
  phone: '+91 98765 43210',
  firstName: 'Rahul',
  lastName: 'Verma',
  dob: '1988-04-12',
  gender: 'MALE',
  testCatalogIds: ['cbc', 'lipid'],
  paidAmount: '1250.50',
};

function issues(values: BookingForm): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(result.error.issues.map(issue => [issue.path.join('.'), issue.message]));
}

describe('booking schema', () => {
  it('accepts a complete new registration', () => {
    expect(issues(newPatient)).toEqual({});
  });

  it('requires demographics for a new patient', () => {
    const errors = issues({ ...newPatient, firstName: '', lastName: '', dob: '' });
    expect(errors.firstName).toBe('First name is mandatory');
    expect(errors.lastName).toBe('Last name is mandatory');
    expect(errors.dob).toBe('Date of birth is mandatory');
  });

  it('does not require demographics for a matched patient', () => {
    expect(issues({ ...newPatient, patientId: 'p-1', firstName: '', lastName: '', dob: '' })).toEqual({});
  });

  it('rejects future birth dates and malformed phone numbers', () => {
    const errors = issues({ ...newPatient, dob: '2999-01-01', phone: 'call me' });
    expect(errors.dob).toBe('Date of birth must be in the past');
    expect(errors.phone).toBe('Phone number format is invalid');
  });

  it('requires at least one test', () => {
    expect(issues({ ...newPatient, testCatalogIds: [], paidAmount: '0' }).testCatalogIds).toBe('Select at least one laboratory test');
  });

  it('caps the discount at the gross bill and payment at the net payable', () => {
    expect(issues({ ...newPatient, discountAmount: '1300' }).discountAmount).toMatch(/cannot exceed the gross bill/);
    expect(issues({ ...newPatient, discountAmount: '50.50', paidAmount: '1200.01' }).paidAmount).toMatch(/cannot exceed the net payable/);
    expect(issues({ ...newPatient, discountAmount: '50.50', paidAmount: '1200.00' })).toEqual({});
  });

  it('enforces at most 2 decimal places on amounts', () => {
    expect(issues({ ...newPatient, paidAmount: '10.555' }).paidAmount).toMatch(/max 2 decimals/);
  });
});

describe('toBookOrderRequest', () => {
  it('sends full demographics for a new patient', () => {
    expect(toBookOrderRequest({ ...newPatient, email: '', discountAmount: '' })).toEqual({
      firstName: 'Rahul',
      lastName: 'Verma',
      dob: '1988-04-12',
      gender: 'MALE',
      phone: '+91 98765 43210',
      email: null,
      testCatalogIds: ['cbc', 'lipid'],
      discountAmount: 0,
      paidAmount: 1250.5,
      referringDoctor: null,
    });
  });

  it('references a matched patient by id only', () => {
    const request = toBookOrderRequest({ ...newPatient, patientId: 'p-1', referringDoctor: 'Dr. A. Sharma' });
    expect(request).toEqual({
      patientId: 'p-1',
      testCatalogIds: ['cbc', 'lipid'],
      discountAmount: 0,
      paidAmount: 1250.5,
      referringDoctor: 'Dr. A. Sharma',
    });
  });
});
