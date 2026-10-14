import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { RoomService } from '../../core/api/room.service';
import { AuthService } from '../../core/auth/auth.service';
import { Room, ScheduleEntry } from '../../core/models';
import { errorMessage } from '../../shared/error-message';
import { addDays, todayIso } from '../../shared/time';
import { TimeRangePipe } from '../../shared/time-range.pipe';
import { blockPosition, buildSlots, currentMinute, hourLabels, suggestedEnd } from '../../shared/timeline';

/**
 * Room detail page with a DAY TIMELINE (08:00-20:00).
 *
 * Data flow:
 *   1. The router passes `id` (from /rooms/:id) and optionally `date` (from ?date=) as @Inputs.
 *   2. We load the room once and the schedule for the selected day.
 *   3. `slots` and `blocks` are computed() signals derived from the schedule, so changing the
 *      day (prev/next/date picker) only needs to update `entries` - the timeline redraws itself.
 *   4. Clicking a free row navigates to the booking form with room/date/start/end prefilled.
 *
 * The geometry (which rows are free, where blocks go) lives in shared/timeline.ts as pure
 * functions with unit tests.
 */
@Component({
  selector: 'app-room-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, TimeRangePipe],
  templateUrl: './room-detail.component.html',
  styleUrl: './room-detail.component.scss',
})
export class RoomDetailComponent implements OnInit {
  private readonly api = inject(RoomService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Route param ":id" (strings: everything in a URL is text). */
  @Input() id!: string;
  /** Optional query param ?date=YYYY-MM-DD so a specific day can be linked/bookmarked. */
  @Input() date?: string;

  readonly today = todayIso();
  readonly hours = hourLabels();

  readonly room = signal<Room | null>(null);
  readonly day = signal(this.today);
  readonly entries = signal<ScheduleEntry[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /**
   * "Now" relative to the day being viewed: today -> current minute (earlier rows are past);
   * a past day -> end of day (everything past); a future day -> null (nothing past).
   */
  private readonly nowMinute = computed(() => {
    const d = this.day();
    if (d === this.today) return currentMinute();
    return d < this.today ? 24 * 60 : null;   // "YYYY-MM-DD" strings compare correctly as text
  });

  /** The 24 half-hour rows. Re-computed automatically whenever entries or day change. */
  readonly slots = computed(() => buildSlots(this.entries(), this.nowMinute()));

  /** Booked blocks with their CSS position (percent of the day). */
  readonly blocks = computed(() => this.entries().map((entry) => ({ entry, pos: blockPosition(entry) })));

  readonly isPastDay = computed(() => this.day() < this.today);

  ngOnInit(): void {
    if (this.date && /^\d{4}-\d{2}-\d{2}$/.test(this.date)) {
      this.day.set(this.date);
    }
    this.api.get(Number(this.id)).subscribe({
      next: (room) => this.room.set(room),
      error: (err) => this.error.set(errorMessage(err, 'Room not found')),
    });
    this.loadSchedule();
  }

  loadSchedule(): void {
    this.loading.set(true);
    this.api.schedule(Number(this.id), this.day()).subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  /** Prev/next day buttons and the date picker all end up here. */
  goToDay(date: string): void {
    if (!date) return;
    this.day.set(date);
    // Keep the URL in sync (?date=...) so refresh/back/share show the same day.
    // replaceUrl avoids filling the browser history with one entry per click.
    this.router.navigate([], { queryParams: { date }, replaceUrl: true });
    this.loadSchedule();
  }

  shiftDay(delta: number): void {
    this.goToDay(addDays(this.day(), delta));
  }

  /** Click on a free row -> booking form, prefilled. */
  pickSlot(index: number): void {
    const slot = this.slots()[index];
    if (!slot.free || slot.past || !this.room()?.active) return;
    this.router.navigate(['/bookings/new'], {
      queryParams: { roomId: this.id, date: this.day(), start: slot.start, end: suggestedEnd(this.slots(), index) },
    });
  }

  /** Owners and admins can open a future booking from the timeline to reschedule it. */
  canEdit(entry: ScheduleEntry): boolean {
    return (entry.mine || this.auth.isAdmin()) && !this.isPastDay();
  }
}
