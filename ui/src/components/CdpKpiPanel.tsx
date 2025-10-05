import { useEffect, useRef } from 'react';
import { useCdpKpis } from '../lib/useCdpKpis';

interface CdpKpiPanelProps {
  isSimulatorRunning: boolean;
}

declare global {
  interface Window {
    Chart: any;
  }
}

export function CdpKpiPanel({ isSimulatorRunning }: CdpKpiPanelProps) {
  const kpis = useCdpKpis(isSimulatorRunning);
  const chartRef = useRef<HTMLCanvasElement>(null);
  const chartInstanceRef = useRef<any>(null);

  // Initialize Chart.js sparkline when simulator starts
  useEffect(() => {
    if (!isSimulatorRunning) {
      // Cleanup chart when simulator stops
      if (chartInstanceRef.current) {
        chartInstanceRef.current.destroy();
        chartInstanceRef.current = null;
      }
      return;
    }

    if (!chartRef.current || typeof window.Chart === 'undefined') {
      console.warn('Chart.js not loaded or canvas ref not available');
      return;
    }

    const ctx = chartRef.current.getContext('2d');
    if (!ctx) return;

    // Destroy existing chart if any
    if (chartInstanceRef.current) {
      chartInstanceRef.current.destroy();
    }

    // Create new chart
    chartInstanceRef.current = new window.Chart(ctx, {
      type: 'line',
      data: {
        labels: Array(120).fill(''), // 120 data points (2 minutes at 1s intervals)
        datasets: [
          {
            label: 'Segment ENTERs/min',
            data: [],
            borderColor: '#3b82f6',
            backgroundColor: 'rgba(59, 130, 246, 0.1)',
            borderWidth: 2,
            fill: true,
            tension: 0.4,
            pointRadius: 0,
            pointHoverRadius: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
          duration: 0, // Disable animation to avoid jank
        },
        scales: {
          x: {
            display: false,
          },
          y: {
            beginAtZero: true,
            ticks: {
              font: {
                size: 10,
              },
              color: '#6b7280',
            },
            grid: {
              color: '#e5e7eb',
            },
          },
        },
        plugins: {
          legend: {
            display: false,
          },
          tooltip: {
            enabled: false,
          },
        },
      },
    });

    return () => {
      if (chartInstanceRef.current) {
        chartInstanceRef.current.destroy();
        chartInstanceRef.current = null;
      }
    };
  }, [isSimulatorRunning]);

  // Update chart data
  useEffect(() => {
    if (!chartInstanceRef.current || !isSimulatorRunning) return;
    if (kpis.segmentEntersHistory.length === 0) return;

    console.log('Updating sparkline with', kpis.segmentEntersHistory.length, 'data points');

    // Use requestAnimationFrame to avoid reflow
    requestAnimationFrame(() => {
      if (chartInstanceRef.current) {
        chartInstanceRef.current.data.datasets[0].data = kpis.segmentEntersHistory;
        chartInstanceRef.current.update('none'); // Update without animation
      }
    });
  }, [kpis.segmentEntersHistory, isSimulatorRunning]);

  // Simulator not running state
  if (!isSimulatorRunning) {
    return (
      <div
        style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          padding: '1.5rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ textAlign: 'center', color: '#6b7280' }}>
          <div style={{ fontSize: '0.875rem', fontWeight: '600', marginBottom: '0.5rem' }}>
            CDP Metrics
          </div>
          <div style={{ fontSize: '0.75rem' }}>
            Start the simulator to see real-time KPIs
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        backgroundColor: 'white',
        borderRadius: '0.5rem',
        padding: '1.5rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        border: '1px solid #e2e8f0',
      }}
    >
      {/* KPI Cards */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: '1rem',
          marginBottom: '1.5rem',
        }}
      >
        {/* Active Profiles */}
        <div
          style={{
            padding: '1rem',
            backgroundColor: '#f9fafb',
            borderRadius: '0.5rem',
            border: '1px solid #e5e7eb',
          }}
        >
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: '600',
              color: '#6b7280',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              marginBottom: '0.5rem',
            }}
          >
            Active Profiles (5m)
          </div>
          <div
            style={{
              fontSize: '1.875rem',
              fontWeight: '700',
              color: '#111827',
            }}
          >
            {kpis.activeProfiles}
          </div>
        </div>

        {/* Events/min */}
        <div
          style={{
            padding: '1rem',
            backgroundColor: '#f9fafb',
            borderRadius: '0.5rem',
            border: '1px solid #e5e7eb',
          }}
        >
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: '600',
              color: '#6b7280',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              marginBottom: '0.5rem',
            }}
          >
            Events/min
          </div>
          <div
            style={{
              fontSize: '1.875rem',
              fontWeight: '700',
              color: '#111827',
            }}
          >
            {kpis.eventsPerMinute.toFixed(1)}
          </div>
        </div>

        {/* Segment ENTERs/min */}
        <div
          style={{
            padding: '1rem',
            backgroundColor: '#f9fafb',
            borderRadius: '0.5rem',
            border: '1px solid #e5e7eb',
          }}
        >
          <div
            style={{
              fontSize: '0.75rem',
              fontWeight: '600',
              color: '#6b7280',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              marginBottom: '0.5rem',
            }}
          >
            Segment ENTERs/min
          </div>
          <div
            style={{
              fontSize: '1.875rem',
              fontWeight: '700',
              color: '#3b82f6',
            }}
          >
            {kpis.segmentEntersPerMinute.toFixed(1)}
          </div>
        </div>
      </div>

      {/* Sparkline */}
      <div>
        <div
          style={{
            fontSize: '0.75rem',
            fontWeight: '600',
            color: '#6b7280',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            marginBottom: '0.75rem',
          }}
        >
          Segment ENTERs/min (Last 2 min)
        </div>
        <div style={{ height: '120px', position: 'relative' }}>
          <canvas ref={chartRef} />
        </div>
      </div>
    </div>
  );
}
