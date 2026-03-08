import { useState, useEffect } from "react";

export function Ticker({ value, suffix = "", decimals = 0 }: { value: number, suffix?: string, decimals?: number }) {
    const [display, setDisplay] = useState(0);

    useEffect(() => {
        let start = 0;
        const step = value / 30;
        const t = setInterval(() => {
            start += step;
            if (start >= value) {
                setDisplay(value);
                clearInterval(t);
            } else {
                setDisplay(start);
            }
        }, 20);
        return () => clearInterval(t);
    }, [value]);

    return <>{Number(display).toFixed(decimals)}{suffix}</>;
}
