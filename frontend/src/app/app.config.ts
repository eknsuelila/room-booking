import { ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';

/**
 * Application-wide providers (in standalone Angular this replaces the old AppModule).
 *
 * - provideRouter: enables routing. withComponentInputBinding() lets route params (":id") and
 *   query params ("?date=...") arrive directly as component @Input()s - no ActivatedRoute
 *   boilerplate needed in the pages.
 * - provideHttpClient: enables HttpClient and plugs in our JWT interceptor, which runs for
 *   every request.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
