'use client';

import React from 'react';

interface ExpressiveProgressProps {
  value: number; // 0 to 100+
  overBudget?: boolean;
  height?: number;
}

/**
 * Android 16 Material 3 Expressive LinearWavyProgressIndicator
 * Combines a sinusoidal wavy active stroke with a tonal remaining track and stop dot.
 */
export function ExpressiveProgress({
  value,
  overBudget = false,
  height = 16,
}: ExpressiveProgressProps) {
  const clamped = Math.max(0, Math.min(100, value));
  const strokeColor = overBudget ? 'var(--md-warning)' : 'var(--md-primary-vibrant)';

  // Build smooth SVG sine wave across viewBox 0..400
  const totalWidth = 400;
  const activeWidth = (clamped / 100) * totalWidth;
  const midY = 8;
  const amp = clamped > 2 && clamped < 99 ? 3.2 : 1.6;
  const wavelength = 24;

  let wavePath = `M 4 ${midY}`;
  if (activeWidth > 6) {
    const endX = Math.max(8, activeWidth - 4);
    for (let x = 4; x <= endX; x += 2) {
      const y = midY + Math.sin(((x - 4) / wavelength) * Math.PI * 2) * amp;
      wavePath += ` L ${x.toFixed(1)} ${y.toFixed(2)}`;
    }
  }

  const trackStartX = Math.min(totalWidth - 4, activeWidth + 8);

  return (
    <div style={{ width: '100%', height: `${height}px`, display: 'flex', alignItems: 'center' }}>
      <svg
        viewBox={`0 0 ${totalWidth} 16`}
        preserveAspectRatio="none"
        style={{ width: '100%', height: '16px', overflow: 'visible' }}
        aria-hidden="true"
      >
        {/* Remaining Track */}
        {clamped < 98 && (
          <line
            x1={clamped <= 1 ? 4 : trackStartX}
            y1={midY}
            x2={totalWidth - 4}
            y2={midY}
            stroke="var(--md-surface-container-lowest)"
            strokeWidth="5"
            strokeLinecap="round"
          />
        )}

        {/* Active Expressive Wave */}
        {clamped > 0 && (
          <path
            d={wavePath}
            fill="none"
            stroke={strokeColor}
            strokeWidth="5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        )}

        {/* End Stop Indicator Dot (M3 Expressive spec) */}
        {clamped < 97 && (
          <circle
            cx={totalWidth - 4}
            cy={midY}
            r="2.6"
            fill={strokeColor}
          />
        )}
      </svg>
    </div>
  );
}
