import { afterNextRender, Component, DestroyRef, DOCUMENT, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** The collage changes set every 2.8 s (taste-skill: the triptych hero's moment). */
export const COLLAGE_INTERVAL_MS = 2800;
const COLLAGE_SETS = 3;

/**
 * The landing hero's collage: three sets of live product UI pieces (applying, screening, deciding)
 * that crossfade in turn, with a pause button. Phones and reduced motion hold the first set.
 */
@Component({
  selector: 'app-hero-collage',
  imports: [MatIconModule],
  templateUrl: './hero-collage.html',
  styleUrl: './hero-collage.scss',
})
export class HeroCollage {
  private readonly document = inject(DOCUMENT);

  readonly collageSet = signal(0);
  readonly collagePaused = signal(false);

  private timer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
    afterNextRender(() => {
      if (this.motionAllowed()) this.start();
    });
  }

  toggleCollage(): void {
    if (this.collagePaused()) {
      this.collagePaused.set(false);
      this.start();
    } else {
      this.collagePaused.set(true);
      this.stop();
    }
  }

  private motionAllowed(): boolean {
    const view = this.document.defaultView;
    if (!view?.matchMedia) return false;
    return (
      !view.matchMedia('(prefers-reduced-motion: reduce)').matches &&
      !view.matchMedia('(max-width: 639px)').matches
    );
  }

  private start(): void {
    this.stop();
    this.timer = setInterval(
      () => this.collageSet.update((i) => (i + 1) % COLLAGE_SETS),
      COLLAGE_INTERVAL_MS,
    );
  }

  private stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}
