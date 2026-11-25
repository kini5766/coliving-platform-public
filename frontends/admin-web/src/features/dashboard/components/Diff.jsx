// 지난달·지난주 대비 증감 표시. 값이 없으면 "비교 데이터 없음"
function Diff({ value, unit, label, goodWhenUp = true }) {
    if (value == null) return <p className="mt-1 text-xs text-text/50">{label} 비교 데이터 없음</p>
    if (value === 0) return <p className="mt-1 text-xs text-text/70">변동 없음 · {label}</p>

    const up = value > 0
    const good = up === goodWhenUp
    return (
        <p className={`mt-1 text-xs ${good ? 'text-main' : 'text-red-600'}`}>
            {up ? '▲' : '▼'} {Math.abs(value)}
            {unit} <span className="text-text/50">{label}</span>
        </p>
    )
}

export default Diff