/**
 * Small helpers for building RSQL query strings safely.
 *
 * User input is always wrapped in single quotes with `\` and `'` escaped, so characters that are
 * meaningful in RSQL (`;`, `,`, `(`, `)`, `=`, `!`, `<`, `>`, `*`, quotes) can't change the
 * structure of the query.
 */

export function quote(value: string | number | boolean): string {
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;
}

export function eq(field: string, value: string | number | boolean): string {
  return `${field}==${quote(value)}`;
}

/** Case-insensitive "contains" (the backend wraps the value in `%...%`). */
export function ilike(field: string, value: string): string {
  return `${field}=ilike=${quote(value)}`;
}

export function isIn(field: string, values: readonly (string | number)[]): string {
  return `${field}=in=(${values.map(quote).join(',')})`;
}

/** Joins the non-empty clauses with AND (`;`). Returns `undefined` if nothing remains. */
export function and(...clauses: (string | null | undefined | false)[]): string | undefined {
  const parts = clauses.filter((c): c is string => !!c);
  if (parts.length === 0) return undefined;
  return parts.join(';');
}

/**
 * Joins the non-empty clauses with OR (`,`), parenthesised so it can be nested inside {@link and}.
 * Returns `undefined` if nothing remains.
 */
export function or(...clauses: (string | null | undefined | false)[]): string | undefined {
  const parts = clauses.filter((c): c is string => !!c);
  if (parts.length === 0) return undefined;
  return parts.length === 1 ? parts[0] : `(${parts.join(',')})`;
}
