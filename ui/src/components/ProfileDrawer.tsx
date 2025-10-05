import { useEffect } from 'react';
import { ProfileSummary } from '../lib/useCdpProfiles';

interface ProfileDrawerProps {
  profile: ProfileSummary | null;
  onClose: () => void;
}

export function ProfileDrawer({ profile, onClose }: ProfileDrawerProps) {
  // Handle ESC key to close drawer
  useEffect(() => {
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && profile) {
        onClose();
      }
    };

    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [profile, onClose]);

  if (!profile) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        onClick={onClose}
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.5)',
          zIndex: 1000,
        }}
      />

      {/* Drawer */}
      <div
        style={{
          position: 'fixed',
          top: 0,
          right: 0,
          bottom: 0,
          width: '400px',
          maxWidth: '90vw',
          backgroundColor: 'white',
          boxShadow: '-4px 0 6px rgba(0, 0, 0, 0.1)',
          zIndex: 1001,
          display: 'flex',
          flexDirection: 'column',
          animation: 'slideIn 0.3s ease',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: '1.5rem',
            borderBottom: '1px solid #e5e7eb',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <h2
            style={{
              margin: 0,
              fontSize: '1.25rem',
              fontWeight: '600',
              color: '#111827',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
            title={profile.profileId}
          >
            {profile.profileId}
          </h2>
          <button
            onClick={onClose}
            style={{
              backgroundColor: 'transparent',
              border: 'none',
              fontSize: '1.5rem',
              cursor: 'pointer',
              color: '#6b7280',
              padding: '0.25rem',
              lineHeight: 1,
            }}
          >
            ×
          </button>
        </div>

        {/* Content */}
        <div
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '1.5rem',
          }}
        >
          {/* Traits Section */}
          <section style={{ marginBottom: '2rem' }}>
            <h3
              style={{
                margin: '0 0 1rem 0',
                fontSize: '0.875rem',
                fontWeight: '600',
                color: '#6b7280',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              Traits
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>Plan</div>
                <div style={{ fontSize: '0.875rem', fontWeight: '500', color: '#111827' }}>
                  {profile.plan || 'N/A'}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>Country</div>
                <div style={{ fontSize: '0.875rem', fontWeight: '500', color: '#111827' }}>
                  {profile.country || 'N/A'}
                </div>
              </div>
            </div>
          </section>

          {/* Identifiers Section */}
          <section style={{ marginBottom: '2rem' }}>
            <h3
              style={{
                margin: '0 0 1rem 0',
                fontSize: '0.875rem',
                fontWeight: '600',
                color: '#6b7280',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              Identifiers
            </h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>User IDs</div>
                <div style={{ fontSize: '0.875rem', color: '#111827' }}>
                  {profile.identifiers.userIds.length > 0 ? (
                    <ul style={{ margin: '0.25rem 0 0 0', padding: '0 0 0 1.25rem' }}>
                      {profile.identifiers.userIds.map((id) => (
                        <li key={id} style={{ fontSize: '0.75rem' }}>
                          {id}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <span style={{ fontSize: '0.75rem', color: '#9ca3af' }}>None</span>
                  )}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>Emails</div>
                <div style={{ fontSize: '0.875rem', color: '#111827' }}>
                  {profile.identifiers.emails.length > 0 ? (
                    <ul style={{ margin: '0.25rem 0 0 0', padding: '0 0 0 1.25rem' }}>
                      {profile.identifiers.emails.map((email) => (
                        <li key={email} style={{ fontSize: '0.75rem' }}>
                          {email}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <span style={{ fontSize: '0.75rem', color: '#9ca3af' }}>None</span>
                  )}
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>Anonymous IDs</div>
                <div style={{ fontSize: '0.875rem', color: '#111827' }}>
                  {profile.identifiers.anonymousIds.length > 0 ? (
                    <ul style={{ margin: '0.25rem 0 0 0', padding: '0 0 0 1.25rem' }}>
                      {profile.identifiers.anonymousIds.map((anonId) => (
                        <li key={anonId} style={{ fontSize: '0.75rem' }}>
                          {anonId}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <span style={{ fontSize: '0.75rem', color: '#9ca3af' }}>None</span>
                  )}
                </div>
              </div>
            </div>
          </section>

          {/* Events Section (Placeholder) */}
          <section>
            <h3
              style={{
                margin: '0 0 1rem 0',
                fontSize: '0.875rem',
                fontWeight: '600',
                color: '#6b7280',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              Events
            </h3>
            <div
              style={{
                padding: '2rem',
                backgroundColor: '#f9fafb',
                borderRadius: '0.5rem',
                textAlign: 'center',
                color: '#6b7280',
                fontSize: '0.875rem',
              }}
            >
              Recent events timeline (coming soon)
            </div>
          </section>
        </div>
      </div>
    </>
  );
}
