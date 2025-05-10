import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';

@Component({
    selector: 'app-settings',
    templateUrl: './settings.component.html',
    styleUrl: './settings.component.scss',
    imports: [
        FormsModule,
        MatCardModule,
        MatButtonModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
    ],
})
export class SettingsComponent {
    projects = EXAMPLE;

    newProjectCategory='';
    newProjectServiceAccount='';

    onEditProjectCategoryClicked = (project: Project) => {
    }

    onEditProjectServiceAccountClicked = (project: Project) => {
    }

    onDeleteProjectClicked = (project: Project) => {
    }
}

interface Project {
    name: string;
    category?: string;
    service_account: string;
}
const EXAMPLE: Project[] = [
    {
        name: "Project A",
        category: "Some category",
        service_account: "project-a@serviceaccount.comdli ylsdiuv lasylfi ifgawubswçiouçufgiurçouaeroçeoçr aeoçrugvaeo urçouwg4çouerçfu grwçfg srgoçerug çoeurgeuvrvçeu gçorguv eçrou"
    },
    {
        name: "Project B",
        category: "Some other category",
        service_account: "project-b@serviceaccount.com"
    }
];