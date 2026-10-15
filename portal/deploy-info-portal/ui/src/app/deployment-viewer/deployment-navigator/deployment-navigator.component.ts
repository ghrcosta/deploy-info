import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { HomeService } from '../../home/home.service';
import { DeploymentViewerService, DeploymentSelection } from '../deployment-viewer.service';
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
    /** Epoch ms of the deployment, shown as the navigator's relative timestamp. */
    deploymentTimestamp: number;
    /** The file-viewer selection this node represents. */
    selection: DeploymentSelection;
}

/**
 * Navigator: a filter box above a group → project → service → version tree of
 * native `<details>` elements. The tree is fetched by the parent deployment viewer
 * and passed in. Screen design: documentation/frontend/main-screen.md.
 */
@Component({
    selector: 'deployment-navigator',
    imports: [MatIconModule],
    templateUrl: './deployment-navigator.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './deployment-navigator.component.scss',
})
export class DeploymentNavigatorComponent {
    /** Navigator tree of `GET /portal/tree` (see `tree-model.ts`), fetched by the deployment viewer. */
    tree = input.required<GroupEntry[]>();

    private deploymentViewerService = inject(DeploymentViewerService);
    private homeService = inject(HomeService);
    private document = inject(DOCUMENT);

    constructor(private elementRef: ElementRef) {}

    groupList = computed(() => this.convertDataToNodes(this.tree()));

    /** Id of the currently selected version node; drives the `.selected` state. */
    selectedVersionId: string | null = null;

    /** Relative timestamp formatter shared with the file viewer. */
    timeAgo = timeAgo;

    onSearchInput = (event: Event) => this.filterTree((event.target as HTMLInputElement).value);

    selectVersion = (node: VersionNode) => {
        this.selectedVersionId = node.id;
        this.deploymentViewerService.newDeploymentClickedEvent(node.selection);
        // Close the mobile navigator drawer when a deployment is picked.
        this.document.body.classList.remove('nav-open');
    };

    /** The navigator empty state's "Manage projects" button opens the Projects view. */
    openProjectsView = () => this.homeService.openView('projects');

    /**
     * Navigator filter by node name. A node stays visible when it, any of its
     * descendants, or an ancestor contains a match. The substring that matches
     * is wrapped in `<mark>` elements to highlight it.
     */
    filterTree(term: string) {
        const tree = this.elementRef.nativeElement;
        const t = term.trim().toLowerCase();
        const all = (selector: string) => Array.from(tree.querySelectorAll(selector)) as HTMLElement[];

        // Restore the plain names first, so matching and re-highlighting see the original text.
        this.unhighlight(all('.nav-group-label, .nav-project-name, .nav-service-name, .nav-version-name'));

        if (!t) {
            all('[data-filtered-out]').forEach(el => el.removeAttribute('data-filtered-out'));
            all('.nav-project, .nav-service').forEach(el => { (el as HTMLDetailsElement).open = true; });
            return;
        }
        const matches = (name: string) => name.toLowerCase().includes(t);

        const filterVersion = (version: HTMLElement, forced: boolean): boolean => {
            const nameEl = version.querySelector('.nav-version-name');
            const selfMatch = matches(nameEl?.textContent ?? '');
            if (selfMatch) this.highlight(nameEl, t);
            const visible = forced || selfMatch;
            version.toggleAttribute('data-filtered-out', !visible);
            return visible;
        };

        const filterService = (service: HTMLElement, forced: boolean): boolean => {
            const nameEl = service.querySelector('.nav-service-name');
            const selfMatch = matches(nameEl?.textContent ?? '');
            if (selfMatch) this.highlight(nameEl, t);
            const childForced = forced || selfMatch;
            // Walk every service/version even when forced: short-circuiting would leave
            // stale data-filtered-out attributes on the lower levels.
            const anyChildVisible = Array.from(service.querySelectorAll<HTMLElement>('.nav-version'))
                .reduce((acc, version) => filterVersion(version, childForced) || acc, false);
            const visible = childForced || anyChildVisible;
            service.toggleAttribute('data-filtered-out', !visible);
            if (visible) (service as HTMLDetailsElement).open = true;
            return visible;
        };

        const filterProject = (project: HTMLElement, forced: boolean): boolean => {
            const nameEl = project.querySelector('.nav-project-name');
            const selfMatch = matches(nameEl?.textContent ?? '');
            if (selfMatch) this.highlight(nameEl, t);
            const childForced = forced || selfMatch;
            const anyChildVisible = Array.from(project.querySelectorAll<HTMLElement>('.nav-service'))
                .reduce((acc, service) => filterService(service, childForced) || acc, false);
            const visible = childForced || anyChildVisible;
            project.toggleAttribute('data-filtered-out', !visible);
            if (visible) (project as HTMLDetailsElement).open = true;
            return visible;
        };

        const filterGroup = (group: HTMLElement, forced: boolean): boolean => {
            const label = group.querySelector('.nav-group-label');
            // The ungrouped pseudo-label ("No group") is not a name and must not match.
            const name = label?.querySelector('.nav-group-none') ? '' : label?.textContent ?? '';
            const selfMatch = !!name && matches(name);
            if (selfMatch) this.highlight(label, t);
            const childForced = forced || selfMatch;
            const anyChildVisible = Array.from(group.querySelectorAll<HTMLElement>('.nav-project'))
                .reduce((acc, project) => filterProject(project, childForced) || acc, false);
            const visible = childForced || anyChildVisible;
            group.toggleAttribute('data-filtered-out', !visible);
            return visible;
        };

        all('.nav-group').forEach(group => filterGroup(group, false));
    }

    /** Wraps every case-insensitive occurrence of `term` in `el`'s text in a `<mark>` element. */
    private highlight = (el: Element | null, term: string) => {
        if (!el) return;
        const text = el.textContent ?? '';
        const lower = text.toLowerCase();
        const content = this.document.createDocumentFragment();
        let from = 0;
        let at = lower.indexOf(term);
        while (at !== -1) {
            if (at > from) content.appendChild(this.document.createTextNode(text.slice(from, at)));
            const mark = this.document.createElement('mark');
            mark.textContent = text.slice(at, at + term.length);
            content.appendChild(mark);
            from = at + term.length;
            at = lower.indexOf(term, from);
        }
        if (from === 0) return; // nothing matched
        if (from < text.length) content.appendChild(this.document.createTextNode(text.slice(from)));
        el.replaceChildren(content);
    };

    /** Removes the `<mark>` wrappers a previous filter run left in `els`, restoring the plain text. */
    private unhighlight = (els: Element[]) => els.forEach(el => {
        const marks = el.querySelectorAll('mark');
        if (!marks.length) return;
        marks.forEach(mark => mark.replaceWith(...Array.from(mark.childNodes)));
        el.normalize();
    });

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
                        deploymentTimestamp: version.timestamp,
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
