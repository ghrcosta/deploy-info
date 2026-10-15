import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HelpComponent } from './help.component';
import { HomeService, HomeView } from '../home/home.service';

describe('HelpComponent', () => {
    let component: HelpComponent;
    let fixture: ComponentFixture<HelpComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HelpComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(HelpComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('renders the getting-started card with the three steps', () => {
        const el = fixture.nativeElement as HTMLElement;
        expect(el.querySelector('.help-head h1')?.textContent).toBe('Three steps to see your deployments here:');
        expect(el.querySelectorAll('.help-steps > li').length).toBe(3);
    });

    it('renders the Gradle snippet as a per-line code block', () => {
        const el = fixture.nativeElement as HTMLElement;
        const lines = el.querySelectorAll('.help-steps .code .cl');
        expect(lines.length).toBe(component.snippetLines.length);
        expect(lines[0].querySelector('.cln')?.textContent).toBe('1');
        expect(lines[0].querySelector('.clt')?.textContent).toBe('import io.github.ghrcosta.CollectorTask');
        expect(lines[1].querySelector('.clt')?.textContent).toBe(' ');
    });

    it('shows every deployInfoCollect parameter with an aligned comment', () => {
        const snippetLines = component.snippetLines.map(line => line.text);
        const snippet = snippetLines.join('\n');

        expect(snippet).toContain('deployType.set(CollectorTask.DeployType.GAE)');
        expect(snippet).toContain('portalUrl.set("https://deploy-info.example.com")');
        expect(snippet).toContain('projects.set(listOf("proj-prod", "proj-dev"))');
        expect(snippet).toContain('maxFileSize.set(50)');
        expect(snippet).toContain('collectGitStatus.set(true)');
        expect(snippet).toContain('extraFilesToCollect.set(listOf(');
        expect(snippet).not.toContain('required');
        expect(snippet).not.toContain('optional');

        // Every trailing comment starts at the same column, and the first
        // word of each comment is capitalized.
        const commentColumns = snippetLines
            .filter(text => text.trimStart().startsWith('//'))
            .map(text => text.lastIndexOf('//'));
        expect(new Set(commentColumns).size).toBe(1);
    });

    it('does not show the removed collector paragraph', () => {
        const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
        expect(text).not.toContain('The plugin collects');
    });

    it('switches to the Projects/Deployments views from the internal links', () => {
        const homeService = TestBed.inject(HomeService);
        const views: HomeView[] = [];
        homeService.viewObservable.subscribe(view => views.push(view));

        const el = fixture.nativeElement as HTMLElement;
        const projectsLink = el.querySelector('.help-steps li:nth-child(1) a') as HTMLElement;
        const deploymentsLink = el.querySelector('.help-steps li:nth-child(3) a') as HTMLElement;

        projectsLink.click();
        deploymentsLink.click();

        expect(views).toContain('projects');
        expect(views).toContain('deployments');
    });
});
