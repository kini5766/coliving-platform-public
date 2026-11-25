import { monthText, recentMonths } from '../utils/buildings'

const MONTHS = recentMonths(6)

const selectClass =
    'rounded-md border-2 border-main bg-white px-3 py-1.5 text-sm text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'

function FilterBar({ buildings, buildingId, month, onBuildingChange, onMonthChange }) {
    return (
        <div className="flex gap-2">
            <select
                aria-label="건물 선택"
                className={selectClass}
                value={buildingId ?? ''}
                disabled={buildings.length === 0}
                onChange={(e) => onBuildingChange(Number(e.target.value))}
            >
                {buildings.length === 0 ? (
                    <option value="">등록된 건물이 없습니다</option>
                ) : (
                    buildings.map((b) => (
                        <option key={b.id} value={b.id}>
                            {b.name}
                        </option>
                    ))
                )}
            </select>
            <select
                aria-label="조회 월 선택"
                className={selectClass}
                value={month}
                onChange={(e) => onMonthChange(e.target.value)}
            >
                {MONTHS.map((m) => (
                    <option key={m} value={m}>
                        {monthText(m)}
                    </option>
                ))}
            </select>
        </div>
    )
}

export default FilterBar