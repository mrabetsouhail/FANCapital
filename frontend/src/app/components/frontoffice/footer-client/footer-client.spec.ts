import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FooterClient } from './footer-client';

describe('FooterClient', () => {
  let component: FooterClient;
  let fixture: ComponentFixture<FooterClient>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FooterClient]
    })
    .compileComponents();

    fixture = TestBed.createComponent(FooterClient);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
