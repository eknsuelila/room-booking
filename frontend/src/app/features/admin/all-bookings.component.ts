import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { BookingService } from '../../core/api/booking.service';
import { RoomService } from '../../core/api/room.service';
import { Booking, BookingStatus, Page, Room } from '../../core/models';
import { errorMessage } from '../../shared/error-message';
import { TimeRangePipe } from '../../shared/time-range.pipe';

/**
 * Admin: every booking in the system, filtered by day, room and status, with server-side paging
 * (GET /api/bookings?date=&roomId=&status=&page=&size=). Admins can cancel any future booking.
 *
 * The filter <select>s are bound with FormControls rather than [value]: a FormControl re-applies
 * its value when the async room <option>s appear, so the chosen room never "jumps" to the first
 * option.
 */
@Component({
  selector: 'app-all-bookings',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, TimeRangePipe],
  templateUrl: './all-bookings.component.html',
  styleUrl: './all-bookings.component.scss',
})
export class AllBookingsComponent implements OnInit {
  private readonly bookingsApi = inject(BookingService);
  private readonly roomsApi = inject(RoomService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly statuses: BookingStatus[] = ['CONFIRMED', 'CANCELLED'];

  readonly filters = this.fb.nonNullable.group({
    date: [''],
    roomId: [''],                       // '' = all rooms; select values are strings
    status: ['' as BookingStatus | ''],
  });

  readonly rooms = signal<Room[]>([]);
  readonly page = signal<Page<Booking> | null>(null);
  readonly pageIndex = signal(0);
  readonly pageSize = 15;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly confirmingId = signal<number | null>(null);

  ngOnInit(): void {
    this.roomsApi.list({ activeOnly: false }).subscribe({ next: (r) => this.rooms.set(r) });
    // Any filter change -> back to page 1 and reload. takeUntilDestroyed = auto-unsubscribe.
    this.filters.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageIndex.set(0);
      this.load();
    });
    this.load();
  }

  load(): void {
    const f = this.filters.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.bookingsApi
      .all({
        date: f.date,
        roomId: f.roomId ? Number(f.roomId) : null,
        status: f.status,
        page: this.pageIndex(),
        size: this.pageSize,
      })
      .subscribe({
        next: (p) => {
          this.page.set(p);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err));
          this.loading.set(false);
        },
      });
  }

  goToPage(index: number): void {
    this.pageIndex.set(index);
    this.load();
  }

  clear(): void {
    // emitEvent: false prevents valueChanges from firing (we reload once ourselves).
    this.filters.setValue({ date: '', roomId: '', status: '' }, { emitEvent: false });
    this.pageIndex.set(0);
    this.load();
  }

  /** Only confirmed bookings that have not started can still be cancelled. */
  canCancel(b: Booking): boolean {
    return b.status === 'CONFIRMED' && new Date(b.startTime).getTime() > Date.now();
  }

  cancel(b: Booking): void {
    this.bookingsApi.cancel(b.id).subscribe({
      next: (updated) => {
        this.confirmingId.set(null);
        this.page.update((p) => (p ? { ...p, content: p.content.map((x) => (x.id === updated.id ? updated : x)) } : p));
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
