import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreditPage } from './credit-page';

describe('CreditPage', () => {
  let component: CreditPage;
  let fixture: ComponentFixture<CreditPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreditPage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CreditPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
