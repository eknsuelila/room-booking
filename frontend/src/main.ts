/**
 * Browser entry point: the first TypeScript that runs.
 *
 * bootstrapApplication() starts Angular with AppComponent as the root component and the
 * application-wide providers from app.config.ts (router, HttpClient + JWT interceptor).
 * There is no NgModule: this is a "standalone" Angular app (the default since Angular 17).
 */
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
