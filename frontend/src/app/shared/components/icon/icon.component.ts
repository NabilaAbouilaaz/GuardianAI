import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

export type IconName =
  | 'dashboard' | 'scan' | 'alert' | 'history'
  | 'users' | 'settings' | 'shield' | 'upload';

/**
 * <gd-icon name="dashboard" [active]="true"></gd-icon>
 *
 * Regroupe toutes les icônes SVG en un seul composant : pour ajouter une
 * icône, il suffit d'ajouter une entrée dans ICONS ci-dessous, sans créer
 * un nouveau fichier de composant.
 *
 * Le SVG est généré en interne (jamais depuis une entrée utilisateur), donc
 * on le marque explicitement comme sûr pour éviter que le sanitizer Angular
 * ne supprime les attributs SVG (stroke, viewBox, etc.).
 */
@Component({
  selector: 'gd-icon',
  standalone: true,
  imports: [CommonModule],
  template: `<span class="inline-flex" [innerHTML]="svg"></span>`,
})
export class IconComponent {
  @Input() name: IconName = 'dashboard';
  @Input() active = false;

  constructor(private readonly sanitizer: DomSanitizer) {}

  get svg(): SafeHtml {
    const c = this.active ? '#00FF88' : '#5A7A9A';
    const raw = ICONS[this.name]?.(c) ?? '';
    return this.sanitizer.bypassSecurityTrustHtml(raw);
  }
}

const ICONS: Record<IconName, (c: string) => string> = {
  dashboard: (c) => `<svg width="18" height="18" viewBox="0 0 24 24" fill="none">
    <rect x="3" y="3" width="7" height="7" rx="1" stroke="${c}" stroke-width="1.5"/>
    <rect x="14" y="3" width="7" height="7" rx="1" stroke="${c}" stroke-width="1.5"/>
    <rect x="3" y="14" width="7" height="7" rx="1" stroke="${c}" stroke-width="1.5"/>
    <rect x="14" y="14" width="7" height="7" rx="1" stroke="${c}" stroke-width="1.5"/>
  </svg>`,
  scan: (c) => `<svg width="18" height="18" viewBox="0 0 24 24" fill="none">
    <path d="M4 8V5a1 1 0 0 1 1-1h3M20 8V5a1 1 0 0 0-1-1h-3M4 16v3a1 1 0 0 0 1 1h3M20 16v3a1 1 0 0 1-1 1h-3" stroke="${c}" stroke-width="1.5" stroke-linecap="round"/>
    <circle cx="12" cy="12" r="3" stroke="${c}" stroke-width="1.5"/>
  </svg>`,
  alert: (c) => `<svg width="18" height="18" viewBox="0 0 24 24" fill="none">
    <path d="M12 3 2 20h20L12 3z" stroke="${c}" stroke-width="1.5" stroke-linejoin="round"/>
    <path d="M12 10v4" stroke="${c}" stroke-width="1.5" stroke-linecap="round"/>
    <circle cx="12" cy="17" r="0.8" fill="${c}"/>
  </svg>`,
  history: (c) => `<svg width="18" height="18" viewBox="0 0 24 24" fill="none">
    <circle cx="12" cy="12" r="9" stroke="${c}" stroke-width="1.5"/>
    <path d="M12 7v5l3 3" stroke="${c}" stroke-width="1.5" stroke-linecap="round"/>
  </svg>`,
  users: (c) => `<svg width="18" height="18" viewBox="0 0 24 24" fill="none">
    <circle cx="9" cy="8" r="3" stroke="${c}" stroke-width="1.5"/>
    <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" stroke="${c}" stroke-width="1.5"/>
    <circle cx="17" cy="9" r="2.3" stroke="${c}" stroke-width="1.5"/>
    <path d="M15.5 14c2.7.3 4.8 2.6 4.8 6" stroke="${c}" stroke-width="1.5"/>
  </svg>`,
  settings: (c) => `<svg width="18" height="18" viewBox="0 0 24 24" fill="none">
    <circle cx="12" cy="12" r="3" stroke="${c}" stroke-width="1.5"/>
    <path d="M19 12a7 7 0 0 0-.1-1.1l2-1.5-2-3.4-2.3.9a7 7 0 0 0-1.9-1.1L14.3 3h-4.6l-.4 2.8a7 7 0 0 0-1.9 1.1l-2.3-.9-2 3.4 2 1.5A7 7 0 0 0 5 12c0 .4 0 .7.1 1.1l-2 1.5 2 3.4 2.3-.9c.6.5 1.2.8 1.9 1.1l.4 2.8h4.6l.4-2.8a7 7 0 0 0 1.9-1.1l2.3.9 2-3.4-2-1.5c.1-.4.1-.7.1-1.1z" stroke="${c}" stroke-width="1.2"/>
  </svg>`,
  shield: (c) => `<svg width="20" height="20" viewBox="0 0 24 24" fill="none">
    <path d="M12 3 4 6v6c0 5 3.4 8.7 8 9 4.6-.3 8-4 8-9V6l-8-3z" stroke="${c}" stroke-width="1.5" stroke-linejoin="round"/>
  </svg>`,
  upload: (c) => `<svg width="28" height="28" viewBox="0 0 24 24" fill="none">
    <path d="M12 16V4M7 9l5-5 5 5M4 20h16" stroke="${c}" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`,
};
