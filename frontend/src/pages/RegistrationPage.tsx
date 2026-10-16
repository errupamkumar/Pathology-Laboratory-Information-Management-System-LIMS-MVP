import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  Check,
  CheckCircle2,
  Clock,
  CreditCard,
  Loader2,
  Mail,
  Phone,
  Printer,
  RefreshCw,
  Scan,
  Search,
  Stethoscope,
  User,
  UserPlus,
  X,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { a11yProps, Field, inputClass } from '../components/ui/Field';
import { CardListSkeleton } from '../components/ui/Skeleton';
import { useToast } from '../components/ui/Toaster';
import { useAltHotkey } from '../hooks/useHotkey';
import { ApiError } from '../lib/apiError';
import { formatCurrency, fromPaise, humanize, toIsoDate, toPaise } from '../lib/format';
import { isAbortError } from '../lib/http';
import { billGrossPaise, BookingForm, EMPTY_BOOKING_FORM, makeBookingSchema, toBookOrderRequest } from '../schemas/booking';
import { MONEY_REGEX, PHONE_REGEX } from '../schemas/common';
import { api } from '../services/api';
import type { DiagnosticOrder, Patient, TestCatalog } from '../types';

type FieldName = keyof BookingForm;

/** Enter moves through the form in this order; demographics are skipped for a matched patient. */
const ENTER_ORDER: FieldName[] = ['phone', 'firstName', 'lastName', 'dob', 'gender', 'email', 'referringDoctor', 'discountAmount', 'paidAmount'];
const DEMOGRAPHIC_FIELDS: FieldName[] = ['firstName', 'lastName', 'dob', 'gender', 'email'];

