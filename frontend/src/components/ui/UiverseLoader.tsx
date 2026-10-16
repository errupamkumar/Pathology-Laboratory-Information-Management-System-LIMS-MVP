import React from 'react';
import { Activity } from 'lucide-react';

interface UiverseLoaderProps {
  label?: string;
  message?: string;
  size?: 'sm' | 'md' | 'lg';
}

export const UiverseLoader: React.FC<UiverseLoaderProps> = ({
  label,
  message,
  size = 'md'
}) => {
  const text = message || label || 'Processing clinical diagnostic data...';
  const scale = size === 'sm' ? 'scale-75' : size === 'lg' ? 'scale-125' : 'scale-100';

  return (
    <div className="flex flex-col items-center justify-center py-8 space-y-4">
      <div className={`relative flex items-center justify-center ${scale}`}>
        {/* brave-moose-56 dual orbital spinning rings */}
        <div className="moose-loader"></div>
        {/* Center heartbeat icon */}
        <div className="absolute text-brand-primary animate-pulse">
          <Activity className="w-5 h-5" />
        </div>
      </div>
      {text && (
        <p className="text-xs font-semibold text-slate-600 tracking-wide animate-pulse">
          {text}
        </p>
      )}
    </div>
  );
};
