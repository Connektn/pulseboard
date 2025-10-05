import { useState, memo, useEffect } from 'react';
import { formatDistanceToNow } from 'date-fns';
import { useCdpProfiles, type ProfileSummary } from '../lib/useCdpProfiles';
import { ProfileDrawer } from './ProfileDrawer';

// Memoized row component to prevent unnecessary re-renders
const ProfileRow = memo(({ profile, onClick }: { profile: ProfileSummary; onClick: () => void }) => {
  const formatLastSeen = (isoString: string): string => {
    try {
      return formatDistanceToNow(new Date(isoString), { addSuffix: true });
    } catch {
      return 'Unknown';
    }
  };

  const getPlanBadge = (plan: string | null) => {
    if (!plan) return <span style={{ color: '#9ca3af' }}>—</span>;

    const color = plan === 'pro' ? '#3b82f6' : '#10b981';
    return (
      <span
        style={{
          backgroundColor: `${color}22`,
          color: color,
          padding: '0.25rem 0.5rem',
          borderRadius: '0.25rem',
          fontSize: '0.75rem',
          fontWeight: '600',
        }}
      >
        {plan}
      </span>
    );
  };

  const formatIdentifiers = (identifiers: ProfileSummary['identifiers']): string => {
    const u = identifiers.userIds.length;
    const e = identifiers.emails.length;
    const a = identifiers.anonymousIds.length;
    return `${u}/${e}/${a}`;
  };

  return (
    <tr
      onClick={onClick}
      style={{
        cursor: 'pointer',
        transition: 'background-color 0.15s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.backgroundColor = '#f9fafb';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.backgroundColor = 'transparent';
      }}
    >
      <td
        style={{
          padding: '0.75rem 1rem',
          fontSize: '0.875rem',
          color: '#111827',
          fontFamily: 'monospace',
          maxWidth: '200px',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
        title={profile.profileId}
      >
        {profile.profileId}
      </td>
      <td style={{ padding: '0.75rem 1rem', fontSize: '0.875rem' }}>
        {getPlanBadge(profile.plan)}
      </td>
      <td style={{ padding: '0.75rem 1rem', fontSize: '0.875rem', color: '#111827' }}>
        {profile.country || <span style={{ color: '#9ca3af' }}>—</span>}
      </td>
      <td style={{ padding: '0.75rem 1rem', fontSize: '0.875rem', color: '#6b7280' }}>
        {formatLastSeen(profile.lastSeen)}
      </td>
      <td
        style={{
          padding: '0.75rem 1rem',
          fontSize: '0.875rem',
          color: '#111827',
          fontFamily: 'monospace',
        }}
        title={`${profile.identifiers.userIds.length} user IDs, ${profile.identifiers.emails.length} emails, ${profile.identifiers.anonymousIds.length} anonymous IDs`}
      >
        {formatIdentifiers(profile.identifiers)}
      </td>
      <td
        style={{
          padding: '0.75rem 1rem',
          fontSize: '0.875rem',
          color: '#111827',
          fontWeight: '600',
        }}
      >
        {profile.featureUsedCount}
      </td>
    </tr>
  );
});

ProfileRow.displayName = 'ProfileRow';

export function ProfilesList({ isSimulatorRunning }: { isSimulatorRunning: boolean }) {
  const { profiles, connected, error } = useCdpProfiles(isSimulatorRunning);
  const [selectedProfile, setSelectedProfile] = useState<ProfileSummary | null>(null);

  // Throttled profiles: update at most once per 5 seconds to avoid scroll jank
  const [displayedProfiles, setDisplayedProfiles] = useState<ProfileSummary[]>([]);

  useEffect(() => {
    // Set initial profiles immediately
    setDisplayedProfiles(profiles);

    const interval = setInterval(() => {
      setDisplayedProfiles(profiles);
    }, 5000); // Throttle to 5s to reduce scroll jank

    return () => clearInterval(interval);
  }, [profiles]);

  // Simulator not running state
  if (!isSimulatorRunning) {
    return (
      <div
        style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          padding: '2rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ textAlign: 'center', color: '#6b7280' }}>
          <div style={{ fontSize: '1rem', fontWeight: '600', marginBottom: '0.5rem' }}>
            Simulator not running
          </div>
          <div style={{ fontSize: '0.875rem' }}>
            Click the Start button to begin generating CDP events
          </div>
        </div>
      </div>
    );
  }

  // Loading state
  if (!connected && displayedProfiles.length === 0) {
    return (
      <div
        style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          padding: '2rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ textAlign: 'center', color: '#6b7280' }}>
          <div style={{ fontSize: '1rem', fontWeight: '600', marginBottom: '0.5rem' }}>
            Loading profiles...
          </div>
          <div style={{ fontSize: '0.875rem' }}>Connecting to CDP stream</div>
        </div>
      </div>
    );
  }

  // Empty state
  if (displayedProfiles.length === 0) {
    return (
      <div
        style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          padding: '2rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
        }}
      >
        <div style={{ textAlign: 'center', color: '#6b7280' }}>
          <div style={{ fontSize: '1rem', fontWeight: '600', marginBottom: '0.5rem' }}>
            No profiles yet
          </div>
          <div style={{ fontSize: '0.875rem' }}>
            Start the CDP simulator to see live profile data
          </div>
          {error && (
            <div style={{ marginTop: '0.5rem', color: '#ef4444', fontSize: '0.75rem' }}>
              Error: {error}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <>
      <div
        style={{
          backgroundColor: 'white',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          height: '600px',
        }}
      >
        {/* Header */}
        <div
          style={{
            padding: '1rem',
            borderBottom: '1px solid #e2e8f0',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <h3
            style={{
              margin: 0,
              fontSize: '1rem',
              fontWeight: '600',
              color: '#374151',
            }}
          >
            Live Profiles
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: connected ? '#10b981' : '#ef4444',
              }}
            />
            <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>
              {displayedProfiles.length} {displayedProfiles.length === 1 ? 'profile' : 'profiles'}
            </span>
          </div>
        </div>

        {/* Table */}
        <div style={{ flex: 1, overflow: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ backgroundColor: '#f9fafb', borderBottom: '1px solid #e5e7eb' }}>
                <th
                  style={{
                    padding: '0.75rem 1rem',
                    textAlign: 'left',
                    fontSize: '0.75rem',
                    fontWeight: '600',
                    color: '#6b7280',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  Profile
                </th>
                <th
                  style={{
                    padding: '0.75rem 1rem',
                    textAlign: 'left',
                    fontSize: '0.75rem',
                    fontWeight: '600',
                    color: '#6b7280',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  Plan
                </th>
                <th
                  style={{
                    padding: '0.75rem 1rem',
                    textAlign: 'left',
                    fontSize: '0.75rem',
                    fontWeight: '600',
                    color: '#6b7280',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  Country
                </th>
                <th
                  style={{
                    padding: '0.75rem 1rem',
                    textAlign: 'left',
                    fontSize: '0.75rem',
                    fontWeight: '600',
                    color: '#6b7280',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  Last Seen
                </th>
                <th
                  style={{
                    padding: '0.75rem 1rem',
                    textAlign: 'left',
                    fontSize: '0.75rem',
                    fontWeight: '600',
                    color: '#6b7280',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                  title="User IDs / Emails / Anonymous IDs"
                >
                  Identifiers (u/e/a)
                </th>
                <th
                  style={{
                    padding: '0.75rem 1rem',
                    textAlign: 'left',
                    fontSize: '0.75rem',
                    fontWeight: '600',
                    color: '#6b7280',
                    textTransform: 'uppercase',
                    letterSpacing: '0.05em',
                  }}
                >
                  Feature Used (24h)
                </th>
              </tr>
            </thead>
            <tbody>
              {displayedProfiles.map((profile) => (
                <ProfileRow
                  key={profile.profileId}
                  profile={profile}
                  onClick={() => setSelectedProfile(profile)}
                />
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Profile Drawer */}
      <ProfileDrawer
        profile={selectedProfile}
        onClose={() => setSelectedProfile(null)}
      />
    </>
  );
}
