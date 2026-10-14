import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Chart, registerables } from 'chart.js';

import { StatsService } from '../../core/api/stats.service';
import { Stats } from '../../core/models';
import { errorMessage } from '../../shared/error-message';

// Chart.js is tree-shakable: chart types/scales must be registered. `registerables` = all of them.
Chart.register(...registerables);

/**
 * Admin dashboard: stat cards + two Chart.js bar charts (utilization per room, busiest hours).
 *
 * Lifecycle hooks:
 *  - ngOnInit:        start loading the stats
 *  - ngAfterViewInit: the template exists -> <canvas> elements can be found via @ViewChild
 *  - ngOnDestroy:     destroy the charts (they hold canvas contexts and event listeners)
 * Charts are drawn once BOTH the data and the view are ready, whichever happens last.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss',
})
export class AdminDashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(StatsService);

  /** @ViewChild finds the element marked #utilizationChart in the template. */
  @ViewChild('utilizationChart') utilizationCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('hoursChart') hoursCanvas?: ElementRef<HTMLCanvasElement>;

  readonly stats = signal<Stats | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Typed as Pick<Chart, 'destroy'> because each chart has a different generic type and we only need destroy().
  private charts: Pick<Chart, 'destroy'>[] = [];
  private viewReady = false;

  ngOnInit(): void {
    this.api.get().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
        this.renderCharts();
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.renderCharts();
  }

  ngOnDestroy(): void {
    this.charts.forEach((c) => c.destroy());
  }

  /** Busiest hour label for the stat card, e.g. "10:00". */
  busiestHour(stats: Stats): string {
    const top = [...stats.busiestHours].sort((a, b) => b.count - a.count)[0];
    return top && top.count > 0 ? `${String(top.hour).padStart(2, '0')}:00` : '—';
  }

  /**
   * setTimeout waits one tick so the @if blocks have put the <canvas> elements into the DOM
   * after `loading` flipped to false.
   */
  private renderCharts(): void {
    const stats = this.stats();
    if (!stats || !this.viewReady) {
      return;
    }
    setTimeout(() => {
      this.charts.forEach((c) => c.destroy());
      this.charts = [];
      if (this.utilizationCanvas) {
        this.charts.push(this.buildUtilizationChart(this.utilizationCanvas.nativeElement, stats));
      }
      if (this.hoursCanvas) {
        this.charts.push(this.buildHoursChart(this.hoursCanvas.nativeElement, stats));
      }
    });
  }

  /** Horizontal bars: one per room, value = utilization %. */
  private buildUtilizationChart(canvas: HTMLCanvasElement, stats: Stats) {
    return new Chart(canvas, {
      type: 'bar',
      data: {
        labels: stats.roomUtilization.map((r) => r.roomName),
        datasets: [{
          label: 'Utilization %',
          data: stats.roomUtilization.map((r) => r.utilizationPercent),
          backgroundColor: stats.roomUtilization.map((r) => (r.active ? '#0f766e' : '#cbd5e1')),
          borderRadius: 6,
          maxBarThickness: 28,
        }],
      },
      options: {
        indexAxis: 'y',              // horizontal bars: long room names stay readable
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { x: { beginAtZero: true, ticks: { callback: (v) => `${v}%` } } },
      },
    });
  }

  /** Vertical bars: how many bookings touch each business hour this week. */
  private buildHoursChart(canvas: HTMLCanvasElement, stats: Stats) {
    return new Chart(canvas, {
      type: 'bar',
      data: {
        labels: stats.busiestHours.map((h) => `${String(h.hour).padStart(2, '0')}:00`),
        datasets: [{
          label: 'Bookings',
          data: stats.busiestHours.map((h) => h.count),
          backgroundColor: '#f59e0b',
          borderRadius: 6,
          maxBarThickness: 32,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } }, x: { grid: { display: false } } },
      },
    });
  }
}
