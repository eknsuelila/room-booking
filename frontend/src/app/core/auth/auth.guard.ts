import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthService } from './auth.service';

/**
 * Route guards decide whether a navigation may proceed. A guard returns:
 *   - true      -> go ahead;
 *   - a UrlTree -> redirect there instead (router.createUrlTree([...])).
 *
 * Functional guards (plain functions instead of classes) are the modern Angular style; they
 * use inject() to get services.
 *
 * NOTE: guards only improve the user experience (no flashing of pages you can't use). The real
 * protection is on the server: every API call is checked there, whatever the browser does.
 */

/** Only logged-in users may enter; anonymous visitors are sent to /login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);
};

/** Login/register pages: users who are already logged in go straight to their home page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? inject(Router).createUrlTree([auth.homeUrl()]) : true;
};

/**
 * Role-based guard. The allowed roles come from the route definition:
 *
 *   { path: 'admin', canActivate: [authGuard, roleGuard], data: { roles: ['ADMIN'] }, ... }
 *
 * Anonymous users go to /login; logged-in users without the role go to /rooms (a page every
 * user may see) rather than a dead end. Routes without `data.roles` are allowed.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const roles = (route.data['roles'] as Role[] | undefined) ?? [];

  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  if (roles.length === 0 || auth.hasAnyRole(roles)) {
    return true;
  }
  return router.createUrlTree(['/rooms']);
};
