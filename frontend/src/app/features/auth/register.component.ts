import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { errorMessage } from '../../shared/error-message';

/**
 * Sign-up page. Validation rules mirror the backend's RegisterRequest (@NotBlank, @Email,
 * @Size(min = 8)) for instant feedback - but the backend validates again, because anything in the
 * browser can be bypassed. New accounts are always regular USERs.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './auth-page.scss',
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { name, email, password } = this.form.getRawValue();
    // Registering also logs the user in (the API returns a token).
    this.auth.register(name, email, password).subscribe({
      next: () => this.router.navigate(['/rooms']),
      error: (err) => {
        this.error.set(errorMessage(err, 'Registration failed'));
        this.loading.set(false);
      },
    });
  }
}
