import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type ThemeMode = 'system' | 'light' | 'dark';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly isBrowser = isPlatformBrowser(this.platformId);

  private readonly STORAGE_KEY = 'ats_theme_preference';

  readonly currentTheme = signal<ThemeMode>('system');
  readonly isDark = signal<boolean>(false);

  private mediaQueryList: MediaQueryList | null = null;
  private mediaQueryListener: ((e: MediaQueryListEvent) => void) | null = null;

  constructor() {
    if (this.isBrowser) {
      this.initTheme();
    }
  }

  private initTheme(): void {
    if (typeof window !== 'undefined' && window.matchMedia) {
      this.mediaQueryList = window.matchMedia('(prefers-color-scheme: dark)');
      this.mediaQueryListener = (e: MediaQueryListEvent) => {
        if (this.currentTheme() === 'system') {
          this.applyTheme('system', e.matches);
        }
      };
      this.mediaQueryList.addEventListener('change', this.mediaQueryListener);
    }

    let initialMode: ThemeMode = 'system';
    try {
      const saved = localStorage.getItem(this.STORAGE_KEY) as ThemeMode | null;
      if (saved === 'light' || saved === 'dark') {
        initialMode = saved;
      }
    } catch {
      // Fallback for restricted storage environments
    }

    this.setTheme(initialMode);
  }

  setTheme(mode: ThemeMode): void {
    this.currentTheme.set(mode);

    if (this.isBrowser) {
      try {
        if (mode === 'system') {
          localStorage.removeItem(this.STORAGE_KEY);
        } else {
          localStorage.setItem(this.STORAGE_KEY, mode);
        }
      } catch {
        // Ignore storage errors in restricted contexts
      }

      const systemPrefersDark = this.mediaQueryList?.matches ?? false;
      this.applyTheme(mode, systemPrefersDark);
    }
  }

  toggleTheme(): void {
    if (this.isDark()) {
      this.setTheme('light');
    } else {
      this.setTheme('dark');
    }
  }

  getThemeIcon(): string {
    switch (this.currentTheme()) {
      case 'dark':
        return 'dark_mode';
      case 'light':
        return 'light_mode';
      default:
        return 'settings_brightness';
    }
  }

  getThemeLabel(): string {
    switch (this.currentTheme()) {
      case 'dark':
        return 'Dark';
      case 'light':
        return 'Light';
      default:
        return 'System';
    }
  }

  private applyTheme(mode: ThemeMode, systemPrefersDark: boolean): void {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;

    if (mode === 'dark') {
      root.classList.add('dark-theme');
      root.classList.remove('light-theme');
      root.style.colorScheme = 'dark';
      this.isDark.set(true);
    } else if (mode === 'light') {
      root.classList.add('light-theme');
      root.classList.remove('dark-theme');
      root.style.colorScheme = 'light';
      this.isDark.set(false);
    } else {
      root.classList.remove('dark-theme', 'light-theme');
      root.style.colorScheme = 'light dark';
      this.isDark.set(systemPrefersDark);
    }
  }
}
