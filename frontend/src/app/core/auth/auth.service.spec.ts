import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService, TOKEN_KEY } from './auth.service';

/**
 * HttpTestingController intercepts HttpClient calls so no real network requests are made.
 * We assert on the request that was sent, then "flush" a fake response back to the service.
 * The router gets a 'login' route because logout() navigates there.
 */
describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'login', children: [] }])],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify()); // fails the test if an unexpected request was made

  it('starts logged out', () => {
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.isAdmin()).toBeFalse();
    expect(service.token).toBeNull();
  });

  it('stores the token, user and role after login', () => {
    service.login('admin@example.com', 'password123').subscribe();

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'admin@example.com', password: 'password123' });
    req.flush({ token: 'abc.def.ghi', user: { id: 1, name: 'Ada', email: 'admin@example.com', role: 'ADMIN' } });

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.isAdmin()).toBeTrue();
    expect(service.token).toBe('abc.def.ghi');
    expect(service.homeUrl()).toBe('/admin/dashboard');
  });

  it('clears everything on logout', () => {
    service.login('u@example.com', 'x').subscribe();
    http.expectOne('/api/auth/login').flush({ token: 't', user: { id: 2, name: 'U', email: 'u@example.com', role: 'USER' } });
    expect(service.homeUrl()).toBe('/rooms');

    service.logout();

    expect(service.isLoggedIn()).toBeFalse();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
