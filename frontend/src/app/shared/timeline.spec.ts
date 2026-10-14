import { ScheduleEntry } from '../core/models';
import { blockPosition, buildSlots, hourLabels, suggestedEnd } from './timeline';

/** Pure functions: no TestBed needed, just call them and check the result. */
describe('timeline helpers', () => {
  const entry = (start: string, end: string): ScheduleEntry => ({
    bookingId: 1,
    startTime: `2026-10-15T${start}:00`,
    endTime: `2026-10-15T${end}:00`,
    title: 'Booked',
    bookedBy: null,
    mine: false,
  });

  it('builds 24 half-hour rows from 08:00 to 20:00', () => {
    const slots = buildSlots([]);
    expect(slots.length).toBe(24);
    expect(slots[0].start).toBe('08:00');
    expect(slots[23].end).toBe('20:00');
    expect(slots.every((s) => s.free && !s.past)).toBeTrue();
  });

  it('marks overlapping rows busy using half-open intervals (touching rows stay free)', () => {
    const slots = buildSlots([entry('10:00', '11:00')]);
    const byStart = (t: string) => slots.find((s) => s.start === t)!;
    expect(byStart('09:30').free).toBeTrue();   // ends exactly at 10:00
    expect(byStart('10:00').free).toBeFalse();
    expect(byStart('10:30').free).toBeFalse();
    expect(byStart('11:00').free).toBeTrue();   // starts exactly at 11:00
  });

  it('a 15-minute booking blocks the whole half-hour row it touches', () => {
    const slots = buildSlots([entry('10:15', '10:30')]);
    expect(slots.find((s) => s.start === '10:00')!.free).toBeFalse();
    expect(slots.find((s) => s.start === '10:30')!.free).toBeTrue();
  });

  it('marks rows that have started as past', () => {
    const slots = buildSlots([], 10 * 60 + 10);   // now = 10:10
    expect(slots.find((s) => s.start === '09:30')!.past).toBeTrue();
    expect(slots.find((s) => s.start === '10:00')!.past).toBeTrue();   // already started
    expect(slots.find((s) => s.start === '10:30')!.past).toBeFalse();
  });

  it('positions blocks as a percentage of the 12-hour day', () => {
    expect(blockPosition(entry('08:00', '20:00'))).toEqual({ top: 0, height: 100 });
    const pos = blockPosition(entry('10:15', '11:00'));
    expect(pos.top).toBeCloseTo(18.75);   // 135 of 720 minutes
    expect(pos.height).toBeCloseTo(6.25); // 45 of 720 minutes
  });

  it('suggests a one-hour meeting when the next row is free', () => {
    const slots = buildSlots([entry('11:00', '12:00')]);
    const idx = (t: string) => slots.findIndex((s) => s.start === t);
    expect(suggestedEnd(slots, idx('09:00'))).toBe('10:00');
    expect(suggestedEnd(slots, idx('10:30'))).toBe('11:00');   // 11:00 row is busy
    expect(suggestedEnd(slots, idx('19:30'))).toBe('20:00');   // last row of the day
  });

  it('labels each hour', () => {
    expect(hourLabels()).toEqual(['08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00',
      '15:00', '16:00', '17:00', '18:00', '19:00']);
  });
});