const RegistrationPage: React.FC = () => {
  const navigate = useNavigate();
  const toast = useToast();
  const { can } = useAuth();

  const [catalog, setCatalog] = useState<TestCatalog[]>([]);
  const [catalogState, setCatalogState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [catalogSearch, setCatalogSearch] = useState('');
  const [matchedPatient, setMatchedPatient] = useState<Patient | null>(null);
  const [lookingUp, setLookingUp] = useState(false);
  const [bookedOrder, setBookedOrder] = useState<DiagnosticOrder | null>(null);
  const submittingRef = useRef(false);

  const priceById = useMemo(() => new Map(catalog.map(test => [test.id, Number(test.price)])), [catalog]);
  const schema = useMemo(() => makeBookingSchema(priceById), [priceById]);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setFocus,
    setError,
    clearErrors,
    reset,
    formState: { errors, isSubmitting, isSubmitted },
  } = useForm<BookingForm>({
    resolver: zodResolver(schema),
    defaultValues: EMPTY_BOOKING_FORM,
    mode: 'onTouched',
  });

  const loadCatalog = useCallback(() => {
    setCatalogState('loading');
    api
      .getCatalogTests()
      .then(tests => {
        setCatalog(tests.filter(test => test.isActive !== false));
        setCatalogState('ready');
      })
      .catch(error => {
        setCatalogState('error');
        toast.apiError(error, 'Could not load the test catalog');
      });
  }, [toast]);

  useEffect(loadCatalog, [loadCatalog]);

  // ---- Billing (all arithmetic in paise) ----
  const selectedIds = watch('testCatalogIds');
  const discountInput = watch('discountAmount');
  const paidInput = watch('paidAmount');
  const grossPaise = billGrossPaise(selectedIds, priceById);
  const discountPaise = MONEY_REGEX.test(discountInput) ? toPaise(discountInput) : 0;
  const netPaise = Math.max(0, grossPaise - discountPaise);
  const paidPaise = MONEY_REGEX.test(paidInput) ? toPaise(paidInput) : 0;
  const balancePaise = Math.max(0, netPaise - paidPaise);

  // Default to full payment whenever the bill changes; the cashier can lower it for credit
  useEffect(() => {
    setValue('paidAmount', fromPaise(netPaise).toFixed(2), { shouldValidate: isSubmitted });
  }, [netPaise, setValue, isSubmitted]);

  // ---- Duplicate check: an existing patient is matched by phone number ----
  const phone = watch('phone');
  const matchedRef = useRef(matchedPatient);
  matchedRef.current = matchedPatient;

  const clearMatch = useCallback(() => {
    if (!matchedRef.current) return;
    setMatchedPatient(null);
    setValue('patientId', null);
    // The auto-filled demographics belonged to the matched record; never carry them into a new registration
    setValue('firstName', '');
    setValue('lastName', '');
    setValue('dob', '');
    setValue('gender', 'MALE');
    setValue('email', '');
  }, [setValue]);

  useEffect(() => {
    clearMatch();
    const value = phone.trim();
    if (!PHONE_REGEX.test(value)) return;

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setLookingUp(true);
      try {
        const patient = await api.lookupPatientByPhone(value, controller.signal);
        if (patient && !controller.signal.aborted) {
          setMatchedPatient(patient);
          setValue('patientId', patient.id);
          setValue('firstName', patient.firstName);
          setValue('lastName', patient.lastName);
          setValue('dob', patient.dob);
          setValue('gender', patient.gender);
          setValue('email', patient.email ?? '');
          clearErrors(DEMOGRAPHIC_FIELDS);
        }
      } catch (error) {
        // The lookup is a convenience; a failure must not block registration
        if (!isAbortError(error)) console.warn('Phone lookup failed', error);
      } finally {
        if (!controller.signal.aborted) setLookingUp(false);
      }
    }, 400);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
      setLookingUp(false);
    };
  }, [phone, clearMatch, setValue, clearErrors]);

  // ---- Actions ----
  const resetForm = useCallback(() => {
    reset(EMPTY_BOOKING_FORM);
    setMatchedPatient(null);
    setBookedOrder(null);
    window.setTimeout(() => setFocus('phone'), 0);
  }, [reset, setFocus]);

  const onSubmit = async (values: BookingForm) => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    try {
      const order = await api.registerAndBookOrder(toBookOrderRequest(values));
      setBookedOrder(order);
      toast.success(`Order ${order.accessionNumber} booked`, `${order.patient.fullName} · UHID ${order.patient.uhid}`);
      // Clear the form so a repeated Alt+S cannot book the same order twice
      reset(EMPTY_BOOKING_FORM);
      setMatchedPatient(null);
    } catch (error) {
      if (error instanceof ApiError) {
        for (const [field, message] of Object.entries(error.invalidFields)) {
          if (field in EMPTY_BOOKING_FORM) setError(field as FieldName, { message });
        }
      }
      toast.apiError(error, 'Booking failed');
    } finally {
      submittingRef.current = false;
    }
  };

  const submit = () => handleSubmit(onSubmit)();

  useAltHotkey('n', resetForm);
  useAltHotkey('s', () => void submit());

  const enterOrder = matchedPatient ? ENTER_ORDER.filter(f => !DEMOGRAPHIC_FIELDS.includes(f)) : ENTER_ORDER;
  const onEnter = (field: FieldName) => (event: React.KeyboardEvent) => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    const next = enterOrder[enterOrder.indexOf(field) + 1];
    if (next) setFocus(next);
    else void submit();
  };

  const toggleTest = (id: string) => {
    const next = selectedIds.includes(id) ? selectedIds.filter(t => t !== id) : [...selectedIds, id];
    setValue('testCatalogIds', next, { shouldValidate: isSubmitted });
  };

  const search = catalogSearch.trim().toLowerCase();
  const filteredCatalog = search
    ? catalog.filter(t => [t.testName, t.testCode, t.department].some(v => v.toLowerCase().includes(search)))
    : catalog;
  const selectedTests = selectedIds.map(id => catalog.find(t => t.id === id)).filter((t): t is TestCatalog => !!t);
  const demographicsLocked = !!matchedPatient;

  return (
    <div className="mx-auto max-w-7xl space-y-4 px-3 py-4 sm:space-y-6 sm:px-4 sm:py-6 lg:px-8">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-brand-grid/80 bg-white p-3.5 shadow-sm sm:p-5">
        <div>
          <h1 className="flex items-center space-x-2 text-base font-bold text-slate-900 sm:text-lg">
            <UserPlus className="h-5 w-5 text-brand-primary" aria-hidden />
            <span>Patient Registration & Billing</span>
          </h1>
          <p className="mt-0.5 text-[11px] text-slate-500 sm:text-xs">
            Keyboard-first: <Kbd>Alt+N</Kbd> new patient · <Kbd>Enter</Kbd> next field · <Kbd>Alt+S</Kbd> book & auto-spool
          </p>
        </div>
        <div className="flex w-full items-center justify-end space-x-2 sm:w-auto">
          <button
            type="button"
            onClick={resetForm}
            className="flex items-center space-x-1.5 rounded-lg border border-brand-grid px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            <RefreshCw className="h-3.5 w-3.5" aria-hidden />
            <span>New (Alt+N)</span>
          </button>
        </div>
      </div>

      {bookedOrder && (
        <BookingSuccess
          order={bookedOrder}
          onEnterResults={can('enterResults') ? () => navigate(`/results/${bookedOrder.id}`) : undefined}
          onAccession={can('accession') ? () => navigate('/accessioning') : undefined}
          onDismiss={() => setBookedOrder(null)}
        />
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="grid grid-cols-1 gap-5 sm:gap-6 lg:grid-cols-12">
        <input type="hidden" {...register('patientId')} />

        {/* 1. Demographics */}
        <section className="space-y-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 lg:col-span-4" aria-labelledby="demographics-heading">
          <div className="flex items-center justify-between border-b border-brand-grid/70 pb-3">
            <h2 id="demographics-heading" className="flex items-center space-x-1.5 text-xs font-bold uppercase tracking-wide text-slate-900 sm:text-sm">
              <User className="h-4 w-4 text-brand-primary" aria-hidden />
              <span>1. Patient Demographics</span>
            </h2>
            {matchedPatient && (
              <span className="rounded-full border border-blue-200 bg-blue-100 px-2 py-0.5 text-[10px] font-bold text-blue-800">Existing UHID</span>
            )}
          </div>

          <div className="space-y-3.5">
            <Field
              label="Phone number"
              htmlFor="phone"
              required
              error={errors.phone?.message}
              hint={
                matchedPatient ? undefined : lookingUp ? 'Checking for an existing patient…' : 'Existing patients are matched automatically by phone'
              }
            >
              <div className="relative">
                <Phone className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" aria-hidden />
                <input
                  {...register('phone')}
                  {...a11yProps('phone', errors.phone?.message)}
                  type="tel"
                  inputMode="tel"
                  autoComplete="off"
                  placeholder="+91 98765 43210"
                  onKeyDown={onEnter('phone')}
                  className={inputClass(!!errors.phone, 'pl-8 pr-7 font-mono font-medium')}
                />
                {lookingUp && <Loader2 className="absolute right-2.5 top-2.5 h-3.5 w-3.5 animate-spin text-slate-400" aria-hidden />}
                {matchedPatient && <Check className="absolute right-2.5 top-2.5 h-4 w-4 text-emerald-600" aria-hidden />}
              </div>
              {matchedPatient && (
                <p className="mt-1 text-[11px] font-semibold text-blue-700" role="status">
                  ✓ Matched {matchedPatient.uhid}: {matchedPatient.fullName}, {matchedPatient.age}y
                </p>
              )}
            </Field>

            <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
              <Field label="First name" htmlFor="firstName" required={!demographicsLocked} error={errors.firstName?.message}>
                <input
                  {...register('firstName')}
                  {...a11yProps('firstName', errors.firstName?.message)}
                  readOnly={demographicsLocked}
                  autoComplete="off"
                  placeholder="e.g. Rahul"
                  onKeyDown={onEnter('firstName')}
                  className={inputClass(!!errors.firstName)}
                />
              </Field>
              <Field label="Last name" htmlFor="lastName" required={!demographicsLocked} error={errors.lastName?.message}>
                <input
                  {...register('lastName')}
                  {...a11yProps('lastName', errors.lastName?.message)}
                  readOnly={demographicsLocked}
                  autoComplete="off"
                  placeholder="e.g. Verma"
                  onKeyDown={onEnter('lastName')}
                  className={inputClass(!!errors.lastName)}
                />
              </Field>
            </div>

            <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
              <Field label="Date of birth" htmlFor="dob" required={!demographicsLocked} error={errors.dob?.message}>
                <input
                  {...register('dob')}
                  {...a11yProps('dob', errors.dob?.message)}
                  type="date"
                  max={toIsoDate(new Date())}
                  readOnly={demographicsLocked}
                  onKeyDown={onEnter('dob')}
                  className={inputClass(!!errors.dob)}
                />
              </Field>
              <Field label="Gender" htmlFor="gender" required={!demographicsLocked} error={errors.gender?.message}>
                {/* A disabled control submits undefined in RHF, so a matched record shows a read-only copy instead */}
                {demographicsLocked ? (
                  <input id="gender" readOnly value={humanize(watch('gender'))} className={inputClass(false)} />
                ) : (
                  <select
                    {...register('gender')}
                    {...a11yProps('gender', errors.gender?.message)}
                    onKeyDown={onEnter('gender')}
                    className={inputClass(!!errors.gender)}
                  >
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                    <option value="OTHER">Other</option>
                  </select>
                )}
              </Field>
            </div>

            <Field label="Email address" htmlFor="email" error={errors.email?.message}>
              <div className="relative">
                <Mail className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" aria-hidden />
                <input
                  {...register('email')}
                  {...a11yProps('email', errors.email?.message)}
                  type="email"
                  readOnly={demographicsLocked}
                  autoComplete="off"
                  placeholder="patient@example.com"
                  onKeyDown={onEnter('email')}
                  className={inputClass(!!errors.email, 'pl-8')}
                />
              </div>
            </Field>

            <Field label="Referring physician" htmlFor="referringDoctor" error={errors.referringDoctor?.message} hint="Leave blank for self / walk-in">
              <div className="relative">
                <Stethoscope className="absolute left-2.5 top-2.5 h-3.5 w-3.5 text-slate-400" aria-hidden />
                <input
                  {...register('referringDoctor')}
                  {...a11yProps('referringDoctor', errors.referringDoctor?.message)}
                  autoComplete="off"
                  placeholder="Dr. A. Sharma"
                  onKeyDown={onEnter('referringDoctor')}
                  className={inputClass(!!errors.referringDoctor, 'pl-8')}
                />
              </div>
            </Field>
          </div>
        </section>

        {/* 2. Test selection */}
        <section className="flex flex-col space-y-3.5 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 lg:col-span-5" aria-labelledby="tests-heading">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-brand-grid/70 pb-3">
            <h2 id="tests-heading" className="flex items-center space-x-1.5 text-xs font-bold uppercase tracking-wide text-slate-900 sm:text-sm">
              <span>2. Test Selection</span>
              <span className="rounded-full bg-brand-primary/10 px-2 py-0.5 text-[11px] font-bold text-brand-primary">{selectedIds.length} selected</span>
            </h2>
            <div className="relative w-full sm:w-44">
              <Search className="absolute left-2.5 top-2 h-3.5 w-3.5 text-slate-400" aria-hidden />
              <input
                type="search"
                placeholder="Filter tests…"
                aria-label="Filter tests"
                value={catalogSearch}
                onChange={e => setCatalogSearch(e.target.value)}
                className="w-full rounded-lg border border-brand-grid py-1.5 pl-8 pr-2 text-xs outline-none focus:border-brand-primary"
              />
            </div>
          </div>

          {errors.testCatalogIds && (
            <p className="text-[11px] font-medium text-brand-panic" role="alert">
              {errors.testCatalogIds.message}
            </p>
          )}

          {catalogState === 'loading' && <CardListSkeleton />}
          {catalogState === 'error' && (
            <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-center text-xs text-red-900">
              The test catalog could not be loaded.
              <button type="button" onClick={loadCatalog} className="ml-2 font-bold underline">
                Retry
              </button>
            </div>
          )}
          {catalogState === 'ready' && (
            <div className="max-h-[440px] space-y-2.5 overflow-y-auto pr-1" role="group" aria-label="Available tests">
              {filteredCatalog.length === 0 && <p className="py-8 text-center text-xs text-slate-400">No tests match “{catalogSearch}”.</p>}
              {filteredCatalog.map(test => {
                const selected = selectedIds.includes(test.id);
                return (
                  <label
                    key={test.id}
                    className={`uiverse-card flex cursor-pointer items-center justify-between rounded-xl border p-3 text-xs transition-all focus-within:ring-2 focus-within:ring-brand-primary/40 ${
                      selected ? 'border-brand-primary bg-brand-primary/5 ring-1 ring-brand-primary' : 'border-brand-grid/70 hover:bg-slate-50/60'
                    }`}
                  >
                    <input type="checkbox" className="sr-only" checked={selected} onChange={() => toggleTest(test.id)} />
                    <div className="flex items-start space-x-3">
                      <span
                        className={`mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded ${
                          selected ? 'bg-brand-primary text-white' : 'border border-slate-300'
                        }`}
                        aria-hidden
                      >
                        {selected && <Check className="h-3 w-3 stroke-[3]" />}
                      </span>
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-bold text-slate-900">{test.testName}</span>
                          <span className="rounded border border-slate-200 bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] font-bold text-slate-700">
                            {test.testCode}
                          </span>
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-[10px] text-slate-500">
                          <span className="rounded bg-slate-100 px-1.5 py-0.5 font-medium">{test.department}</span>
                          <span className="font-semibold text-slate-700">Tube: {test.containerType}</span>
                          <span className="flex items-center space-x-1">
                            <Clock className="h-3 w-3 text-slate-400" aria-hidden />
                            <span>{test.tatMinutes}m TAT</span>
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="shrink-0 pl-2 text-right">
                      <span className="block text-sm font-extrabold text-slate-900">{formatCurrency(test.price)}</span>
                      <span className="text-[9px] font-medium text-slate-400">{test.parameters?.length ?? 0} params</span>
                    </div>
                  </label>
                );
              })}
            </div>
          )}
        </section>

        {/* 3. Billing */}
        <section className="flex flex-col justify-between space-y-4 rounded-xl border border-brand-grid/80 bg-white p-4 shadow-sm sm:p-5 lg:col-span-3" aria-labelledby="billing-heading">
          <div>
            <h2 id="billing-heading" className="flex items-center space-x-1.5 border-b border-brand-grid/70 pb-3 text-xs font-bold uppercase tracking-wide text-slate-900 sm:text-sm">
              <CreditCard className="h-4 w-4 text-brand-primary" aria-hidden />
              <span>3. Billing Summary</span>
            </h2>

            {selectedTests.length > 0 ? (
              <ul className="mt-3 space-y-1 border-b border-dashed border-brand-grid pb-3 text-[11px]">
                {selectedTests.map(test => (
                  <li key={test.id} className="flex items-center justify-between gap-2">
                    <span className="truncate text-slate-700">{test.testName}</span>
                    <span className="flex shrink-0 items-center gap-1 font-mono text-slate-900">
                      {formatCurrency(test.price)}
                      <button
                        type="button"
                        onClick={() => toggleTest(test.id)}
                        className="rounded p-0.5 text-slate-400 hover:text-brand-panic"
                        aria-label={`Remove ${test.testName}`}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-3 text-[11px] text-slate-400">No tests selected yet.</p>
            )}

            <div className="mt-3 space-y-3 text-xs">
              <div className="flex justify-between text-slate-600">
                <span>Gross ({selectedIds.length} tests)</span>
                <span className="font-bold text-slate-900">{formatCurrency(fromPaise(grossPaise))}</span>
              </div>

              <div>
                <div className="flex items-center justify-between gap-2">
                  <label htmlFor="discountAmount" className="text-slate-600">
                    Concession / discount
                  </label>
                  <input
                    {...register('discountAmount')}
                    {...a11yProps('discountAmount', errors.discountAmount?.message)}
                    inputMode="decimal"
                    placeholder="0.00"
                    onKeyDown={onEnter('discountAmount')}
                    className={inputClass(!!errors.discountAmount, 'w-24 py-1 text-right font-semibold')}
                  />
                </div>
                {errors.discountAmount && (
                  <p id="discountAmount-error" className="mt-1 text-right text-[11px] text-brand-panic" role="alert">
                    {errors.discountAmount.message}
                  </p>
                )}
              </div>

              <div className="flex justify-between border-t border-brand-grid/80 pt-2 text-sm font-extrabold text-slate-900">
                <span>Net payable</span>
                <span className="font-mono text-brand-primary">{formatCurrency(fromPaise(netPaise))}</span>
              </div>

              <div>
                <div className="flex items-center justify-between gap-2">
                  <label htmlFor="paidAmount" className="font-semibold text-slate-700">
                    Amount paid
                  </label>
                  <div className="flex items-center space-x-1">
                    <input
                      {...register('paidAmount')}
                      {...a11yProps('paidAmount', errors.paidAmount?.message)}
                      inputMode="decimal"
                      onKeyDown={onEnter('paidAmount')}
                      className={inputClass(!!errors.paidAmount, 'w-24 py-1 text-right font-bold text-emerald-700')}
                    />
                    <button
                      type="button"
                      onClick={() => setValue('paidAmount', fromPaise(netPaise).toFixed(2), { shouldValidate: true })}
                      className="rounded bg-slate-100 px-2 py-1 text-[10px] font-bold hover:bg-slate-200"
                    >
                      Full
                    </button>
                  </div>
                </div>
                {errors.paidAmount && (
                  <p id="paidAmount-error" className="mt-1 text-right text-[11px] text-brand-panic" role="alert">
                    {errors.paidAmount.message}
                  </p>
                )}
              </div>

              <div className="flex justify-between">
                <span className="text-slate-600">Balance due</span>
                <span className={`font-mono font-bold ${balancePaise > 0 ? 'text-brand-panic' : 'text-emerald-600'}`}>
                  {formatCurrency(fromPaise(balancePaise))}
                </span>
              </div>
            </div>
          </div>

          <div className="space-y-2 border-t border-brand-grid/80 pt-4">
            <button
              type="submit"
              disabled={isSubmitting || catalogState !== 'ready'}
              className="uiverse-btn-primary flex w-full items-center justify-center space-x-2 rounded-lg py-3 text-xs font-bold uppercase tracking-wider text-white shadow-md disabled:opacity-50"
            >
              <Printer className="h-4 w-4" aria-hidden />
              <span>{isSubmitting ? 'Booking order…' : 'Book & auto-spool (Alt+S)'}</span>
            </button>
            <p className="text-center text-[10px] text-slate-400">Sends the 80mm receipt and tube labels to the print agent</p>
          </div>
        </section>
      </form>
    </div>
  );
};

const Kbd: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <kbd className="rounded border border-slate-300 bg-slate-100 px-1 py-0.5 font-mono text-[10px]">{children}</kbd>
);

interface BookingSuccessProps {
  order: DiagnosticOrder;
  onEnterResults?: () => void;
  onAccession?: () => void;
  onDismiss: () => void;
}

const BookingSuccess: React.FC<BookingSuccessProps> = ({ order, onEnterResults, onAccession, onDismiss }) => (
  <div className="space-y-3 rounded-xl border border-emerald-300 bg-gradient-to-r from-emerald-50 to-teal-50 p-4 shadow-sm sm:p-5" role="status">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center space-x-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
          <CheckCircle2 className="h-6 w-6" aria-hidden />
        </div>
        <div>
          <h3 className="text-sm font-bold text-emerald-950 sm:text-base">Order booked: {order.accessionNumber}</h3>
          <p className="text-xs text-emerald-800">
            UHID <span className="font-mono font-bold text-slate-900">{order.patient.uhid}</span> · {order.patient.fullName} ·{' '}
            {order.items.length} test{order.items.length === 1 ? '' : 's'}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {onAccession && (
          <button
            type="button"
            onClick={onAccession}
            className="flex items-center space-x-1.5 rounded-lg border border-emerald-300 bg-white px-3 py-2 text-xs font-bold text-emerald-800 hover:bg-emerald-50"
          >
            <Scan className="h-3.5 w-3.5" aria-hidden />
            <span>Accession samples</span>
          </button>
        )}
        {onEnterResults && (
          <button
            type="button"
            onClick={onEnterResults}
            className="uiverse-btn-primary flex items-center space-x-1.5 rounded-lg px-4 py-2 text-xs font-bold text-white shadow"
          >
            <span>Enter results</span>
            <ArrowRight className="h-3.5 w-3.5" aria-hidden />
          </button>
        )}
        <button type="button" onClick={onDismiss} className="rounded p-1 text-emerald-700 hover:bg-emerald-100" aria-label="Dismiss">
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
    <div className="grid grid-cols-1 gap-2.5 rounded-lg border border-emerald-200 bg-white/80 p-3 text-xs text-slate-700 sm:grid-cols-3">
      <span className="flex items-center space-x-2">
        <Printer className="h-4 w-4 text-brand-primary" aria-hidden />
        <span>
          <strong>Receipt:</strong> 80mm ESC/POS spooled
        </span>
      </span>
      <span className="flex items-center space-x-2">
        <Scan className="h-4 w-4 text-purple-600" aria-hidden />
        <span>
          <strong>Tube labels:</strong> ZPL stickers spooled
        </span>
      </span>
      <span className="flex items-center space-x-2">
        <CreditCard className="h-4 w-4 text-emerald-600" aria-hidden />
        <span>
          <strong>Paid</strong> {formatCurrency(order.paidAmount)} of {formatCurrency(order.totalAmount)}
          {Number(order.balanceAmount) > 0 && <span className="text-brand-panic"> · due {formatCurrency(order.balanceAmount)}</span>}
        </span>
      </span>
    </div>
  </div>
);

export default RegistrationPage;
