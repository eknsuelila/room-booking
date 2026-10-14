import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { BookingService } from '../../core/api/booking.service';
import { Booking } from '../../core/models';
import { errorMessage } from '../../shared/error-message';
import { TimeRangePipe } from '../../shared/time-range.pipe';

type Scope = 'upcoming' | 'past';

/**
 * "My bookings": two tabs (Upcoming / Past & cancelled). Upcoming bookings can be rescheduled
 * (link to the form) or cancelled with an INLINE confirmation - no window.confirm() popup, which
 * is ugly, blocks the page and cannot be styled or tested easily.
 */
@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [RouterLink, DatePipe, TimeRangePipe],
  templateUrl: './my-bookings.component.html',
  styleUrl: './my-bookings.component.scss',
})
export class MyBookingsComponent implements OnInit {
  private readonly api = inject(BookingService);

  /** ?saved=<id> after the booking form redirects here, to show a success banner. */
  @Input() saved?: string;

  readonly scope = signal<Scope>('upcoming');
  readonly bookings = signal<Booking[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** Id of the booking whose "Cancel?" confirmation is open, or null. */
  readonly confirmingId = signal<number | null>(null);
  readonly busyId = signal<number | null>(null);

  ngOnInit(): void {
    this.load();
  }

  setScope(scope: Scope): void {
    if (scope === this.scope()) return;
    this.scope.set(scope);
    this.confirmingId.set(null);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.mine(this.scope()).subscribe({
      next: (list) => {
        this.bookings.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  cancel(booking: Booking): void {
    this.busyId.set(booking.id);
    this.api.cancel(booking.id).subscribe({
      next: () => {
        // Remove it from the Upcoming list right away (it now belongs to "Past & cancelled").
        this.bookings.update((list) => list.filter((b) => b.id !== booking.id));
        this.confirmingId.set(null);
        this.busyId.set(null);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.busyId.set(null);
      },
    });
  }

  /** Bookings already in progress cannot be changed (the server would answer 400 anyway). */
  hasStarted(b: Booking): boolean {
    // Compare as LOCAL wall-clock time: "2026-10-15T10:00:00" without a Z is parsed as local time.
    return new Date(b.startTime).getTime() <= Date.now();
  }
}
