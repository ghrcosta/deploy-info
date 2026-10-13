import { Component, ChangeDetectionStrategy } from '@angular/core';
import { DeployNavigatorComponent } from "./deploy-navigator/deploy-navigator.component";
import { FileViewerComponent } from './file-viewer/file-viewer.component';

@Component({
  selector: 'deploy-viewer',
  imports: [
    DeployNavigatorComponent,
    FileViewerComponent,
  ],
  templateUrl: './deploy-viewer.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './deploy-viewer.component.scss'
})
export class DeployViewerComponent { }
