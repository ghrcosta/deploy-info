import { ChangeDetectionStrategy, Component, ElementRef, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { HomeService } from '../../home/home.service';
import { DeployTreeNetworkService } from '../deploy-tree.network.service';
import { DeployViewerService, DeploySelection } from '../deploy-viewer.service';
import { GroupEntry } from '../tree-model';
import { timeAgo } from '../time-ago';

interface GroupNode {
    name: string;
    projects: ProjectNode[];
}
interface ProjectNode {
    projectId: string;
    services: ServiceNode[];
}
interface ServiceNode {
    name: string;
    type: string;
    versions: VersionNode[];
}
interface VersionNode {
    id: string;
    name: string;
    /** Epoch ms of the deploy, shown as the navigator's relative timestamp. */
    deployTimestamp: number;
    /** The file-viewer selection this node represents. */
    selection: DeploySelection;
}

/**
 * Navigator: a filter box above a group → project → service → version tree of
 * native `<details>` elements. Screen design: documentation/frontend/main-screen.md.
 */
@Component({
    selector: 'deploy-navigator',
    imports: [MatIconModule],
    templateUrl: './deploy-navigator.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './deploy-navigator.component.scss',
})
export class DeployNavigatorComponent {
    private treeNetworkService = inject(DeployTreeNetworkService);
    private deployViewerService = inject(DeployViewerService);
    private homeService = inject(HomeService);
    private document = inject(DOCUMENT);

    constructor(private elementRef: ElementRef) {}

    groupList: GroupNode[] = [];

    /** Id of the currently selected version node; drives the `.selected` state. */
    selectedVersionId: string | null = null;

    /** Relative timestamp formatter shared with the file viewer. */
    timeAgo = timeAgo;

    ngOnInit() {
        this.treeNetworkService.getTree().subscribe(entries => {
            this.groupList = this.convertDataToNodes(entries);
        });
    }

    onSearchInput = (event: Event) => this.filterTree((event.target as HTMLInputElement).value);

    selectVersion = (node: VersionNode) => {
        this.selectedVersionId = node.id;
        this.deployViewerService.newDeployClickedEvent(node.selection);
        // Close the mobile navigator drawer when a deploy is picked.
        this.document.body.classList.remove('nav-open');
    };

    /** The navigator empty state's "Manage projects" button opens the Projects view. */
    openProjectsView = () => this.homeService.openView('projects');

    /**
     * Navigator filter: versions are
     * matched against their full text; services and projects stay visible when any child
     * survives or their own name matches ("self match"); groups stay visible when any
     * project survives. Hidden nodes get `data-filtered-out` (display: none in the global
     * stylesheet) and surviving services/projects are auto-opened.
     */
    filterTree(term: string) {
        const tree = this.elementRef.nativeElement;
        const t = term.trim().toLowerCase();
        const all = (selector: string, root: ParentNode = tree) =>
            Array.from(root.querySelectorAll(selector)) as HTMLElement[];

        if (!t) {
            all('[data-filtered-out]').forEach(el => el.removeAttribute('data-filtered-out'));
            all('.nav-project, .nav-service').forEach(el => { (el as HTMLDetailsElement).open = true; });
            return;
        }

        all('.nav-version').forEach(el =>
            el.toggleAttribute('data-filtered-out', !(el.textContent ?? '').toLowerCase().includes(t)));

        all('.nav-service').forEach(el => {
            const visible = all('.nav-version', el).some(version => !version.hasAttribute('data-filtered-out'));
            const selfMatch = (el.querySelector('.nav-service-name')?.textContent ?? '').toLowerCase().includes(t);
            el.toggleAttribute('data-filtered-out', !visible && !selfMatch);
            if (visible) (el as HTMLDetailsElement).open = true;
        });

        all('.nav-project').forEach(el => {
            const visible = all('.nav-service', el).some(service => !service.hasAttribute('data-filtered-out'));
            const selfMatch = (el.querySelector('.nav-project-name')?.textContent ?? '').toLowerCase().includes(t);
            el.toggleAttribute('data-filtered-out', !visible && !selfMatch);
            if (visible || selfMatch) (el as HTMLDetailsElement).open = true;
        });

        all('.nav-group').forEach(el => {
            el.toggleAttribute('data-filtered-out', !all('.nav-project', el)
                .some(project => !project.hasAttribute('data-filtered-out')));
        });
    }

    private convertDataToNodes = (sourceData: GroupEntry[]): GroupNode[] =>
        sourceData.map(group => ({
            name: group.name,
            projects: group.projects.map(project => ({
                projectId: project.projectId,
                services: project.services.map(service => ({
                    name: service.name,
                    type: service.type,
                    versions: service.versions.map(version => ({
                        id: version.id,
                        name: version.name,
                        deployTimestamp: version.timestamp,
                        selection: {
                            project: project.projectId,
                            service: service.name,
                            version: version,
                        },
                    })),
                })),
            })),
        }));
}
