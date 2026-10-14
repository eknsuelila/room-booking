import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth.guard';

/**
 * URL -> page mapping.
 *
 * `loadComponent` LAZY-LOADS each page: its code goes into a separate JavaScript chunk that is
 * downloaded only when the user first visits that URL. The initial download stays small, and
 * regular users never even download the admin pages.
 *
 * Guards (see core/auth/auth.guard.ts):
 *   guestGuard  - only for logged-out visitors (login/register)
 *   authGuard   - any logged-in user
 *   roleGuard   - logged-in AND has one of `data.roles`
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'rooms' },

  // ---- Public pages ----
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },

  // ---- Any logged-in user ----
  {
    path: 'rooms',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/room-list.component').then((m) => m.RoomListComponent),
  },
  {
    // ":id" becomes the `id` @Input of RoomDetailComponent; "?date=" becomes its `date` input.
    path: 'rooms/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/rooms/room-detail.component').then((m) => m.RoomDetailComponent),
  },
  {
    path: 'find',
    canActivate: [authGuard],
    loadComponent: () => import('./features/find-room/find-room.component').then((m) => m.FindRoomComponent),
  },
  {
    // Query params (?roomId=&date=&start=&end=&attendees=) prefill the form.
    path: 'bookings/new',
    canActivate: [authGuard],
    loadComponent: () => import('./features/bookings/booking-form.component').then((m) => m.BookingFormComponent),
  },
  {
    path: 'bookings/:id/edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/bookings/booking-form.component').then((m) => m.BookingFormComponent),
  },
  {
    path: 'my-bookings',
    canActivate: [authGuard],
    loadComponent: () => import('./features/bookings/my-bookings.component').then((m) => m.MyBookingsComponent),
  },

  // ---- Admins only: one parent route guards all children ----
  {
    path: 'admin',
    canActivate: [roleGuard],
    data: { roles: ['ADMIN'] },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/admin/admin-dashboard.component').then((m) => m.AdminDashboardComponent),
      },
      {
        path: 'rooms',
        loadComponent: () => import('./features/admin/manage-rooms.component').then((m) => m.ManageRoomsComponent),
      },
      {
        path: 'bookings',
        loadComponent: () => import('./features/admin/all-bookings.component').then((m) => m.AllBookingsComponent),
      },
    ],
  },

  // Unknown URLs go to the rooms page (which itself redirects to /login if needed).
  { path: '**', redirectTo: 'rooms' },
];
