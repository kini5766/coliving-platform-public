import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { createRoom, getAdminRoom, updateRoom } from './propertyApi'
import { Card, dangerButton, Field, inputClass, Message, outlineButton, PageShell, primaryButton } from './PropertyUi'
const ROOM_TYPES = ['원룸', '투룸', '쉐어룸', '도미토리']
const LEASE_TYPES = [
  { code: 'MONTHLY', label: '월세' },
  { code: 'SHORT_TERM', label: '단기' },
  { code: 'OTHER', label: '기타' },
]
const FACILITY_CATEGORIES = ['난방', '세탁', '청결', '주방', '생활', '안전', '공간', '제공 비품']
const emptyToNull = (v) => (v.trim() === '' ? null : v.trim())
function RoomForm() {
  const params = useParams()
  const navigate = useNavigate()
  const roomId = params.roomId ? Number(params.roomId) : null
  const isEdit = roomId !== null
  const [buildingId, setBuildingId] = useState(params.buildingId ? Number(params.buildingId) : null)
  const [loading, setLoading] = useState(isEdit)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [roomName, setRoomName] = useState('')
  const [roomType, setRoomType] = useState('')
  const [roomStructure, setRoomStructure] = useState('')
  const [leaseType, setLeaseType] = useState('MONTHLY')
  const [monthlyFee, setMonthlyFee] = useState('')
  const [deposit, setDeposit] = useState('0')
  const [managementFee, setManagementFee] = useState('0')
  const [floor, setFloor] = useState('')
  const [areaM2, setAreaM2] = useState('')
  const [capacity, setCapacity] = useState('1')
  const [facilities, setFacilities] = useState([])
  useEffect(() => {
    if (roomId === null) return
    let cancelled = false
    getAdminRoom(roomId)
      .then((r) => {
        if (cancelled) return
        setBuildingId(r.buildingId)
        setRoomName(r.roomName)
        setRoomType(r.roomType)
        setRoomStructure(r.roomStructure ?? '')
        setLeaseType(r.leaseType)
        setMonthlyFee(r.monthlyFee.toString())
        setDeposit(r.deposit.toString())
        setManagementFee(r.managementFee.toString())
        setFloor(r.floor.toString())
        setAreaM2(r.areaM2.toString())
        setCapacity(r.capacity.toString())
        setFacilities(r.facilities)
      })
      .catch((e) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [roomId])
  const backPath = buildingId !== null ? `/property/buildings/${buildingId}/edit` : '/property/manage'
  const updateFacility = (index, patch) => setFacilities((list) => list.map((f, i) => (i === index ? { ...f, ...patch } : f)))
  const onSubmit = async (e) => {
    e.preventDefault()
    if (buildingId === null) return setError('건물 정보를 찾을 수 없습니다.')
    if (!roomName.trim() || !roomType || monthlyFee === '' || floor === '' || areaM2 === '') {
      return setError('호실명, 방 유형, 월 이용료, 층수, 전용면적은 필수입니다.')
    }
    if (Number(areaM2) <= 0) return setError('전용면적은 0보다 커야 합니다.')
    if (Number(capacity || 1) < 1) return setError('수용 인원은 1 이상이어야 합니다.')
    setSaving(true)
    setError(null)
    try {
      const body = {
        buildingId,
        roomName: roomName.trim(),
        roomType,
        roomStructure: emptyToNull(roomStructure),
        leaseType,
        monthlyFee: Number(monthlyFee),
        deposit: Number(deposit || 0),
        managementFee: Number(managementFee || 0),
        floor: Number(floor),
        areaM2: Number(areaM2),
        capacity: Number(capacity || 1),
        facilities: facilities.filter((f) => f.name.trim() !== '').map((f) => ({ ...f, name: f.name.trim() })),
      }
      if (roomId === null) await createRoom(body)
      else await updateRoom(roomId, body)
      navigate(backPath)
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }
  if (loading)
    return (
      <PageShell title="호실">
        <Message>불러오는 중입니다…</Message>
      </PageShell>
    )
  return (
    <PageShell title={isEdit ? '호실 수정' : '호실 등록'}>
      <form onSubmit={onSubmit}>
        <Card title="호실 정보">
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="호실명" required>
              <input className={inputClass} value={roomName} maxLength={100} onChange={(e) => setRoomName(e.target.value)} />
            </Field>
            <Field label="층수" required>
              <input className={inputClass} type="number" value={floor} onChange={(e) => setFloor(e.target.value)} />
            </Field>
            <Field label="방 유형" required>
              <select className={inputClass} value={roomType} onChange={(e) => setRoomType(e.target.value)}>
                <option value="">선택</option>
                {ROOM_TYPES.map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </Field>
            <Field label="방 구조">
              <input
                className={inputClass}
                value={roomStructure}
                maxLength={100}
                placeholder="예: 분리형"
                onChange={(e) => setRoomStructure(e.target.value)}
              />
            </Field>
            <Field label="전용면적(㎡)" required>
              <input className={inputClass} type="number" step="0.01" min={0} value={areaM2} onChange={(e) => setAreaM2(e.target.value)} />
            </Field>
            <Field label="수용 인원">
              <input className={inputClass} type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
            </Field>
          </div>
        </Card>

        <Card title="이용 조건 (원 단위)">
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="임대 유형" required>
              <select className={inputClass} value={leaseType} onChange={(e) => setLeaseType(e.target.value)}>
                {LEASE_TYPES.map((t) => (
                  <option key={t.code} value={t.code}>
                    {t.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="월 이용료(월세)" required>
              <input className={inputClass} type="number" min={0} value={monthlyFee} onChange={(e) => setMonthlyFee(e.target.value)} />
            </Field>
            <Field label="보증금">
              <input className={inputClass} type="number" min={0} value={deposit} onChange={(e) => setDeposit(e.target.value)} />
            </Field>
            <Field label="관리비">
              <input
                className={inputClass}
                type="number"
                min={0}
                value={managementFee}
                onChange={(e) => setManagementFee(e.target.value)}
              />
            </Field>
          </div>
        </Card>

        <Card
          title="호실 공용시설"
          actions={
            <button
              type="button"
              className={outlineButton}
              onClick={() => setFacilities((l) => [...l, { category: FACILITY_CATEGORIES[0], name: '' }])}
            >
              시설 추가
            </button>
          }
        >
          {facilities.length === 0 && <p className="text-sm text-text/60">추가된 시설이 없습니다.</p>}
          <div className="space-y-2">
            {facilities.map((f, i) => (
              <div key={i} className="flex gap-2">
                <select
                  className={`${inputClass} w-40`}
                  value={f.category}
                  onChange={(e) => updateFacility(i, { category: e.target.value })}
                >
                  {FACILITY_CATEGORIES.map((c) => (
                    <option key={c}>{c}</option>
                  ))}
                </select>
                <input
                  className={inputClass}
                  placeholder="시설명"
                  value={f.name}
                  maxLength={100}
                  onChange={(e) => updateFacility(i, { name: e.target.value })}
                />
                <button type="button" className={dangerButton} onClick={() => setFacilities((l) => l.filter((_, idx) => idx !== i))}>
                  삭제
                </button>
              </div>
            ))}
          </div>
        </Card>

        {error && <p className="mt-4 text-sm text-red-600">{error}</p>}
        <div className="mt-6 flex justify-end gap-2">
          <button type="button" className={outlineButton} onClick={() => navigate(backPath)}>
            취소
          </button>
          <button type="submit" className={primaryButton} disabled={saving}>
            {saving ? '저장 중…' : '저장'}
          </button>
        </div>
      </form>
    </PageShell>
  )
}
export { RoomForm as default }