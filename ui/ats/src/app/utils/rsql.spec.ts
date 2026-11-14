import { and, eq, ilike, isIn, or, quote } from './rsql';

describe('rsql', () => {
  describe('quote', () => {
    it('wraps values in single quotes', () => {
      expect(quote('java')).toBe("'java'");
      expect(quote(42)).toBe("'42'");
      expect(quote(true)).toBe("'true'");
    });

    it('escapes single quotes and backslashes', () => {
      expect(quote("O'Reilly")).toBe("'O\\'Reilly'");
      expect(quote('C:\\dev')).toBe("'C:\\\\dev'");
      expect(quote("\\'")).toBe("'\\\\\\''");
    });

    it('keeps RSQL operators inside the quoted value', () => {
      expect(quote("a';status==DRAFT;title=='b")).toBe("'a\\';status==DRAFT;title==\\'b'");
      expect(quote('a,b;(c)')).toBe("'a,b;(c)'");
    });
  });

  it('builds comparison clauses', () => {
    expect(eq('status', 'PUBLISHED')).toBe("status=='PUBLISHED'");
    expect(ilike('title', 'dev')).toBe("title=ilike='dev'");
    expect(isIn('id', [1, 2, 3])).toBe("id=in=('1','2','3')");
  });

  describe('and / or', () => {
    it('drops empty clauses', () => {
      expect(and('a==1', '', null, undefined, false, 'b==2')).toBe('a==1;b==2');
      expect(or('', 'a==1', null)).toBe('a==1');
    });

    it('returns undefined when no clause remains', () => {
      expect(and()).toBeUndefined();
      expect(or('', null)).toBeUndefined();
    });

    it('parenthesises multi-clause OR so it nests inside AND', () => {
      expect(and('s==1', or('a==1', 'b==2'))).toBe('s==1;(a==1,b==2)');
    });
  });
});
