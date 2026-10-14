import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../shared/error-message';

/** Seeded demo accounts (see DemoDataSeeder.java). */
const DEMO_ACCOUNTS = {
  admin: { email: 'admin@example.com', password: 'password123' },
  user: { email: 'user@example.com', password: 'password123' },
} as const;

/**
 * Login page built with a REACTIVE FORM.
 *
 * Reactive forms are declared in TypeScript (FormBuilder below) rather than in the HTML, which
 * makes validation explicit, typed and easy to test. The template connects to them with
 * [formGroup] and formControlName.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './auth-page.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** nonNullable: reset() returns to '' instead of null, which keeps the types simple (string, not string | null). */
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  /** UI state as signals: the template re-renders automatically when they change. */
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** "Use admin demo" / "Use user demo": fills in a seeded account so reviewers can try both roles. */
  useDemo(kind: keyof typeof DEMO_ACCOUNTS): void {
    this.form.setValue({ ...DEMO_ACCOUNTS[kind] });
    this.error.set(null);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched(); // reveal validation messages on every field
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      // Admins land on their dashboard, users on the room list.
      next: () => this.router.navigateByUrl(this.auth.homeUrl()),
      error: (err) => {
        this.error.set(errorMessage(err, 'Login failed'));
        this.loading.set(false);
      },
    });
  }
}
