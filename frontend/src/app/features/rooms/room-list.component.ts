import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime } from 'rxjs';

import { RoomService } from '../../core/api/room.service';
import { Room } from '../../core/models';
import { errorMessage } from '../../shared/error-message';

/**
 * "Rooms" page: a card grid of all active rooms with two filters:
 *   - minimum capacity (number input, debounced),
 *   - amenities (toggle chips; a room must have ALL selected ones).
 *
 * Filtering happens on the SERVER (GET /api/rooms?minCapacity=&amenity=...), the same query an
 * API client would use - the page is just one consumer of the API.
 */
@Component({
  selector: 'app-room-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './room-list.component.html',
  styleUrl: './room-list.component.scss',
})
export class RoomListComponent implements OnInit {
  private readonly api = inject(RoomService);
  /** DestroyRef lets takeUntilDestroyed() unsubscribe automatically when the page is left. */
  private readonly destroyRef = inject(DestroyRef);

  readonly minCapacity = new FormControl<number | null>(null);
  readonly allAmenities = signal<string[]>([]);
  /** Selected amenity chips. A new array is set on every toggle so the signal notices the change. */
  readonly selectedAmenities = signal<string[]>([]);

  readonly rooms = signal<Room[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.amenities().subscribe({ next: (a) => this.allAmenities.set(a) });
    // Wait until the user stops typing for 300 ms before hitting the API.
    this.minCapacity.valueChanges
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list({ minCapacity: this.minCapacity.value, amenities: this.selectedAmenities(), activeOnly: true })
      .subscribe({
        next: (rooms) => {
          this.rooms.set(rooms);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(errorMessage(err));
          this.loading.set(false);
        },
      });
  }

  isSelected(amenity: string): boolean {
    return this.selectedAmenities().includes(amenity);
  }

  /** signal.update() derives the next value from the current one. */
  toggleAmenity(amenity: string): void {
    this.selectedAmenities.update((list) =>
      list.includes(amenity) ? list.filter((a) => a !== amenity) : [...list, amenity],
    );
    this.load();
  }

  clearFilters(): void {
    this.minCapacity.setValue(null, { emitEvent: false });   // avoid a second, debounced reload
    this.selectedAmenities.set([]);
    this.load();
  }
}
