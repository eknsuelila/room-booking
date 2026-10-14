import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { RoomService } from '../../core/api/room.service';
import { Room } from '../../core/models';
import { errorMessage } from '../../shared/error-message';
import { defaultSlot, durationLabel, timeOptions, toLocalDateTime, toMinutes, todayIso } from '../../shared/time';

/** Same cross-field rule as the booking form: end after start. */
function endAfterStart(group: AbstractControl): ValidationErrors | null {
  const start = group.get('start')?.value as string;
  const end = group.get('end')?.value as string;
  return start && end && toMinutes(end) <= toMinutes(start) ? { endBeforeStart: true } : null;
}

/**
 * "Find a room": choose a date, time range and head-count, get the list of rooms that are free
 * for the WHOLE slot (GET /api/rooms/available), then book one with a single click.
 *
 * Can be prefilled from query params (?date=&start=&end=&attendees=), which the booking form's
 * conflict panel uses ("find another room at this time"). In that case the search runs at once.
 */
@Component({
  selector: 'app-find-room',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './find-room.component.html',
  styleUrl: './find-room.component.scss',
})
export class FindRoomComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(RoomService);

  @Input() date?: string;
  @Input() start?: string;
  @Input() end?: string;
  @Input() attendees?: string;

  readonly times = timeOptions();
  readonly minDate = todayIso();

  readonly form = this.fb.nonNullable.group(
    {
      date: ['', Validators.required],
      start: ['09:00', Validators.required],
      end: ['10:00', Validators.required],
      attendees: [2, [Validators.required, Validators.min(1)]],
    },
    { validators: endAfterStart },
  );

  /** null = no search yet (shows the intro text instead of "no rooms"). */
  readonly results = signal<Room[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** The slot the current results are for (the form may be edited after searching). */
  readonly searched = signal<{ date: string; start: string; end: string; attendees: number } | null>(null);

  ngOnInit(): void {
    const fallback = defaultSlot();
    this.form.setValue({
      date: this.date ?? fallback.date,
      start: this.start ?? fallback.start,
      end: this.end ?? fallback.end,
      attendees: this.attendees ? Number(this.attendees) : 2,
    });
    if (this.date) {
      this.search();   // arrived with a prefilled slot: show results immediately
    }
  }

  search(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.api.available(toLocalDateTime(v.date, v.start), toLocalDateTime(v.date, v.end), v.attendees).subscribe({
      next: (rooms) => {
        this.results.set(rooms);
        this.searched.set(v);
        this.loading.set(false);
      },
      error: (err) => {
        // e.g. 400 "Bookings must be within business hours" - the same rules as booking.
        this.error.set(errorMessage(err));
        this.results.set(null);
        this.loading.set(false);
      },
    });
  }

  duration(): string {
    const s = this.searched();
    return s ? durationLabel(s.start, s.end) : '';
  }

  /** Query params that prefill the booking form for a chosen room. */
  bookParams(room: Room) {
    const s = this.searched()!;
    return { roomId: room.id, date: s.date, start: s.start, end: s.end, attendees: s.attendees };
  }
}
