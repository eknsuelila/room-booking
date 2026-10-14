import { HttpErrorResponse } from '@angular/common/http';
import { addDays, defaultSlot, durationLabel, timeOptions, toLocalDateTime } from './time';
import { errorMessage, isConflict } from './error-message';

describe('time helpers', () => {
  it('adds days across month boundaries without time-zone drift', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('builds API date-times and 15-minute options', () => {
    expect(toLocalDateTime('2026-10-15', '10:30')).toBe('2026-10-15T10:30:00');
    const options = timeOptions();
    expect(options[0]).toBe('08:00');
    expect(options[1]).toBe('08:15');
    expect(options[options.length - 1]).toBe('20:00');
    expect(durationLabel('09:00', '10:30')).toBe('1 h 30 min');
  });

  it('defaults to the next full hour, or tomorrow morning after closing time', () => {
    expect(defaultSlot(new Date(2026, 9, 14, 10, 20))).toEqual({ date: '2026-10-14', start: '11:00', end: '12:00' });
    expect(defaultSlot(new Date(2026, 9, 14, 19, 30))).toEqual({ date: '2026-10-15', start: '09:00', end: '10:00' });
  });
});

describe('errorMessage', () => {
  it('uses the server message and recognizes conflicts', () => {
    const err = new HttpErrorResponse({ status: 409, error: { message: "'Atlas' is already booked" } });
    expect(errorMessage(err)).toBe("'Atlas' is already booked");
    expect(isConflict(err)).toBeTrue();
  });
});
