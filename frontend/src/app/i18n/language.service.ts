import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';

export type AppLang = 'fr' | 'en' | 'ar';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly doc = inject(DOCUMENT);

  private readonly STORAGE_KEY = 'appLang';
  private readonly supported: AppLang[] = ['fr', 'en', 'ar'];

  init() {
    // Register supported languages
    this.translate.addLangs(this.supported);
    this.translate.setDefaultLang('fr');

    const saved = (localStorage.getItem(this.STORAGE_KEY) ?? '').toLowerCase();
    const browser = (this.translate.getBrowserLang() ?? '').toLowerCase();
    const lang = this.normalizeLang(saved) ?? this.normalizeLang(browser) ?? 'fr';

    this.use(lang);
  }

  get current(): AppLang {
    const l = (this.translate.currentLang ?? this.translate.defaultLang ?? 'fr') as AppLang;
    return this.normalizeLang(l) ?? 'fr';
  }

  use(lang: AppLang) {
    const normalized = this.normalizeLang(lang) ?? 'fr';
    this.translate.use(normalized);
    localStorage.setItem(this.STORAGE_KEY, normalized);

    const dir: 'ltr' | 'rtl' = normalized === 'ar' ? 'rtl' : 'ltr';
    this.doc.documentElement.setAttribute('lang', normalized);
    this.doc.documentElement.setAttribute('dir', dir);
    this.doc.body.classList.toggle('rtl', dir === 'rtl');
  }

  private normalizeLang(v: string | null | undefined): AppLang | null {
    if (!v) return null;
    const x = v.trim().toLowerCase();
    if (x.startsWith('fr')) return 'fr';
    if (x.startsWith('en')) return 'en';
    if (x.startsWith('ar')) return 'ar';
    return null;
  }
}

