import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  DOCUMENT,
  ElementRef,
  inject,
  viewChild,
} from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import Keycloak from 'keycloak-js';
import { CompanyStateService } from '../../services/company-state.service';
import { HeroCollage } from './hero-collage/hero-collage';
import { ProductWindow } from './product-window/product-window';

export const LANDING_TITLE = 'ATS · Job board with AI CV screening';

/** What the company column offers, depending on who is looking. */
export type CompanyCardMode = 'signed-out' | 'register' | 'post-jobs' | 'member';

@Component({
  selector: 'app-landing',
  imports: [RouterLink, MatButtonModule, MatIconModule, HeroCollage, ProductWindow],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing {
  private readonly keycloak = inject(Keycloak);
  protected readonly companyState = inject(CompanyStateService);
  private readonly title = inject(Title);
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);

  private readonly sidesSection = viewChild<ElementRef<HTMLElement>>('sides');

  readonly signedIn = !!this.keycloak.authenticated;

  readonly companyMode = computed<CompanyCardMode>(() => {
    if (!this.signedIn) return 'signed-out';
    if (this.companyState.canPostJobs()) return 'post-jobs';
    if (this.companyState.hasCompany()) return 'member';
    return 'register';
  });

  constructor() {
    // Other pages don't set titles, so put the original back when leaving (like the job page)
    const originalTitle = this.title.getTitle();
    this.title.setTitle(LANDING_TITLE);
    this.destroyRef.onDestroy(() => this.title.setTitle(originalTitle));

    if (this.signedIn) {
      this.companyState.init().catch((err: unknown) => {
        console.debug('Failed to load companies on the landing page', err);
      });
    }

    afterNextRender(() => this.setUpReveal());
  }

  /** New visitors most likely have no account yet: start with sign-up. */
  signUp(target: 'jobs' | 'company'): void {
    const path = target === 'company' ? '/company/register' : '/jobs';
    this.keycloak.register({ redirectUri: `${window.location.origin}${path}` });
  }

  signIn(): void {
    this.keycloak.login({ redirectUri: `${window.location.origin}/company/register` });
  }

  /**
   * The circular reveal on the "sides" section: as the section scrolls in, a circle opens in an
   * inset panel of the hero's ground, and each block fades in once the circle holds all of it, so
   * no word is ever cut. The app scrolls inside the sidenav content, so that is what we listen to.
   * Phones and reduced motion get the section as it is.
   */
  private setUpReveal(): void {
    const section = this.sidesSection()?.nativeElement;
    const view = this.document.defaultView;
    if (!section || !view?.matchMedia) return;
    const still =
      view.matchMedia('(prefers-reduced-motion: reduce)').matches ||
      view.matchMedia('(max-width: 639px)').matches;
    if (still) return;

    const panel = section.querySelector<HTMLElement>('.reveal');
    const blocks = [...section.querySelectorAll<HTMLElement>('h2, .section__lead, .side')];
    if (!panel) return;
    section.classList.add('is-revealing');

    let frame = 0;
    const update = () => {
      frame = 0;
      const vh = view.innerHeight;
      const s = section.getBoundingClientRect();
      // 0 when the section's top enters the screen, 1 when it is a fifth of the screen from the top
      const u = Math.min(1, Math.max(0, (vh * 0.98 - s.top) / (vh * 0.78)));
      const t = 1 - (1 - u) * (1 - u);

      // Fully open: the panel is hidden (display: none measures as an empty box), so stop
      // measuring and show every block as it is
      const open = t >= 1;
      section.classList.toggle('is-open', open);
      if (open) {
        for (const block of blocks) block.style.opacity = '';
        return;
      }

      const p = panel.getBoundingClientRect();
      const visibleTop = Math.max(0, p.top);
      const visibleBottom = Math.min(vh, p.bottom);
      const cx = p.width / 2;
      const cy = visibleTop - p.top + Math.max(0, visibleBottom - visibleTop) / 2;
      const full = Math.hypot(cx, Math.max(cy, p.height - cy)) + 2;
      const radius = t * full;

      panel.style.setProperty('--reveal-r', `${radius}px`);
      panel.style.setProperty('--reveal-cx', `${cx}px`);
      panel.style.setProperty('--reveal-cy', `${cy}px`);

      // A block shows once the circle holds its farthest corner, fading over the next 60px
      for (const block of blocks) {
        const b = block.getBoundingClientRect();
        const dx = Math.max(Math.abs(b.left - p.left - cx), Math.abs(b.right - p.left - cx));
        const dy = Math.max(Math.abs(b.top - p.top - cy), Math.abs(b.bottom - p.top - cy));
        const reach = Math.hypot(dx, dy);
        block.style.opacity = String(Math.min(1, Math.max(0, (radius - reach) / 60 + 1)));
      }
    };
    const request = () => {
      if (!frame) frame = view.requestAnimationFrame(update);
    };

    const scroller = this.host.nativeElement.closest('.mat-drawer-content, .mat-sidenav-content');
    const target: EventTarget = scroller ?? view;
    target.addEventListener('scroll', request, { passive: true });
    view.addEventListener('resize', request);
    update();

    this.destroyRef.onDestroy(() => {
      target.removeEventListener('scroll', request);
      view.removeEventListener('resize', request);
      if (frame) view.cancelAnimationFrame(frame);
    });
  }
}
