import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AcceuilClientPage } from './acceuil-client-page';

describe('AcceuilClientPage', () => {
  let component: AcceuilClientPage;
  let fixture: ComponentFixture<AcceuilClientPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AcceuilClientPage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AcceuilClientPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
