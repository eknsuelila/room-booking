import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';

import { BookingService } from '../../core/api/booking.service';
import { RoomService } from '../../core/api/room.service';
import { Booking, BookingRequest, Room } from '../../core/models';
import { errorMessage, isConflict } from '../../shared/error-message';
import {
  datePart, defaultSlot, durationLabel, timeOptions, timePart, toLocalDateTime, toMinutes, todayIso,
} from '../../shared/time';

/**
 * Cross-field validator: end must be after start. Attached to the whole FormGroup because it
 * needs two controls at once. Returning null means "valid"; an object means "invalid" and its
 * key ("endBeforeStart") can be checked in the template.
 */
function endAfterStart(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startTime')?.value as string;
  const end = group.get('endTime')?.value as string;
  return start && end && toMinutes(end) <= toMinutes(start) ? { endBeforeStart: true } : null;
}

/**
 * One form for both CREATING (/bookings/new?roomId=&date=&start=&end=&attendees=) and
 * RESCHEDULING (/bookings/:id/edit) a booking.
 *
 * Client-side checks (required fields, end after start, 15-minute grid via the <select> options,
 * capacity hint) give instant feedback, but the SERVER is the source of truth: it re-checks every
 * rule and, crucially, the overlap with other bookings. When it answers 409 Conflict we show an
 * amber "slot taken" panel with shortcuts to alternatives instead of a generic red error.
 */
@Component({
  selector: 'app-booking-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './booking-form.component.html',
  styleUrl: './booking-form.component.scss',
})
export class BookingFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly bookings = inject(BookingService);
  private readonly roomsApi = inject(RoomService);
  private readonly router = inject(Router);

  // ---- Inputs bound from the URL (withComponentInputBinding in app.config.ts) ----
  /** Path param of /bookings/:id/edit. Present = reschedule mode. */
  @Input() id?: string;
  /** Query params used to prefill a new booking (from the timeline or "Find a room"). */
  @Input() roomId?: string;
  @Input() date?: string;
  @Input() start?: string;
  @Input() end?: string;
  @Input() attendees?: string;

  readonly times = timeOptions();
  readonly minDate = todayIso();

  readonly form = this.fb.group(
    {
      roomId: this.fb.control<number | null>(null, Validators.required),
      title: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(150)]),
      description: this.fb.nonNullable.control('', Validators.maxLength(1000)),
      date: this.fb.nonNullable.control('', Validators.required),
      startTime: this.fb.nonNullable.control('09:00', Validators.required),
      endTime: this.fb.nonNullable.control('10:00', Validators.required),
      attendees: this.fb.nonNullable.control(1, [Validators.required, Validators.min(1)]),
    },
    { validators: endAfterStart },
  );

  readonly rooms = signal<Room[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  /** Message of a 409 response, shown in the special "slot taken" panel. */
  readonly conflict = signal<string | null>(null);
  readonly original = signal<Booking | null>(null);

  /**
   * toSignal() turns an Observable into a signal. form.valueChanges is an Observable, so this
   * gives us a signal of the latest form value that computed() values below can depend on.
   */
  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** The room currently selected in the dropdown (for the capacity hint). */
  readonly selectedRoom = computed(() => this.rooms().find((r) => r.id === Number(this.formValue().roomId)) ?? null);

  readonly overCapacity = computed(() => {
    const room = this.selectedRoom();
    return !!room && (this.formValue().attendees ?? 0) > room.capacity;
  });

  readonly duration = computed(() => {
    const v = this.formValue();
    return v.startTime && v.endTime && toMinutes(v.endTime) > toMinutes(v.startTime)
      ? durationLabel(v.startTime, v.endTime)
      : '';
  });

  get isEdit(): boolean {
    return !!this.id;
  }

  ngOnInit(): void {
    // Only active rooms can be booked, so only they are offered.
    this.roomsApi.list({ activeOnly: true }).subscribe({
      next: (rooms) => this.rooms.set(rooms),
      error: (err) => this.error.set(errorMessage(err)),
    });

    if (this.id) {
      this.loadExisting(Number(this.id));
    } else {
      // Prefill from query params, falling back to a sensible default slot.
      const fallback = defaultSlot();
      this.form.patchValue({
        roomId: this.roomId ? Number(this.roomId) : null,
        date: this.date ?? fallback.date,
        startTime: this.start ?? fallback.start,
        endTime: this.end ?? fallback.end,
        attendees: this.attendees ? Number(this.attendees) : 1,
      });
    }
  }

  private loadExisting(id: number): void {
    this.loading.set(true);
    this.bookings.get(id).subscribe({
      next: (b) => {
        this.original.set(b);
        this.form.patchValue({
          roomId: b.roomId,
          title: b.title,
          description: b.description ?? '',
          date: datePart(b.startTime),
          startTime: timePart(b.startTime),
          endTime: timePart(b.endTime),
          attendees: b.attendees,
        });
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Could not load this booking'));
        this.loading.set(false);
      },
    });
  }

  showError(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  /** Query params for the "find another room at this time" shortcut in the conflict panel. */
  findParams() {
    const v = this.form.getRawValue();
    return { date: v.date, start: v.startTime, end: v.endTime, attendees: v.attendees };
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.conflict.set(null);

    const v = this.form.getRawValue();
    // Glue date + times into the API's LocalDateTime format ("2026-10-15T10:00:00").
    const body: BookingRequest = {
      roomId: Number(v.roomId),
      title: v.title,
      description: v.description || null,
      startTime: toLocalDateTime(v.date, v.startTime),
      endTime: toLocalDateTime(v.date, v.endTime),
      attendees: v.attendees,
    };
    const request$ = this.id ? this.bookings.reschedule(Number(this.id), body) : this.bookings.create(body);

    request$.subscribe({
      next: (saved) => this.router.navigate(['/my-bookings'], { queryParams: { saved: saved.id } }),
      error: (err) => {
        // 409 = someone else holds the slot: special, friendlier panel. Anything else: red alert.
        if (isConflict(err)) {
          this.conflict.set(errorMessage(err));
        } else {
          this.error.set(errorMessage(err, 'Could not save the booking'));
        }
        this.saving.set(false);
      },
    });
  }
}
