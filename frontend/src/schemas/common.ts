import { z } from 'zod';

// Each pattern mirrors a backend constant so client and server reject exactly the same input.

/** PatientValidation.PHONE_REGEX */
export const PHONE_REGEX = /^[0-9+()\-\s]{7,20}$/;

/** AccessioningDtos.BARCODE_REGEX */
export const BARCODE_REGEX = /^[A-Za-z0-9-]{1,64}$/;

/** AuthDtos.PASSWORD_POLICY_REGEX */
export const PASSWORD_POLICY_REGEX = /^(?=.*[A-Za-z])(?=.*\d).{8,128}$/;
export const PASSWORD_POLICY_MESSAGE = 'Password must be 8-128 characters and contain at least one letter and one digit';

/** UserAdminDtos.CreateUserRequest username pattern */
export const USERNAME_REGEX = /^[a-zA-Z0-9._-]{3,100}$/;

/** @Digits(integer = 8, fraction = 2) on monetary request fields */
export const MONEY_REGEX = /^\d{1,8}(\.\d{1,2})?$/;

export const passwordPolicy = z.string().regex(PASSWORD_POLICY_REGEX, PASSWORD_POLICY_MESSAGE);

/** Optional email held as a string in the form ('' means not provided). */
export const optionalEmail = z
  .string()
  .trim()
  .max(150, 'Email must be at most 150 characters')
  .refine(value => value === '' || z.string().email().safeParse(value).success, 'Email address is invalid');
