import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * HTTP interceptor: a function that runs for EVERY request made with HttpClient.
 *
 * 1. Adds "Authorization: Bearer <jwt>" so the backend knows who is calling. HttpRequest objects
 *    are immutable, so we clone() the request with the extra header.
 * 2. If the server answers 401 (token expired/invalid), logs the user out so they land on the
 *    login page instead of a half-broken screen. A 403 is NOT a logout: the user is known, they
 *    just may not do that particular thing - the page shows the message instead.
 *
 * Registered in app.config.ts via provideHttpClient(withInterceptors([authInterceptor])).
 * inject() works here because Angular runs interceptors inside an injection context.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token;

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Ignore 401s from the auth endpoints themselves: there it just means "wrong password".
      if (error.status === 401 && !req.url.includes('/api/auth/')) {
        auth.logout();
      }
      return throwError(() => error);   // re-throw so the calling component can show a message
    }),
  );
};
