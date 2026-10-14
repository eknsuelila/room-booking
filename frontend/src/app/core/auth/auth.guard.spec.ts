import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { roleGuard } from './auth.guard';
import { TOKEN_KEY, USER_KEY } from './auth.service';
import { Role } from '../models';

/**
 * roleGuard reads the allowed roles from route.data. We build a fake route snapshot with
 * data: { roles: ['ADMIN'] } and run the guard inside TestBed's injection context, because the
 * guard uses inject().
 */
describe('roleGuard', () => {
  function loginAs(role: Role | null): void {
    localStorage.clear();
    if (role) {
      localStorage.setItem(TOKEN_KEY, 'token');
      localStorage.setItem(USER_KEY, JSON.stringify({ id: 1, name: 'X', email: 'x@example.com', role }));
    }
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
  }

  function runGuard(roles: Role[]) {
    const route = { data: { roles } } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, {} as RouterStateSnapshot));
  }

  function urlOf(result: unknown): string {
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  afterEach(() => localStorage.clear());

  it('lets admins into admin routes', () => {
    loginAs('ADMIN');
    expect(runGuard(['ADMIN'])).toBeTrue();
  });

  it('redirects regular users to /rooms', () => {
    loginAs('USER');
    expect(urlOf(runGuard(['ADMIN']))).toBe('/rooms');
  });

  it('redirects anonymous visitors to /login', () => {
    loginAs(null);
    expect(urlOf(runGuard(['ADMIN']))).toBe('/login');
  });
});
