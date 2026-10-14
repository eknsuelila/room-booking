/**
 * Small date/time helpers that work on PLAIN STRINGS.
 *
 * The API speaks "office wall-clock" LocalDateTime strings such as "2026-10-15T10:00:00".
 * Converting them to JavaScript Date objects would attach the browser's time zone, and
 * Date.toISOString() converts to UTC - a classic source of "my 10:00 meeting shows as 08:00"
 * bugs. So we keep the strings and only cut or glue them:
 *
 *    datePart("2026-10-15T10:00:00") -> "2026-10-15"
 *    timePart("2026-10-15T10:00:00") -> "10:00"
 *    toLocalDateTime("2026-10-15", "10:00") -> "2026-10-15T10:00:00"
 *
 * The only place a Date is used is to read the browser's CURRENT local date/time and to add
 * days to a date, both of which we do with local-time getters (getFullYear/getDate...), never UTC.
 */

/** Opening and closing time of the office, mirroring BookingTimeRules.java. */
export const OPENING_HOUR = 8;
export const CLOSING_HOUR = 20;
/** Bookings snap to this grid (minutes), mirroring BookingTimeRules.SLOT_MINUTES. */
export const SLOT_MINUTES = 15;

const pad = (n: number) => String(n).padStart(2, '0');

/** A Date's LOCAL calendar day as "YYYY-MM-DD". */
export function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Today in the browser's local time zone, "YYYY-MM-DD". */
export function todayIso(now: Date = new Date()): string {
  return toIsoDate(now);
}

/** "2026-10-15" + n days (n may be negative). Month/year roll-over handled by Date. */
export function addDays(isoDate: string, n: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return toIsoDate(new Date(y, m - 1, d + n));   // local-time constructor: no UTC shift
}

export function datePart(localDateTime: string): string {
  return localDateTime.slice(0, 10);
}

export function timePart(localDateTime: string): string {
  return localDateTime.slice(11, 16);
}

/** Joins "2026-10-15" and "10:00" into the API format "2026-10-15T10:00:00". */
export function toLocalDateTime(date: string, time: string): string {
  return `${date}T${time}:00`;
}

/** "10:30" -> 630 (minutes since midnight). */
export function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

/** 630 -> "10:30". */
export function fromMinutes(minutes: number): string {
  return `${pad(Math.floor(minutes / 60))}:${pad(minutes % 60)}`;
}

/** Minutes since midnight of a LocalDateTime string, e.g. "...T10:15:00" -> 615. */
export function minutesOfDay(localDateTime: string): number {
  return toMinutes(timePart(localDateTime));
}

/**
 * Every bookable time on the 15-minute grid, "08:00", "08:15", ... "20:00".
 * Used to fill the start/end <select>s so users cannot even pick an invalid minute.
 */
export function timeOptions(): string[] {
  const options: string[] = [];
  for (let m = OPENING_HOUR * 60; m <= CLOSING_HOUR * 60; m += SLOT_MINUTES) {
    options.push(fromMinutes(m));
  }
  return options;
}

/** Duration label for two "HH:mm" times: ("09:00", "10:30") -> "1 h 30 min". */
export function durationLabel(start: string, end: string): string {
  const total = toMinutes(end) - toMinutes(start);
  const h = Math.floor(total / 60);
  const m = total % 60;
  return [h ? `${h} h` : '', m ? `${m} min` : ''].filter(Boolean).join(' ');
}

/**
 * A sensible default slot for the "Find a room" and booking forms: the next full hour today if
 * that still fits in business hours, otherwise 09:00-10:00 tomorrow.
 */
export function defaultSlot(now: Date = new Date()): { date: string; start: string; end: string } {
  const nextHour = now.getHours() + 1;
  if (nextHour >= OPENING_HOUR && nextHour + 1 <= CLOSING_HOUR) {
    return { date: toIsoDate(now), start: fromMinutes(nextHour * 60), end: fromMinutes((nextHour + 1) * 60) };
  }
  // Before opening: today at 09:00 is fine; after closing: tomorrow.
  const date = nextHour < OPENING_HOUR ? toIsoDate(now) : addDays(toIsoDate(now), 1);
  return { date, start: '09:00', end: '10:00' };
}
