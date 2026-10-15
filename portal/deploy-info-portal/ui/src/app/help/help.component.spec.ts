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

    it('includes the Gradle snippet in its code block', () => {
        const el = fixture.nativeElement as HTMLElement;
        const rendered = Array.from(el.querySelectorAll('.help-steps .code-gradle .clt'), e => e.textContent);
        expect(rendered).toEqual(component.snippetLines.map(line => line.text || ' '));
    });

    it('includes the Python CLI snippet in its code block', () => {
        const el = fixture.nativeElement as HTMLElement;
        const rendered = Array.from(el.querySelectorAll('.help-steps .code-python-cli .clt'), e => e.textContent);
        expect(rendered).toEqual(component.pythonCliSnippetLines.map(line => line.text || ' '));
    });

    it('includes the Python script-call snippet in its code block', () => {
        const el = fixture.nativeElement as HTMLElement;
        const rendered = Array.from(el.querySelectorAll('.help-steps .code-python-script .clt'), e => e.textContent);
        expect(rendered).toEqual(component.pythonScriptSnippetLines.map(line => line.text || ' '));
    });

    it('shows the Python collector as the non-Gradle alternative with its prerequisites and defaults', () => {
        const el = fixture.nativeElement as HTMLElement;
        const text = el.textContent ?? '';
        expect(text).toContain('Not building with Gradle?');
        expect(text).toContain('Python ≥ 3.10');

        // The "deploy-info repository" mention links to GitHub in a new tab.
        const repoLink = el.querySelector('.help-hint a[href="https://github.com/ghrcosta/deploy-info"]') as HTMLAnchorElement;
        expect(repoLink).withContext('repository link present').toBeTruthy();
        expect(repoLink.target).toBe('_blank');
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
