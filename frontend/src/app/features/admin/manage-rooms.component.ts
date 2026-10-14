import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { RoomService } from '../../core/api/room.service';
import { Room, RoomRequest } from '../../core/models';
import { errorMessage } from '../../shared/error-message';

/**
 * Admin: manage rooms. A table of ALL rooms (including inactive ones) plus one form card used for
 * both "New room" and "Edit room".
 *
 * Deleting is only possible for rooms that were never booked; otherwise the API answers 409 and
 * the message ("...Deactivate it instead.") is shown. Deactivate/activate is the normal way to
 * take a room out of service while keeping its booking history.
 */
@Component({
  selector: 'app-manage-rooms',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './manage-rooms.component.html',
  styleUrl: './manage-rooms.component.scss',
})
export class ManageRoomsComponent implements OnInit {
  private readonly api = inject(RoomService);
  private readonly fb = inject(FormBuilder);

  readonly rooms = signal<Room[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  /** 'closed' = no form shown; 'new' = creating; a number = editing that room id. */
  readonly editing = signal<'closed' | 'new' | number>('closed');
  readonly saving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly confirmingDeleteId = signal<number | null>(null);

  /** Amenities are edited as one comma-separated text field ("Projector, Whiteboard"). */
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    location: ['', [Validators.required, Validators.maxLength(150)]],
    capacity: [4, [Validators.required, Validators.min(1), Validators.max(500)]],
    amenities: [''],
    description: ['', Validators.maxLength(1000)],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    // activeOnly=false: admins need to see (and re-activate) inactive rooms too.
    this.api.list({ activeOnly: false }).subscribe({
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

  openNew(): void {
    this.form.reset();   // nonNullable controls reset to their initial values
    this.formError.set(null);
    this.editing.set('new');
  }

  openEdit(room: Room): void {
    this.form.setValue({
      name: room.name,
      location: room.location,
      capacity: room.capacity,
      amenities: room.amenities.join(', '),
      description: room.description ?? '',
    });
    this.formError.set(null);
    this.editing.set(room.id);
  }

  closeForm(): void {
    this.editing.set('closed');
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: RoomRequest = {
      name: v.name,
      location: v.location,
      capacity: v.capacity,
      // "Projector, , Whiteboard " -> ["Projector", "Whiteboard"]
      amenities: v.amenities.split(',').map((a) => a.trim()).filter((a) => a.length > 0),
      description: v.description || null,
    };
    const editing = this.editing();
    const request$ = typeof editing === 'number' ? this.api.update(editing, body) : this.api.create(body);

    this.saving.set(true);
    this.formError.set(null);
    request$.subscribe({
      next: (room) => {
        this.saving.set(false);
        this.editing.set('closed');
        this.notice.set(`Saved "${room.name}".`);
        this.load();
      },
      error: (err) => {
        this.formError.set(errorMessage(err, 'Could not save the room'));   // e.g. 409 duplicate name
        this.saving.set(false);
      },
    });
  }

  toggleActive(room: Room): void {
    this.error.set(null);
    this.api.setActive(room.id, !room.active).subscribe({
      // Replace just that row with the server's response.
      next: (updated) => this.rooms.update((list) => list.map((r) => (r.id === updated.id ? updated : r))),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  delete(room: Room): void {
    this.error.set(null);
    this.api.delete(room.id).subscribe({
      next: () => {
        this.confirmingDeleteId.set(null);
        this.notice.set(`Deleted "${room.name}".`);
        this.rooms.update((list) => list.filter((r) => r.id !== room.id));
      },
      error: (err) => {
        this.confirmingDeleteId.set(null);
        this.error.set(errorMessage(err));   // 409: has bookings -> deactivate instead
      },
    });
  }

  showError(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.invalid && c.touched;
  }
}
