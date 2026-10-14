import { ScheduleEntry } from '../core/models';
import { CLOSING_HOUR, OPENING_HOUR, fromMinutes, minutesOfDay, toMinutes } from './time';

/**
 * Pure functions that turn a room's schedule into the DAY TIMELINE shown on the room page.
 * Kept out of the component so they can be unit tested without rendering anything
 * (see timeline.spec.ts).
 *
 * The timeline is drawn in two layers:
 *
 *   1. A GRID of 30-minute rows from 08:00 to 20:00 (24 rows). Each row knows whether it is
 *      free (clickable -> opens the booking form prefilled) or busy/past (not clickable).
 *
 *   2. BOOKED BLOCKS drawn on top of the grid with absolute positioning. Their vertical position
 *      is a percentage of the 12-hour day, so a 10:15-11:00 booking (15-minute precision) lines up
 *      exactly even though the grid rows are 30 minutes tall:
 *
 *        top    = (start - 08:00) / 720 min * 100%
 *        height = (end - start)   / 720 min * 100%
 *
 *      08:00 ┬───────────────┐  top = 0%
 *            │               │
 *      10:15 │ ┌───────────┐ │  top = 135/720 = 18.75%
 *            │ │  Booked   │ │  height = 45/720 = 6.25%
 *      11:00 │ └───────────┘ │
 *            ┊               ┊
 *      20:00 ┴───────────────┘  100%
 */

export const ROW_MINUTES = 30;
const DAY_START = OPENING_HOUR * 60;           // 480
const DAY_END = CLOSING_HOUR * 60;             // 1200
const DAY_LENGTH = DAY_END - DAY_START;        // 720

/** One 30-minute row of the grid. */
export interface TimelineSlot {
  start: string;      // "10:00"
  end: string;        // "10:30"
  /** No confirmed booking overlaps this row. */
  free: boolean;
  /** The row has already begun (only possible when viewing today). */
  past: boolean;
}

/** Absolute position of a booked block, in percent of the timeline height. */
export interface BlockPosition {
  top: number;
  height: number;
}

/**
 * Builds the 24 grid rows for a day.
 *
 * A row is busy if ANY booking overlaps it, using the same half-open interval rule as the
 * backend: booking.start < row.end && booking.end > row.start. So a 10:00-11:00 booking marks
 * the 10:00 and 10:30 rows busy, but NOT the 09:30 row (ends exactly at 10:00) nor the 11:00 row.
 * A short 10:15-10:30 booking marks the whole 10:00 row busy (a free half-hour no longer exists).
 *
 * @param entries   the day's confirmed bookings
 * @param nowMinute minutes since midnight if the day shown is TODAY, else null (no row is past)
 */
export function buildSlots(entries: ScheduleEntry[], nowMinute: number | null = null): TimelineSlot[] {
  const ranges = entries.map((e) => ({ start: minutesOfDay(e.startTime), end: minutesOfDay(e.endTime) }));
  const slots: TimelineSlot[] = [];
  for (let start = DAY_START; start < DAY_END; start += ROW_MINUTES) {
    const end = start + ROW_MINUTES;
    const free = !ranges.some((r) => r.start < end && r.end > start);
    // A row counts as past once it has started: you cannot book 10:00-10:30 at 10:10.
    const past = nowMinute !== null && start < nowMinute;
    slots.push({ start: fromMinutes(start), end: fromMinutes(end), free, past });
  }
  return slots;
}

/** Where to draw a booking block (percentages of the 08:00-20:00 day). Clamped to the visible day. */
export function blockPosition(entry: ScheduleEntry): BlockPosition {
  const start = Math.max(minutesOfDay(entry.startTime), DAY_START);
  const end = Math.min(minutesOfDay(entry.endTime), DAY_END);
  return {
    top: ((start - DAY_START) / DAY_LENGTH) * 100,
    height: (Math.max(end - start, 0) / DAY_LENGTH) * 100,
  };
}

/** Labels for the hour ticks on the left of the timeline: "08:00" ... "19:00". */
export function hourLabels(): string[] {
  const labels: string[] = [];
  for (let h = OPENING_HOUR; h < CLOSING_HOUR; h++) {
    labels.push(fromMinutes(h * 60));
  }
  return labels;
}

/** Minutes since midnight of "now" - pulled out so tests can pass a fixed Date. */
export function currentMinute(now: Date = new Date()): number {
  return toMinutes(`${now.getHours()}:${now.getMinutes()}`);
}

/**
 * When a user clicks a free row, we suggest a ONE-hour meeting if the following row is also free
 * (the most common meeting length), otherwise just the 30 minutes of the clicked row.
 */
export function suggestedEnd(slots: TimelineSlot[], index: number): string {
  const next = slots[index + 1];
  return next && next.free && !next.past ? next.end : slots[index].end;
}
