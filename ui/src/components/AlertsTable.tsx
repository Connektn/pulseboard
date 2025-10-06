import { useState, useRef, useEffect } from 'react';
import type { Alert } from '../lib/types';

interface AlertsTableProps {
  alerts: Alert[];
}

export function AlertsTable({ alerts }: AlertsTableProps) {
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());
  const [isHovered, setIsHovered] = useState(false);
  const tableBodyRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when new alerts arrive, unless user is hovering
  useEffect(() => {
    if (!isHovered && tableBodyRef.current) {
      tableBodyRef.current.scrollTop = tableBodyRef.current.scrollHeight;
    }
  }, [alerts, isHovered]);

  const toggleRowExpansion = (alertId: string) => {
    setExpandedRows(prev => {
      const newSet = new Set(prev);
      if (newSet.has(alertId)) {
        newSet.delete(alertId);
      } else {
        newSet.add(alertId);
      }
      return newSet;
    });
  };

  const getSeverityColor = (severity: Alert['severity']) => {
    switch (severity) {
      case 'HIGH': return '#ef4444'; // red
      case 'MEDIUM': return '#f59e0b'; // yellow/amber
      case 'LOW': return '#10b981'; // green
      default: return '#6b7280'; // gray
    }
  };

  const formatTime = (timestamp: string) => {
    try {
      const date = new Date(timestamp);
      return date.toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });
    } catch {
      return timestamp;
    }
  };

  const formatEvidence = (evidence: Record<string, unknown>) => {
    return JSON.stringify(evidence, null, 2);
  };

  if (alerts.length === 0) {
    return (
      <div style={{
        backgroundColor: 'white',
        borderRadius: '0.5rem',
        padding: '2rem',
        boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
        border: '1px solid #e2e8f0',
        textAlign: 'center',
        minHeight: '400px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '1rem'
      }}>
        <h3 style={{
          margin: '0',
          fontSize: '1.25rem',
          fontWeight: '600',
          color: '#374151'
        }}>
          Live Alerts Table
        </h3>
        <p style={{
          margin: '0',
          color: '#6b7280',
          fontSize: '0.875rem'
        }}>
          No alerts yet. Start the simulator to see real-time data.
        </p>
      </div>
    );
  }

  return (
    <div style={{
      backgroundColor: 'white',
      borderRadius: '0.5rem',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
      border: '1px solid #e2e8f0',
      overflow: 'hidden'
    }}>
      {/* Sticky Header */}
      <div style={{
        backgroundColor: '#f8fafc',
        borderBottom: '2px solid #e2e8f0',
        padding: '0.75rem 1rem',
        position: 'sticky',
        top: 0,
        zIndex: 10
      }}>
        <h3 style={{
          margin: '0',
          fontSize: '1.125rem',
          fontWeight: '600',
          color: '#374151'
        }}>
          Live Alerts ({alerts.length})
        </h3>
      </div>

      {/* Table */}
      <div
        ref={tableBodyRef}
        style={{
          maxHeight: '500px',
          overflowY: 'auto'
        }}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
      >
        <table style={{
          width: '100%',
          borderCollapse: 'collapse',
          fontSize: '0.875rem'
        }}>
          {/* Table Header */}
          <thead style={{
            backgroundColor: '#f1f5f9',
            position: 'sticky',
            top: 0,
            zIndex: 5
          }}>
            <tr>
              <th style={{
                padding: '0.75rem 1rem',
                textAlign: 'left',
                fontWeight: '600',
                color: '#374151',
                borderBottom: '1px solid #e2e8f0',
                minWidth: '90px'
              }}>
                Time
              </th>
              <th style={{
                padding: '0.75rem 1rem',
                textAlign: 'left',
                fontWeight: '600',
                color: '#374151',
                borderBottom: '1px solid #e2e8f0',
                minWidth: '120px'
              }}>
                Rule
              </th>
              <th style={{
                padding: '0.75rem 1rem',
                textAlign: 'left',
                fontWeight: '600',
                color: '#374151',
                borderBottom: '1px solid #e2e8f0',
                minWidth: '100px'
              }}>
                Entity
              </th>
              <th style={{
                padding: '0.75rem 1rem',
                textAlign: 'left',
                fontWeight: '600',
                color: '#374151',
                borderBottom: '1px solid #e2e8f0',
                minWidth: '90px'
              }}>
                Severity
              </th>
              <th style={{
                padding: '0.75rem 1rem',
                textAlign: 'left',
                fontWeight: '600',
                color: '#374151',
                borderBottom: '1px solid #e2e8f0',
                minWidth: '100px'
              }}>
                Evidence
              </th>
            </tr>
          </thead>

          {/* Table Body */}
          <tbody>
            {alerts.map((alert, index) => (
              <tr key={`${alert.id}-${index}`} style={{
                borderBottom: index < alerts.length - 1 ? '1px solid #f1f5f9' : undefined
              }}>
                <td style={{
                  padding: '0.75rem 1rem',
                  color: '#374151',
                  fontFamily: 'ui-monospace, monospace',
                  fontSize: '0.8125rem'
                }}>
                  {formatTime(alert.ts)}
                </td>
                <td style={{
                  padding: '0.75rem 1rem',
                  color: '#374151',
                  fontWeight: '500'
                }}>
                  {alert.rule}
                </td>
                <td style={{
                  padding: '0.75rem 1rem',
                  color: '#374151',
                  fontFamily: 'ui-monospace, monospace',
                  fontSize: '0.8125rem'
                }}>
                  {alert.entityId}
                </td>
                <td style={{
                  padding: '0.75rem 1rem'
                }}>
                  <span style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    padding: '0.25rem 0.5rem',
                    borderRadius: '0.375rem',
                    fontSize: '0.75rem',
                    fontWeight: '600',
                    backgroundColor: getSeverityColor(alert.severity) + '20',
                    color: getSeverityColor(alert.severity)
                  }}>
                    {alert.severity}
                  </span>
                </td>
                <td style={{
                  padding: '0.75rem 1rem'
                }}>
                  <button
                    onClick={() => toggleRowExpansion(alert.id)}
                    style={{
                      backgroundColor: '#f1f5f9',
                      border: '1px solid #e2e8f0',
                      borderRadius: '0.375rem',
                      padding: '0.25rem 0.5rem',
                      fontSize: '0.75rem',
                      color: '#374151',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.25rem'
                    }}
                  >
                    {expandedRows.has(alert.id) ? '▼' : '▶'}
                    JSON
                  </button>
                  {expandedRows.has(alert.id) && (
                    <div style={{
                      marginTop: '0.5rem',
                      padding: '0.75rem',
                      backgroundColor: '#f8fafc',
                      borderRadius: '0.375rem',
                      border: '1px solid #e2e8f0'
                    }}>
                      <pre style={{
                        margin: '0',
                        fontSize: '0.75rem',
                        color: '#374151',
                        fontFamily: 'ui-monospace, monospace',
                        whiteSpace: 'pre-wrap',
                        wordBreak: 'break-word',
                        maxHeight: '200px',
                        overflow: 'auto'
                      }}>
                        {formatEvidence(alert.evidence)}
                      </pre>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}