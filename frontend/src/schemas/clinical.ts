import { z } from 'zod';
import type { RejectionReason } from '../types';
import { BARCODE_REGEX } from './common';

export const REJECTION_REASONS: { value: RejectionReason; label: string }[] = [
  { value: 'HEMOLYZED', label: 'Hemolyzed (ruptured RBCs)' },
  { value: 'CLOTTED', label: 'Clotted (fibrin clots in EDTA / citrate)' },
  { value: 'INSUFFICIENT_VOLUME', label: 'Insufficient volume (QNS)' },
  { value: 'INCORRECT_CONTAINER', label: 'Incorrect container / tube' },
  { value: 'CONTAMINATED', label: 'Contaminated specimen' },
  { value: 'LEAKING_SPECIMEN', label: 'Leaking specimen' },
  { value: 'LABEL_MISMATCH', label: 'Label / patient mismatch' },
];

const REJECTION_VALUES = REJECTION_REASONS.map(r => r.value) as [RejectionReason, ...RejectionReason[]];

export const scanSchema = z.object({
  barcode: z
    .string()
    .trim()
    .min(1, 'Scan or type a barcode')
    .regex(BARCODE_REGEX, 'Barcode may contain only letters, digits and hyphens (max 64)'),
});
export type ScanForm = z.infer<typeof scanSchema>;

export const rejectionSchema = z.object({
  rejectionReason: z.enum(REJECTION_VALUES, { errorMap: () => ({ message: 'Rejection reason is mandatory' }) }),
  rejectionNotes: z.string().trim().max(1000, 'Rejection notes must be at most 1000 characters'),
});
export type RejectionForm = z.infer<typeof rejectionSchema>;

export const signOffSchema = z.object({
  pathologistName: z.string().trim().min(1, 'Pathologist name is mandatory').max(150, 'At most 150 characters'),
  licenseNumber: z.string().trim().min(1, 'License number is mandatory').max(100, 'At most 100 characters'),
  // Sign-off is irreversible and locks the results, so require an explicit review attestation
  attest: z.boolean().refine(checked => checked, 'Confirm that you have reviewed every result'),
});
export type SignOffForm = z.infer<typeof signOffSchema>;

export const RESULT_VALUE_MAX = 100;
export const RESULT_NOTES_MAX = 1000;
