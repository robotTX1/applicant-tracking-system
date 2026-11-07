import { Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { CompanyForm, normalizeWebsite } from '../../utils/company-form';

/** The company detail fields, shared by registering a company and its settings page. */
@Component({
  selector: 'app-company-fields',
  imports: [
    ReactiveFormsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  templateUrl: './company-fields.html',
  styleUrl: './company-fields.scss',
})
export class CompanyFields {
  readonly form = input.required<CompanyForm>();
  readonly maxDate = new Date();

  normalizeWebsite(): void {
    normalizeWebsite(this.form());
  }
}
