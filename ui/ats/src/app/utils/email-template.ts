import { CompanyEmailTemplateType } from '../api';

export const MAX_TEMPLATES_PER_TYPE = 3;
export const SUBJECT_MAX_LENGTH = 255;
export const BODY_MAX_LENGTH = 2000;

export interface TemplatePlaceholder {
  key: string;
  label: string;
  /** Shown in the preview instead of the placeholder. */
  sample: string;
}

/** The placeholders the backend fills in (EmailServiceImpl); anything else is sent as typed. */
export const TEMPLATE_PLACEHOLDERS: readonly TemplatePlaceholder[] = [
  { key: 'firstName', label: 'First name', sample: 'Anna' },
  { key: 'lastName', label: 'Last name', sample: 'Kovács' },
  { key: 'jobTitle', label: 'Job title', sample: 'Senior Java Developer' },
  { key: 'companyName', label: 'Company', sample: 'Acme' },
  { key: 'location', label: 'Location', sample: 'Budapest' },
];

const KNOWN_KEYS = new Set(TEMPLATE_PLACEHOLDERS.map((p) => p.key));
const PLACEHOLDER_PATTERN = /\$\{([^}]*)\}/g;

export function placeholderToken(key: string): string {
  return `\${${key}}`;
}

/** Fills in known placeholders; unknown ones stay as typed, like in the real email. */
export function renderTemplate(text: string, values: Record<string, string>): string {
  return text.replace(PLACEHOLDER_PATTERN, (token, key: string) =>
    Object.prototype.hasOwnProperty.call(values, key) ? values[key] : token,
  );
}

export interface PreviewPerson {
  firstName?: string;
  lastName?: string;
}

/**
 * Preview values: the company's real name, and the signed-in user as the candidate (so they can
 * picture the email), falling back to sample details for anything missing.
 */
export function sampleValues(companyName?: string, person?: PreviewPerson): Record<string, string> {
  const values = Object.fromEntries(TEMPLATE_PLACEHOLDERS.map((p) => [p.key, p.sample]));
  if (companyName) values['companyName'] = companyName;
  if (person?.firstName?.trim()) values['firstName'] = person.firstName.trim();
  if (person?.lastName?.trim()) values['lastName'] = person.lastName.trim();
  return values;
}

/** The signed-in user's name from the Keycloak token, for previews. */
export function previewPersonFromToken(token: Record<string, unknown> | undefined): PreviewPerson {
  const text = (v: unknown) => (typeof v === 'string' ? v : undefined);
  return { firstName: text(token?.['given_name']), lastName: text(token?.['family_name']) };
}

/** Placeholders the backend won't fill in (typos like `${firstname}`), without duplicates. */
export function unknownPlaceholders(text: string): string[] {
  const unknown = new Set<string>();
  for (const match of text.matchAll(PLACEHOLDER_PATTERN)) {
    if (!KNOWN_KEYS.has(match[1])) unknown.add(match[0]);
  }
  return [...unknown];
}

export const TEMPLATE_TYPE_LABELS: Record<CompanyEmailTemplateType, string> = {
  [CompanyEmailTemplateType.Offer]: 'Offer',
  [CompanyEmailTemplateType.Decline]: 'Decline',
};
