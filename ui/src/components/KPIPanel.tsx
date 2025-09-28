import { useState, useEffect, useRef } from 'react';
import { Chart, registerables } from 'chart.js';
import 'chartjs-adapter-date-fns';
import { type Alert } from '../lib/useSSE';

Chart.register(...registerables);

interface KPIPanelProps {
  stats: {
    eventsPerMin: number;
    alertsPerMin: number;
    uptimeSec: number;
  } | null;
  alerts: Alert[];
}

interface TimeSeriesPoint {
  timestamp: Date;
  alertsPerMin: number;
}

export function KPIPanel({ stats, alerts }: KPIPanelProps) {
  const chartRef = useRef<HTMLCanvasElement>(null);
  const chartInstanceRef = useRef<Chart<'line'> | null>(null);
  const [timeSeriesData, setTimeSeriesData] = useState<TimeSeriesPoint[]>([]);

  // Update time series data every second
  useEffect(() => {
    const updateData = () => {
      const now = new Date();
      const twoMinutesAgo = new Date(now.getTime() - 2 * 60 * 1000);

      // Count alerts in the last minute
      const oneMinuteAgo = new Date(now.getTime() - 60 * 1000);
      const recentAlerts = alerts.filter(alert => {
        const alertTime = new Date(alert.ts);
        return alertTime >= oneMinuteAgo && alertTime <= now;
      });

      const newPoint: TimeSeriesPoint = {
        timestamp: now,
        alertsPerMin: recentAlerts.length
      };

      setTimeSeriesData(prevData => {
        // Add new point and filter out old data (older than 2 minutes)
        const updatedData = [...prevData, newPoint].filter(
          point => point.timestamp >= twoMinutesAgo
        );

        // Keep only the last 120 points (2 minutes at 1-second intervals)
        return updatedData.slice(-120);
      });
    };

    // Initial update
    updateData();

    // Set up 1-second interval
    const interval = setInterval(updateData, 1000);

    return () => clearInterval(interval);
  }, [alerts]);

  // Update chart when data changes
  useEffect(() => {
    if (!chartRef.current) return;

    // Destroy existing chart
    if (chartInstanceRef.current) {
      chartInstanceRef.current.destroy();
    }

    // Create new chart
    const ctx = chartRef.current.getContext('2d');
    if (!ctx) return;

    chartInstanceRef.current = new Chart(ctx, {
      type: 'line',
      data: {
        datasets: [{
          label: 'Alerts/min',
          data: timeSeriesData.map(point => ({
            x: point.timestamp.getTime(),
            y: point.alertsPerMin
          })),
          borderColor: '#ef4444',
          backgroundColor: 'rgba(239, 68, 68, 0.1)',
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
          tension: 0.3,
          fill: true
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
          intersect: false,
          mode: 'index'
        },
        plugins: {
          legend: {
            display: false
          },
          tooltip: {
            enabled: false
          }
        },
        scales: {
          x: {
            type: 'time',
            time: {
              displayFormats: {
                second: 'HH:mm:ss'
              }
            },
            grid: {
              display: false
            },
            ticks: {
              display: false
            }
          },
          y: {
            beginAtZero: true,
            grid: {
              display: false
            },
            ticks: {
              display: false
            }
          }
        },
        animation: {
          duration: 0
        }
      }
    });

    return () => {
      if (chartInstanceRef.current) {
        chartInstanceRef.current.destroy();
      }
    };
  }, [timeSeriesData]);

  const formatUptime = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    } else if (minutes > 0) {
      return `${minutes}m ${secs}s`;
    } else {
      return `${secs}s`;
    }
  };

  if (!stats) {
    return (
      <div style={{
        backgroundColor: 'white',
        borderRadius: '0.5rem',
        padding: '1.5rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        border: '1px solid #e2e8f0'
      }}>
        <h3 style={{
          margin: '0 0 1rem 0',
          fontSize: '1.125rem',
          fontWeight: '600',
          color: '#374151'
        }}>
          System Metrics
        </h3>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '120px',
          color: '#6b7280',
          fontSize: '0.875rem'
        }}>
          Loading metrics...
        </div>
      </div>
    );
  }

  return (
    <div style={{
      backgroundColor: 'white',
      borderRadius: '0.5rem',
      padding: '1.5rem',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      border: '1px solid #e2e8f0'
    }}>
      {/* Header */}
      <h3 style={{
        margin: '0 0 1.5rem 0',
        fontSize: '1.125rem',
        fontWeight: '600',
        color: '#374151'
      }}>
        System Metrics
      </h3>

      {/* KPI Tiles */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '1rem',
        marginBottom: '1.5rem'
      }}>
        {/* Events/min */}
        <div style={{
          backgroundColor: '#f8fafc',
          padding: '1rem',
          borderRadius: '0.5rem',
          border: '1px solid #e2e8f0',
          textAlign: 'center'
        }}>
          <div style={{
            fontSize: '2rem',
            fontWeight: 'bold',
            color: '#1e40af',
            lineHeight: 1,
            marginBottom: '0.5rem'
          }}>
            {stats.eventsPerMin}
          </div>
          <div style={{
            fontSize: '0.875rem',
            color: '#6b7280',
            fontWeight: '500'
          }}>
            Events/min
          </div>
        </div>

        {/* Alerts/min */}
        <div style={{
          backgroundColor: '#fef2f2',
          padding: '1rem',
          borderRadius: '0.5rem',
          border: '1px solid #fecaca',
          textAlign: 'center'
        }}>
          <div style={{
            fontSize: '2rem',
            fontWeight: 'bold',
            color: '#ef4444',
            lineHeight: 1,
            marginBottom: '0.5rem'
          }}>
            {stats.alertsPerMin}
          </div>
          <div style={{
            fontSize: '0.875rem',
            color: '#6b7280',
            fontWeight: '500'
          }}>
            Alerts/min
          </div>
        </div>

        {/* Uptime */}
        <div style={{
          backgroundColor: '#f0fdf4',
          padding: '1rem',
          borderRadius: '0.5rem',
          border: '1px solid #bbf7d0',
          textAlign: 'center'
        }}>
          <div style={{
            fontSize: '1.25rem',
            fontWeight: 'bold',
            color: '#10b981',
            lineHeight: 1,
            marginBottom: '0.5rem'
          }}>
            {formatUptime(stats.uptimeSec)}
          </div>
          <div style={{
            fontSize: '0.875rem',
            color: '#6b7280',
            fontWeight: '500'
          }}>
            Uptime
          </div>
        </div>
      </div>

      {/* Sparkline Chart */}
      <div style={{
        backgroundColor: '#f8fafc',
        borderRadius: '0.5rem',
        border: '1px solid #e2e8f0',
        padding: '1rem'
      }}>
        <div style={{
          fontSize: '0.875rem',
          fontWeight: '600',
          color: '#374151',
          marginBottom: '0.75rem'
        }}>
          Alerts/min (Last 2 minutes)
        </div>
        <div style={{ height: '80px', position: 'relative' }}>
          <canvas
            ref={chartRef}
            style={{
              width: '100%',
              height: '100%'
            }}
          />
        </div>
      </div>
    </div>
  );
}