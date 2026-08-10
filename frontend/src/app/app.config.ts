import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './core/auth.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // L'intercepteur joint le jeton à chaque appel et renvoie vers la page de
    // connexion en cas de 401, sans que les services aient à s'en soucier.
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
