import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Room, RoomRequest, ScheduleEntry } from '../models';

/** Filters for GET /api/rooms. All optional. */
export interface RoomQuery {
  minCapacity?: number | null;
  amenities?: string[];
  activeOnly?: boolean;
}

/**
 * Typed wrapper around the /api/rooms endpoints.
 *
 * Components never build URLs; they call these methods. HttpClient returns COLD Observables:
 * nothing is sent until someone subscribes. URLs are relative ("/api/..."): in development the
 * Angular dev server proxies them to http://localhost:8084 (proxy.conf.json); in Docker nginx
 * forwards them to the backend container (nginx.conf).
 */
@Injectable({ providedIn: 'root' })
export class RoomService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/rooms';

  /** GET /api/rooms?minCapacity=6&amenity=Projector&amenity=Whiteboard&activeOnly=true */
  list(query: RoomQuery = {}): Observable<Room[]> {
    // HttpParams is immutable: each set()/append() returns a NEW object, hence the reassignment.
    let params = new HttpParams();
    if (query.minCapacity) {
      params = params.set('minCapacity', query.minCapacity);
    }
    // append() (not set()) so that several amenities become repeated ?amenity= parameters.
    for (const a of query.amenities ?? []) {
      params = params.append('amenity', a);
    }
    if (query.activeOnly !== undefined) {
      params = params.set('activeOnly', query.activeOnly);
    }
    return this.http.get<Room[]>(this.baseUrl, { params });
  }

  get(id: number): Observable<Room> {
    return this.http.get<Room>(`${this.baseUrl}/${id}`);
  }

  amenities(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/amenities`);
  }

  /** Rooms free for the whole [start, end) slot with room for `attendees` people. */
  available(start: string, end: string, attendees: number): Observable<Room[]> {
    const params = new HttpParams().set('start', start).set('end', end).set('attendees', attendees);
    return this.http.get<Room[]>(`${this.baseUrl}/available`, { params });
  }

  /** Confirmed bookings of one room on one day ("YYYY-MM-DD"). */
  schedule(roomId: number, date: string): Observable<ScheduleEntry[]> {
    return this.http.get<ScheduleEntry[]>(`${this.baseUrl}/${roomId}/schedule`, { params: { date } });
  }

  // ----- admin only (the backend answers 403 for regular users) -----

  create(body: RoomRequest): Observable<Room> {
    return this.http.post<Room>(this.baseUrl, body);
  }

  update(id: number, body: RoomRequest): Observable<Room> {
    return this.http.put<Room>(`${this.baseUrl}/${id}`, body);
  }

  setActive(id: number, active: boolean): Observable<Room> {
    return this.http.post<Room>(`${this.baseUrl}/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
