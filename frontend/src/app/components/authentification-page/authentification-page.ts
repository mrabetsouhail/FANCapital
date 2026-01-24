import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'app-authentification-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './authentification-page.html',
  styleUrl: './authentification-page.css',
})
export class AuthentificationPage {
  email: string = '';
  password: string = '';
  logoPath: string = '/fancapital_logo.jpg';
  
  constructor(private router: Router) {}
  
  
  onSubmit() {
    this.router.navigate(['/acceuil-client']);
  }
}
