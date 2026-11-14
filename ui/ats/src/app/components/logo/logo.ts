import { Component, input } from '@angular/core';

@Component({
  selector: 'app-logo',
  imports: [],
  templateUrl: './logo.html',
  styleUrl: './logo.scss',
})
export class Logo {
  // Whether to show the stacked text wordmark next to the badge
  readonly showWordmark = input<boolean>(true);
}
