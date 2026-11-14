import { CanActivateFn } from '@angular/router';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';

export const authGuard = createAuthGuard<CanActivateFn>(
  async (_route, state, authData: AuthGuardData) => {
    const { authenticated, keycloak } = authData;
    if (!authenticated) {
      await keycloak.login({
        redirectUri: window.location.origin + state.url,
      });
      return false;
    }
    return true;
  },
);
