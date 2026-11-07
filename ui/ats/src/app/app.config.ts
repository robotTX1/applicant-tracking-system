import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { provideKeycloakAngular } from './keycloak.config';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { includeBearerTokenInterceptor } from 'keycloak-angular';
import { provideNativeDateAdapter } from '@angular/material/core';
import { BASE_PATH } from './api';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideKeycloakAngular(),
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    provideNativeDateAdapter(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withXhr(), withInterceptors([includeBearerTokenInterceptor])),
    { provide: BASE_PATH, useValue: environment.apiUrl },
  ],
};
