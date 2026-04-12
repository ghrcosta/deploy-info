import { Component, ElementRef } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatTreeModule } from '@angular/material/tree';
import { MatIconModule } from '@angular/material/icon';
import { DeployViewerService } from '../deploy-viewer.component'

const VERSION_NODE_HIGHLIGHTED_CLASS = 'version-node-highlighted';
const VERSION_NODE_CLASS = 'version-node';

@Component({
  selector: 'deploy-navigator',
  templateUrl: './deploy-navigator.component.html',
  styleUrl: './deploy-navigator.component.scss',
  imports: [
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    MatTreeModule,
  ]
})
export class DeployNavigatorComponent {
    constructor(
        private deployViewerService: DeployViewerService,
        private elementRef: ElementRef
    ) {}

    onDeployClicked = (event: MouseEvent, deployId: string) => {
        this.deployViewerService.newDeployClickedEvent(deployId);

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
                        });
                    }

                    versionNodes.sort((a, b) => a.name.localeCompare(b.name));
                    serviceNodes.push({
                        id: service.name,
                        name: service.name,
                        icon: service.type,
                        children: versionNodes,
                    });
                }

                serviceNodes.sort((a, b) => a.name.localeCompare(b.name));
                projectNodes.push({
                    name: project.name,
                    services: serviceNodes,
                });
            }

            projectNodes.sort((a, b) => a.name.localeCompare(b.name));
            groupNodes.push({
                name: group.name,
                projects: projectNodes,
            });
        }

        groupNodes.sort((a, b) => {
            if (!a.name) return 1 // Make ungrouped items (name='') last
            if (!b.name) return -1
            return a.name.localeCompare(b.name)
        });
        return groupNodes;
    }

    groupList = this.convertDataToNodes(EXAMPLE_DATA_FULL);
    //groupList = this.convertDataToNodes(EXAMPLE_DATA_1_GROUP_WITH_NAME);
    //groupList = this.convertDataToNodes(EXAMPLE_DATA_1_GROUP_WITHOUT_NAME);
    //groupList = this.convertDataToNodes(EXAMPLE_DATA_EMPTY);
    childrenAccessor = (node: TreeNode) => node.children ?? [];
    hasChild = (_: number, node: TreeNode) => !!node.children && node.children.length > 0;
}

interface GroupNode {
    name: string;
    projects: ProjectNode[];
}
interface ProjectNode {
    name: string;
    services: TreeNode[];
}
interface TreeNode {
    id: string;
    name: string;
    icon?: string;
    children?: TreeNode[];
}

interface GroupEntry {
    name: string;
    projects: ProjectEntry[];
}
interface ProjectEntry {
    name: string;
    services: ServiceEntry[];
}
interface ServiceEntry {
    name: string;
    type: string;
    versions: VersionEntry[];
}
interface VersionEntry {
    id: string;
    name: string;
}

const EXAMPLE_DATA_FULL: GroupEntry[] = [
    {
        name: 'PROD',
        projects: [
            {
                name: 'Project C',
                services: [
                    {
                        name: 'GAE Service 23',
                        type: 'GAE',
                        versions: [
                            { id: '1', name: 'Version name 42' },
                            { id: '2', name: 'Version name 51232' },
                        ]
                    },
                    {
                        name: 'GAE Service 151',
                        type: 'GAE',
                        versions: [
                            { id: '3', name: 'Version name 42' },
                            { id: '4', name: 'Version name 51232' },
                        ]
                    },
                    {
                        name: 'GAE Service A3',
                        type: 'GAE',
                        versions: [
                            { id: '5', name: 'Version name 42' },
                            { id: '6', name: 'Version name 51232' },
                        ]
                    }
                ]
            },
            {
                name: 'Project B',
                services: [
                    {
                        name: 'GAE Service B1',
                        type: 'GAE',
                        versions: [
                            { id: '7', name: 'Version name 42' },
                            { id: '8', name: 'Version name 51232' },
                        ]
                    },
                    {
                        name: 'RUN Service B2',
                        type: 'RUN',
                        versions: [
                            { id: '9', name: 'Version name 42' },
                            { id: '10', name: 'Version name 51232' },
                        ]
                    }
                ]
            },
        ]
    },
    {
        name: 'DEV',
        projects: [
            {
                name: 'Project A has a very long name, like, really really long for real',
                services: [
                    {
                        name: 'GAE Service with a very long name',
                        type: 'GAE',
                        versions: [
                            { id: '11', name: 'Version name 42' },
                            { id: '12', name: 'Version name 51232' },
                        ]
                    },
                    {
                        name: 'RUN Service B2',
                        type: 'RUN',
                        versions: [
                            { id: '13', name: 'MyVersion_FULL' },
                            { id: '14', name: 'MyVersion_NO_EXTRA' },
                        ]
                    }
                ]
            },
        ]
    },
    {
        name: '',
        projects: [
            {
                name: 'Project D',
                services: [
                    {
                        name: 'RUN Service B1',
                        type: 'RUN',
                        versions: [
                            { id: '15', name: 'MyVersion_NO_GIT' },
                            { id: '16', name: 'Version name 51232' },
                        ]
                    },
                    {
                        name: 'RUN Service B2',
                        type: 'RUN',
                        versions: [
                            { id: '17', name: 'Version name 42' },
                            { id: '18', name: 'Version name 51232' },
                        ]
                    }
                ]
            }
        ]
    }
];
