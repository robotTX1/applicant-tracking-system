import {
  placeholderToken,
  previewPersonFromToken,
  renderTemplate,
  sampleValues,
  unknownPlaceholders,
} from './email-template';

describe('email-template utils', () => {
  it('fills in known placeholders and keeps unknown ones', () => {
    expect(renderTemplate('Hi ${firstName}, re ${jobTitle} ${oops}', sampleValues('Globex'))).toBe(
      'Hi Anna, re Senior Java Developer ${oops}',
    );
    expect(renderTemplate('${companyName}', sampleValues('Globex'))).toBe('Globex');
  });

  it('finds placeholders the backend would not fill in', () => {
    expect(unknownPlaceholders('${firstName} ${firstname} ${firstname} ${ jobTitle }')).toEqual([
      '${firstname}',
      '${ jobTitle }',
    ]);
    expect(unknownPlaceholders('No placeholders, just $5 and {braces}')).toEqual([]);
  });

  it('builds tokens', () => {
    expect(placeholderToken('location')).toBe('${location}');
  });

  it('previews with the signed-in user as the candidate, falling back to samples', () => {
    const person = previewPersonFromToken({ given_name: 'Elek', family_name: 'Teszt' });
    expect(renderTemplate('${firstName} ${lastName}', sampleValues('Acme', person))).toBe(
      'Elek Teszt',
    );
    expect(sampleValues('Acme', { firstName: ' ', lastName: undefined })['firstName']).toBe('Anna');
    expect(previewPersonFromToken(undefined)).toEqual({
      firstName: undefined,
      lastName: undefined,
    });
    expect(previewPersonFromToken({ given_name: 42 }).firstName).toBeUndefined();
  });
});
