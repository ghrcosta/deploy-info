import { Component, ElementRef, inject, ChangeDetectionStrategy } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTreeModule } from '@angular/material/tree';
import { MatIconModule } from '@angular/material/icon';
import { DeployViewerService, DeploySelection } from '../deploy-viewer.component'
import { DeployTreeNetworkService } from '../deploy-tree.network.service';
import { GroupEntry } from '../tree-model';

const VERSION_NODE_HIGHLIGHTED_CLASS = 'version-node-highlighted';
const VERSION_NODE_CLASS = 'version-node';

@Component({
  selector: 'deploy-navigator',
  templateUrl: './deploy-navigator.component.html',
  styleUrl: './deploy-navigator.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    MatTreeModule,
  ]
})
export class DeployNavigatorComponent {
    private treeNetworkService = inject(DeployTreeNetworkService);

    constructor(
        private deployViewerService: DeployViewerService,
        private elementRef: ElementRef
    ) {}

    groupList: GroupNode[] = [];

    ngOnInit() {
        this.treeNetworkService.getTree().subscribe(entries => {
            this.groupList = this.convertDataToNodes(entries);
        });
    }
    onDeployClicked = (event: MouseEvent, node: TreeNode) => {
        if (node.selection) {
            this.deployViewerService.newDeployClickedEvent(node.selection);
        }

        const newVersionClicked = event.currentTarget as HTMLElement;
        if (!newVersionClicked.classList.contains(VERSION_NODE_HIGHLIGHTED_CLASS)) {
            // Ensure other versions are no longer highlighted, then highlight clicked version
            let versionNodes = this.elementRef.nativeElement.querySelectorAll('.'+VERSION_NODE_CLASS);
            for (var versionNode of versionNodes) {
                versionNode.classList.remove(VERSION_NODE_HIGHLIGHTED_CLASS);
            }

            newVersionClicked.classList.add(VERSION_NODE_HIGHLIGHTED_CLASS);
        }
    }

    private convertDataToNodes = (sourceData: GroupEntry[]) => {
        let groupNodes: GroupNode[] = [];

        for (var group of sourceData) {
            let projectNodes: ProjectNode[] = [];
            for (var project of group.projects) {
                let serviceNodes: TreeNode[] = [];
                for (var service of project.services) {
                    let versionNodes: TreeNode[] = [];
                    for (var version of service.versions) {
                        versionNodes.push({
                            id: version.id,
                            name: version.name,
                            selection: {
                                project: project.projectId,
                                service: service.name,
                                version: version,
                            },
                        });
                    }

                    // The backend sorts every level of the tree (see documentation/api.md)

                    serviceNodes.push({
                        id: service.name,
                        name: service.name,
                        icon: service.type,
                        children: versionNodes,
                    });
                }

                projectNodes.push({
                    projectId: project.projectId,
                    services: serviceNodes,
                });
            }

            groupNodes.push({
                name: group.name,
                projects: projectNodes,
            });
        }

        return groupNodes;
    }

    childrenAccessor = (node: TreeNode) => node.children ?? [];
    hasChild = (_: number, node: TreeNode) => !!node.children && node.children.length > 0;
}

interface GroupNode {
    name: string;
    projects: ProjectNode[];
}
interface ProjectNode {
    projectId: string;
    services: TreeNode[];
}
interface TreeNode {
    id: string;
    name: string;
    icon?: string;
    children?: TreeNode[];
    /** The file-viewer selection this node represents; only set on version nodes. */
    selection?: DeploySelection;
}
