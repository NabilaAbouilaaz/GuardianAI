import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './layout/sidebar/sidebar.component';

@Component({
  selector: 'gd-root',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent],
  template: `
    <div class="flex min-h-screen bg-[#070B12] text-[#E2EBF5]">
      <gd-sidebar></gd-sidebar>
      <main class="flex-1 p-8 overflow-auto">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
})
export class AppComponent {}
