import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService, TOKEN_KEY, USER_KEY } from './auth.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', children: [] }]),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('adds the Bearer token when logged in', () => {
    localStorage.setItem(TOKEN_KEY, 'my-jwt');
    client.get('/api/rooms').subscribe();
    const req = http.expectOne('/api/rooms');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt');
    req.flush([]);
  });

  it('sends no Authorization header when logged out', () => {
    client.get('/api/rooms').subscribe();
    const req = http.expectOne('/api/rooms');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush([]);
  });

  it('logs out on 401 but not on 403', () => {
    localStorage.setItem(TOKEN_KEY, 'expired');
    localStorage.setItem(USER_KEY, JSON.stringify({ id: 1, name: 'U', email: 'u@x', role: 'USER' }));
    const auth = TestBed.inject(AuthService);
    const logout = spyOn(auth, 'logout').and.callThrough();

    client.get('/api/stats').subscribe({ error: () => undefined });
    http.expectOne('/api/stats').flush({ message: 'nope' }, { status: 403, statusText: 'Forbidden' });
    expect(logout).not.toHaveBeenCalled();

    client.get('/api/rooms').subscribe({ error: () => undefined });
    http.expectOne('/api/rooms').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(logout).toHaveBeenCalled();
  });
});
