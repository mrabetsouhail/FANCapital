import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthentificationPage } from './authentification-page';

describe('AuthentificationPage', () => {
  let component: AuthentificationPage;
  let fixture: ComponentFixture<AuthentificationPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthentificationPage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthentificationPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
