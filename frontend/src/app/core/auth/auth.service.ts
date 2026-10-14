import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Role, User } from '../models';

/**
 * localStorage keys, prefixed with the app name so they never collide with other apps served
 * from the same origin (e.g. localhost). The token survives page refreshes until logout.
 */
export const TOKEN_KEY = 'roombooking.token';
export const USER_KEY = 'roombooking.user';

/**
 * Owns everything about "who is logged in" and "what role do they have".
 *
 * `@Injectable({ providedIn: 'root' })` registers ONE shared instance (a singleton) for the whole
 * app, created the first time something injects it. Components, guards and the HTTP interceptor
 * all see the same state.
 *
 * State is kept in Angular SIGNALS. A signal is a reactive value: templates and `computed()`
 * values that read it are automatically updated when it changes. When login() sets the user,
 * the navbar (which reads `isAdmin()`) re-renders by itself - no manual change detection, no
 * Subjects to subscribe to.
 *
 * SECURITY NOTE: the role stored here only decides what the UI SHOWS. Every admin endpoint is
 * protected on the server with @PreAuthorize, so editing localStorage gains nothing.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  // inject() is the modern alternative to constructor parameters for dependency injection.
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** `signal(initialValue)` creates a writable signal. Private: only this service may change it. */
  private readonly _currentUser = signal<User | null>(this.readStoredUser());

  /** Read-only view for components: they can read `currentUser()` but not `.set()` it. */
  readonly currentUser = this._currentUser.asReadonly();

  /**
   * `computed()` derives a value from other signals and caches it. It re-computes only when
   * `_currentUser` changes, and anything reading `isLoggedIn()` updates along with it.
   */
  readonly isLoggedIn = computed(() => this._currentUser() !== null);
  readonly isAdmin = computed(() => this._currentUser()?.role === 'ADMIN');

  /** The raw JWT, read by the interceptor for every request. */
  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /** True if the logged-in user has one of the given roles (used by roleGuard). */
  hasAnyRole(roles: Role[]): boolean {
    const user = this._currentUser();
    return !!user && roles.includes(user.role);
  }

  /** POST /api/auth/login, then remember the session. Returns the Observable so callers can react. */
  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password })
      // tap() runs a side effect (saving the session) without changing the emitted value.
      .pipe(tap((res) => this.storeSession(res)));
  }

  /** POST /api/auth/register. The backend always creates a USER and logs them in. */
  register(name: string, email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', { name, email, password })
      .pipe(tap((res) => this.storeSession(res)));
  }

  /**
   * Clears the session and goes to the login page. JWTs are stateless, so there is nothing to
   * tell the server: the token simply stops being sent (it still expires on its own after 24 h).
   */
  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._currentUser.set(null);
    this.router.navigate(['/login']);
  }

  /** Where to send a user right after login: admins to their dashboard, users to the rooms. */
  homeUrl(): string {
    return this.isAdmin() ? '/admin/dashboard' : '/rooms';
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this._currentUser.set(res.user);
  }

  /** Restores the user after a page refresh. Corrupt or partial data counts as logged out. */
  private readStoredUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw || !localStorage.getItem(TOKEN_KEY)) {
      return null;
    }
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }
}
