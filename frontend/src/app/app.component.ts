import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

/**
 * Root component: the app "shell". Renders the role-aware navbar and a <router-outlet>, the
 * placeholder where the page for the current URL appears.
 *
 * `standalone: true` means the component declares its own dependencies in `imports` instead of
 * belonging to an NgModule.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  /** `protected` so the template can use it, while other classes cannot. */
  protected readonly auth = inject(AuthService);
}
