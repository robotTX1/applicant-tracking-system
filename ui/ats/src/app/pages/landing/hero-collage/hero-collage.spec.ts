import { ComponentFixture, TestBed } from '@angular/core/testing';
import { COLLAGE_INTERVAL_MS, HeroCollage } from './hero-collage';

describe('HeroCollage', () => {
  let fixture: ComponentFixture<HeroCollage>;
  let component: HeroCollage;
  let reduced = false;
  let phone = false;

  beforeEach(() => {
    reduced = false;
    phone = false;
    vi.useFakeTimers();
    // jsdom has no matchMedia: give the component one that answers from the test's flags
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: (query: string) =>
        ({
          matches: query.includes('reduced-motion')
            ? reduced
            : query.includes('max-width') && phone,
          media: query,
        }) as MediaQueryList,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    delete (window as { matchMedia?: unknown }).matchMedia;
  });

  async function create() {
    TestBed.configureTestingModule({ imports: [HeroCollage] });
    fixture = TestBed.createComponent(HeroCollage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const shownSet = () =>
    [...(fixture.nativeElement as HTMLElement).querySelectorAll('.collage__set')].findIndex((s) =>
      s.classList.contains('is-on'),
    );

  it('cycles through the three sets every 2.8 seconds', async () => {
    await create();
    expect(component.collageSet()).toBe(0);
    vi.advanceTimersByTime(COLLAGE_INTERVAL_MS);
    expect(component.collageSet()).toBe(1);
    vi.advanceTimersByTime(COLLAGE_INTERVAL_MS * 2);
    expect(component.collageSet()).toBe(0);
    fixture.detectChanges();
    expect(shownSet()).toBe(0);
  });

  it('pauses and resumes from the pause button', async () => {
    await create();
    const pause = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
      '.collage__pause',
    )!;
    pause.click();
    fixture.detectChanges();
    expect(pause.getAttribute('aria-label')).toBe('Play the product preview');
    vi.advanceTimersByTime(COLLAGE_INTERVAL_MS * 3);
    expect(component.collageSet()).toBe(0);
    pause.click();
    vi.advanceTimersByTime(COLLAGE_INTERVAL_MS);
    expect(component.collageSet()).toBe(1);
  });

  it('holds the first set under reduced motion', async () => {
    reduced = true;
    await create();
    vi.advanceTimersByTime(COLLAGE_INTERVAL_MS * 3);
    expect(component.collageSet()).toBe(0);
  });

  it('holds the first set on a phone', async () => {
    phone = true;
    await create();
    vi.advanceTimersByTime(COLLAGE_INTERVAL_MS * 3);
    expect(component.collageSet()).toBe(0);
  });

  it('hides the inactive sets from assistive technology and describes the collage once', async () => {
    await create();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.collage')?.getAttribute('role')).toBe('img');
    expect(el.querySelectorAll('.collage__set[aria-hidden="true"]').length).toBe(2);
  });
});
