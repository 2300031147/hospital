export function MapDot({ x, y, color, pulse, label, size = 8 }: { key?: string | number, x: number, y: number, color: string, pulse?: boolean, label?: string, size?: number }) {
    return (
        <g>
            {pulse && (
                <circle cx={x} cy={y} r={size + 6} fill={color} opacity={0.15}>
                    <animate attributeName="r" values={`${size + 2};${size + 10};${size + 2}`} dur="2s" repeatCount="indefinite" />
                    <animate attributeName="opacity" values="0.3;0;0.3" dur="2s" repeatCount="indefinite" />
                </circle>
            )}
            <circle cx={x} cy={y} r={size} fill={color} opacity={0.9} />
            <circle cx={x} cy={y} r={size - 2} fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1" />
            {label && (
                <text x={x + size + 4} y={y + 4} fill="rgba(255,255,255,0.7)" fontSize="9" fontFamily="monospace">{label}</text>
            )}
        </g>
    );
}
