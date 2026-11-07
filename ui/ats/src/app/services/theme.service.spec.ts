import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    document.documentElement.style.colorScheme = '';

    TestBed.configureTestingModule({
      providers: [ThemeService],
    });
    service = TestBed.inject(ThemeService);
  });

  afterEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    document.documentElement.style.colorScheme = '';
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should set dark theme correctly', () => {
    service.setTheme('dark');
    expect(service.currentTheme()).toBe('dark');
    expect(service.isDark()).toBe(true);
    expect(document.documentElement.classList.contains('dark-theme')).toBe(true);
    expect(document.documentElement.classList.contains('light-theme')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('dark');
    expect(localStorage.getItem('ats_theme_preference')).toBe('dark');
  });

  it('should set light theme correctly', () => {
    service.setTheme('light');
    expect(service.currentTheme()).toBe('light');
    expect(service.isDark()).toBe(false);
    expect(document.documentElement.classList.contains('light-theme')).toBe(true);
    expect(document.documentElement.classList.contains('dark-theme')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('light');
    expect(localStorage.getItem('ats_theme_preference')).toBe('light');
  });

  it('should set system theme correctly and remove localStorage override', () => {
    service.setTheme('dark');
    service.setTheme('system');
    expect(service.currentTheme()).toBe('system');
    expect(document.documentElement.classList.contains('dark-theme')).toBe(false);
    expect(document.documentElement.classList.contains('light-theme')).toBe(false);
    expect(document.documentElement.style.colorScheme).toBe('light dark');
    expect(localStorage.getItem('ats_theme_preference')).toBeNull();
  });

  it('should toggle theme from light to dark and vice-versa', () => {
    service.setTheme('light');
    expect(service.isDark()).toBe(false);

    service.toggleTheme();
    expect(service.currentTheme()).toBe('dark');
    expect(service.isDark()).toBe(true);

    service.toggleTheme();
    expect(service.currentTheme()).toBe('light');
    expect(service.isDark()).toBe(false);
  });

  it('should return appropriate icons and labels', () => {
    service.setTheme('dark');
    expect(service.getThemeIcon()).toBe('dark_mode');
    expect(service.getThemeLabel()).toBe('Dark');

    service.setTheme('light');
    expect(service.getThemeIcon()).toBe('light_mode');
    expect(service.getThemeLabel()).toBe('Light');

    service.setTheme('system');
    expect(service.getThemeIcon()).toBe('settings_brightness');
    expect(service.getThemeLabel()).toBe('System');
  });
});
