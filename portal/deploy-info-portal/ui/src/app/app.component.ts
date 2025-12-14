import { Component, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { MatIconRegistry } from '@angular/material/icon';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
    constructor() {
        const iconRegistry = inject(MatIconRegistry);
        const sanitizer = inject(DomSanitizer);
        iconRegistry.addSvgIcon('project', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/gcp_project.svg'));
        iconRegistry.addSvgIcon('GAE', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/gcp_gae.svg'));
        iconRegistry.addSvgIcon('RUN', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/gcp_run.svg'));
        iconRegistry.addSvgIcon('no_data', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/data_off.svg'));
        iconRegistry.addSvgIcon('group', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/group.svg'));
        iconRegistry.addSvgIcon('deployed_code', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/deployed_code.svg'));
        iconRegistry.addSvgIcon('deployed_code_alert', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/deployed_code_alert.svg'));
        iconRegistry.addSvgIcon('label', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/label.svg'));
        iconRegistry.addSvgIcon('service_account', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/key.svg'));
        iconRegistry.addSvgIcon('edit', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/edit.svg'));
        iconRegistry.addSvgIcon('delete', sanitizer.bypassSecurityTrustResourceUrl('assets/icons/delete.svg'));
    }

    title = 'ui';
}
