import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SigninPage } from './signin-page';

describe('SigninPage', () => {
  let component: SigninPage;
  let fixture: ComponentFixture<SigninPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SigninPage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SigninPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
