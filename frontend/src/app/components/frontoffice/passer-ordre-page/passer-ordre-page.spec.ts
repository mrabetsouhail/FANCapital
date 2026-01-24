import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PasserOrdrePage } from './passer-ordre-page';

describe('PasserOrdrePage', () => {
  let component: PasserOrdrePage;
  let fixture: ComponentFixture<PasserOrdrePage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PasserOrdrePage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PasserOrdrePage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
