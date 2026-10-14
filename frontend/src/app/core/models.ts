/**
 * TypeScript interfaces that mirror the JSON exchanged with the Spring Boot API.
 *
 * Keeping them in one file means that if a backend DTO changes, the compiler points at every
 * frontend file that needs updating. Interfaces have zero runtime cost: they are erased when
 * TypeScript compiles to JavaScript.
 *
 * DATE/TIME CONVENTION: booking times are "office wall-clock" LocalDateTime strings WITHOUT a
 * time zone, e.g. "2026-10-15T10:00:00". We deliberately keep them as strings and slice them
 * ("2026-10-15" / "10:00") instead of turning them into JavaScript Date objects, because `new Date()`
 * would apply the browser's time zone and could shift the hour. See shared/time.ts.
 */

/** Must match the Java enum `Role`. */
export type Role = 'USER' | 'ADMIN';

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
}

/** Response of POST /api/auth/login and /api/auth/register. */
export interface AuthResponse {
  token: string;
  user: User;
}

/** A meeting room (Java RoomResponse). */
export interface Room {
  id: number;
  name: string;
  location: string;
  capacity: number;
  amenities: string[];
  description: string | null;
  active: boolean;
}

/** Body for creating/updating a room (admin). */
export interface RoomRequest {
  name: string;
  location: string;
  capacity: number;
  amenities: string[];
  description: string | null;
}

/**
 * One booked block on a room's day timeline (Java ScheduleEntryResponse).
 * `title` is "Booked" and `bookedBy` null unless the viewer owns it or is an admin.
 */
export interface ScheduleEntry {
  bookingId: number;
  startTime: string;   // "2026-10-15T10:00:00"
  endTime: string;
  title: string;
  bookedBy: string | null;
  mine: boolean;
}

/** Must match the Java enum `BookingStatus`. */
export type BookingStatus = 'CONFIRMED' | 'CANCELLED';

/** A booking (Java BookingResponse). */
export interface Booking {
  id: number;
  roomId: number;
  roomName: string;
  roomLocation: string;
  userId: number;
  userName: string;
  title: string;
  description: string | null;
  startTime: string;
  endTime: string;
  attendees: number;
  status: BookingStatus;
  createdAt: string;   // ISO instant, e.g. "2026-10-14T09:12:44.123Z"
}

/** Body for creating or rescheduling a booking. */
export interface BookingRequest {
  roomId: number;
  title: string;
  description: string | null;
  startTime: string;
  endTime: string;
  attendees: number;
}

/** Generic page of results; mirrors the Java `PageResponse<T>` record. */
export interface Page<T> {
  content: T[];
  page: number;          // zero-based page index
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RoomUtilization {
  roomId: number;
  roomName: string;
  active: boolean;
  bookedMinutes: number;
  utilizationPercent: number;
}

export interface HourCount {
  hour: number;   // 8 = the 08:00-09:00 hour
  count: number;
}

/** Response of GET /api/stats (admin). */
export interface Stats {
  weekStart: string;
  weekEnd: string;
  bookingsToday: number;
  bookingsThisWeek: number;
  cancelledThisWeek: number;
  activeRooms: number;
  averageUtilization: number;
  roomUtilization: RoomUtilization[];
  busiestHours: HourCount[];
}

/** Shape of every error body produced by the backend's GlobalExceptionHandler. */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors: Record<string, string>;
}
