import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { Header } from './components/header/header';
import { NavDrawer } from './components/nav-drawer/nav-drawer';

@Component({
  imports: [RouterOutlet, MatSidenavModule, Header, NavDrawer],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {}
