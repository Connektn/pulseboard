import { useState } from 'react';
import { ProfilesList } from './ProfilesList';
import { SegmentFeed } from './SegmentFeed';
import { CdpKpiPanel } from './CdpKpiPanel';

type TabType = 'profiles' | 'segments';

interface CdpTabsProps {
  isSimulatorRunning: boolean;
}

export function CdpTabs({ isSimulatorRunning }: CdpTabsProps) {
  const [activeTab, setActiveTab] = useState<TabType>('profiles');

  return (
    <>
      {/* KPI Panel */}
      <CdpKpiPanel isSimulatorRunning={isSimulatorRunning} />

      {/* Tabs */}
      <div
        style={{
          backgroundColor: 'white',
          borderRadius: '0.5rem',
          boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
          overflow: 'hidden',
        }}
      >
      {/* Tab Headers */}
      <div
        style={{
          display: 'flex',
          borderBottom: '1px solid #e2e8f0',
          backgroundColor: '#f9fafb',
        }}
      >
        <button
          onClick={() => setActiveTab('profiles')}
          style={{
            flex: 1,
            padding: '1rem',
            backgroundColor: activeTab === 'profiles' ? 'white' : 'transparent',
            border: 'none',
            borderBottom: activeTab === 'profiles' ? '2px solid #3b82f6' : '2px solid transparent',
            color: activeTab === 'profiles' ? '#3b82f6' : '#6b7280',
            fontWeight: activeTab === 'profiles' ? '600' : '500',
            fontSize: '0.875rem',
            cursor: 'pointer',
            transition: 'all 0.2s ease',
          }}
        >
          Live Profiles
        </button>
        <button
          onClick={() => setActiveTab('segments')}
          style={{
            flex: 1,
            padding: '1rem',
            backgroundColor: activeTab === 'segments' ? 'white' : 'transparent',
            border: 'none',
            borderBottom: activeTab === 'segments' ? '2px solid #3b82f6' : '2px solid transparent',
            color: activeTab === 'segments' ? '#3b82f6' : '#6b7280',
            fontWeight: activeTab === 'segments' ? '600' : '500',
            fontSize: '0.875rem',
            cursor: 'pointer',
            transition: 'all 0.2s ease',
          }}
        >
          Segment Activity Feed
        </button>
      </div>

      {/* Tab Content */}
      <div style={{ padding: 0 }}>
        {activeTab === 'profiles' && <ProfilesList isSimulatorRunning={isSimulatorRunning} />}
        {activeTab === 'segments' && <SegmentFeed isSimulatorRunning={isSimulatorRunning} />}
      </div>
    </div>
    </>
  );
}
