import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Booking, BookingRequest, BookingStatus, Page } from '../models';

/** Filters for the admin list GET /api/bookings. */
export interface BookingQuery {
  date?: string;                  // "YYYY-MM-DD"
  roomId?: number | null;
  status?: BookingStatus | '';
  page?: number;
  size?: number;
}

/** Typed wrapper around the /api/bookings endpoints. */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/bookings';

  /** POST /api/bookings - 201 on success; 400 (rule broken) or 409 (slot taken) otherwise. */
  create(body: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>(this.baseUrl, body);
  }

  get(id: number): Observable<Booking> {
    return this.http.get<Booking>(`${this.baseUrl}/${id}`);
  }

  /** PUT /api/bookings/{id} - move a booking to another time and/or room. */
  reschedule(id: number, body: BookingRequest): Observable<Booking> {
    return this.http.put<Booking>(`${this.baseUrl}/${id}`, body);
  }

  cancel(id: number): Observable<Booking> {
    return this.http.post<Booking>(`${this.baseUrl}/${id}/cancel`, {});
  }

  /** The current user's bookings: 'upcoming' (soonest first) or 'past' (incl. cancelled). */
  mine(scope: 'upcoming' | 'past'): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.baseUrl}/me`, { params: { scope } });
  }

  /** Admin: all bookings, filtered and paged. Empty filter values are not sent. */
  all(query: BookingQuery = {}): Observable<Page<Booking>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<Booking>>(this.baseUrl, { params });
  }
}
