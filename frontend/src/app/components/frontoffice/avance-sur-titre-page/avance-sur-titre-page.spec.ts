import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AvanceSurTitrePage } from './avance-sur-titre-page';

describe('AvanceSurTitrePage', () => {
  let component: AvanceSurTitrePage;
  let fixture: ComponentFixture<AvanceSurTitrePage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AvanceSurTitrePage]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AvanceSurTitrePage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
