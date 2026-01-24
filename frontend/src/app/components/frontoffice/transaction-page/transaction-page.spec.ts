import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TransactionPage } from './transaction-page';

describe('TransactionPage', () => {
  let component: TransactionPage;
  let fixture: ComponentFixture<TransactionPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionPage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TransactionPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
