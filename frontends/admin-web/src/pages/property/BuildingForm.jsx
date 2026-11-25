import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  createBuilding,
  deleteBuildingImage,
  deleteRoom,
  getAdminBuilding,
  imageSrc,
  saveContractForms,
  updateBuilding,
  uploadBuildingImage,
} from './propertyApi'
import { fetchForms } from '../../features/document/api/formApi'
import { Card, dangerButton, Field, inputClass, Message, outlineButton, PageShell, primaryButton } from './PropertyUi'
const BUILDING_TYPES = ['빌라', '연립', '다세대', '오피스텔', '아파트', '기타']
const FACILITY_CATEGORIES = ['난방', '세탁', '청결', '주방', '생활', '안전', '공간', '제공 비품']
const LANGUAGES = [
  { code: 'ko', label: '한국어' },
  { code: 'en', label: 'English' },
]
const emptyToNull = (v) => (v.trim() === '' ? null : v.trim())
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
function BuildingForm() {
  const { buildingId } = useParams()
  const navigate = useNavigate()
  const id = buildingId ? Number(buildingId) : null
  const isEdit = id !== null
  const [loading, setLoading] = useState(isEdit)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [region, setRegion] = useState('')
  const [buildingType, setBuildingType] = useState('')
  const [totalFloors, setTotalFloors] = useState('')
  const [parking, setParking] = useState(false)
  const [elevator, setElevator] = useState(false)
  const [introduction, setIntroduction] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [facilities, setFacilities] = useState([])
  const [formIds, setFormIds] = useState({ ko: '', en: '' })
  const [rooms, setRooms] = useState([])
  // 대표 이미지: 서버에 저장된 URL, 새로 고른 파일(저장 버튼을 누를 때 업로드), 삭제 예약 여부
  const [imageUrl, setImageUrl] = useState(null)
  const [imageFile, setImageFile] = useState(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [removeImage, setRemoveImage] = useState(false)
  const [imageError, setImageError] = useState(null)
  useEffect(() => {
    if (!imageFile) return
    const url = URL.createObjectURL(imageFile)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [imageFile])
  // 문서 서비스에 등록된 계약서 양식 목록 (선택 목록에 표시)
  const [documentForms, setDocumentForms] = useState([])
  const [formsError, setFormsError] = useState(false)
  useEffect(() => {
    let cancelled = false
    fetchForms()
      .then((list) => !cancelled && setDocumentForms(Array.isArray(list) ? list : []))
      .catch(() => !cancelled && setFormsError(true))
    return () => {
      cancelled = true
    }
  }, [])
  useEffect(() => {
    if (id === null) return
    let cancelled = false
    getAdminBuilding(id)
      .then((b) => {
        if (cancelled) return
        setName(b.name)
        setAddress(b.address)
        setRegion(b.region)
        setBuildingType(b.buildingType)
        setTotalFloors(b.totalFloors.toString())
        setParking(b.parkingAvailable)
        setElevator(b.elevatorAvailable)
        setIntroduction(b.introduction ?? '')
        setContactPhone(b.contactPhone ?? '')
        setFacilities(b.facilities)
        setFormIds({
          ko: b.contractForms.find((f) => f.language === 'ko')?.formId.toString() ?? '',
          en: b.contractForms.find((f) => f.language === 'en')?.formId.toString() ?? '',
        })
        setRooms(b.rooms)
        setImageUrl(b.imageUrl ?? null)
      })
      .catch((e) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [id])
  const onSubmit = async (e) => {
    e.preventDefault()
    if (!name.trim() || !address.trim() || !region.trim() || !buildingType || totalFloors === '') {
      setError('건물명, 주소, 지역, 건물 형태, 전체 층수는 필수입니다.')
      return
    }
    if (Number(totalFloors) < 1) {
      setError('전체 층수는 1 이상이어야 합니다.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const body = {
        name: name.trim(),
        address: address.trim(),
        region: region.trim(),
        buildingType,
        totalFloors: Number(totalFloors),
        parkingAvailable: parking,
        elevatorAvailable: elevator,
        introduction: emptyToNull(introduction),
        contactPhone: emptyToNull(contactPhone),
        facilities: facilities.filter((f) => f.name.trim() !== '').map((f) => ({ ...f, name: f.name.trim() })),
      }
      let savedId = id
      if (id === null) {
        savedId = (await createBuilding(body)).id
      } else {
        await updateBuilding(id, body)
      }
      const forms = LANGUAGES.filter((l) => formIds[l.code] !== '').map((l) => ({
        language: l.code,
        formId: Number(formIds[l.code]),
      }))
      await saveContractForms(savedId, forms)
      if (imageFile) {
        await uploadBuildingImage(savedId, imageFile)
      } else if (removeImage && imageUrl) {
        await deleteBuildingImage(savedId)
      }
      navigate('/property/manage')
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }
  const onPickImage = (e) => {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일을 다시 골라도 반응하도록 비운다
    if (!file) return
    if (!IMAGE_TYPES.includes(file.type)) {
      setImageError('jpg, png, webp, gif 이미지만 올릴 수 있습니다.')
      return
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setImageError('이미지는 5MB 이하만 올릴 수 있습니다.')
      return
    }
    setImageError(null)
    setRemoveImage(false)
    setImageFile(file)
  }
  const onRemoveImage = () => {
    setImageFile(null)
    setPreviewUrl(null)
    setRemoveImage(true)
    setImageError(null)
  }
  const shownImage = imageFile ? previewUrl : removeImage ? null : imageSrc(imageUrl)
  const updateFacility = (index, patch) => setFacilities((list) => list.map((f, i) => (i === index ? { ...f, ...patch } : f)))
  const onDeleteRoom = async (room) => {
    if (!window.confirm(`'${room.roomName}' 호실을 삭제할까요?`)) return
    try {
      await deleteRoom(room.id)
      setRooms((list) => list.filter((r) => r.id !== room.id))
    } catch (err) {
      window.alert(err.message)
    }
  }
  if (loading)
    return (
      <PageShell title="건물">
        <Message>불러오는 중입니다…</Message>
      </PageShell>
    )
  return (
    <PageShell
      title={isEdit ? '건물 수정' : '건물 등록'}
      actions={
        <Link to="/property/manage" className="text-sm text-main">
          ← 목록
        </Link>
      }
    >
      <form onSubmit={onSubmit}>
        <Card title="대표 이미지">
          <div className="flex flex-col gap-4 md:flex-row md:items-start">
            <div className="flex aspect-video w-full items-center justify-center overflow-hidden rounded-lg bg-background md:w-96">
              {shownImage ? (
                <img src={shownImage} alt="건물 대표 이미지" className="h-full w-full object-cover" />
              ) : (
                <span className="text-sm text-text/50">등록된 대표 이미지가 없습니다.</span>
              )}
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex gap-2">
                <label className={`${outlineButton} cursor-pointer`}>
                  {shownImage ? '이미지 변경' : '이미지 선택'}
                  <input type="file" accept={IMAGE_TYPES.join(',')} className="hidden" onChange={onPickImage} />
                </label>
                {shownImage && (
                  <button type="button" className={dangerButton} onClick={onRemoveImage}>
                    이미지 삭제
                  </button>
                )}
              </div>
              <p className="text-xs text-text/60">jpg, png, webp, gif · 5MB 이하. 저장 버튼을 눌러야 반영됩니다.</p>
              {imageFile && <p className="text-xs text-main">새 이미지: {imageFile.name}</p>}
              {removeImage && imageUrl && <p className="text-xs text-red-600">저장하면 대표 이미지가 삭제됩니다.</p>}
              {imageError && <p className="text-xs text-red-600">{imageError}</p>}
            </div>
          </div>
        </Card>

        <Card title="기본 정보">
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="건물명" required>
              <input className={inputClass} value={name} maxLength={200} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label="문의 연락처">
              <input className={inputClass} value={contactPhone} maxLength={30} onChange={(e) => setContactPhone(e.target.value)} />
            </Field>
            <Field label="주소" required>
              <input className={inputClass} value={address} maxLength={500} onChange={(e) => setAddress(e.target.value)} />
            </Field>
            <Field label="지역 (검색용)" required>
              <input
                className={inputClass}
                value={region}
                maxLength={100}
                placeholder="예: 마포구"
                onChange={(e) => setRegion(e.target.value)}
              />
            </Field>
            <Field label="건물 형태" required>
              <select className={inputClass} value={buildingType} onChange={(e) => setBuildingType(e.target.value)}>
                <option value="">선택</option>
                {BUILDING_TYPES.map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </Field>
            <Field label="전체 층수" required>
              <input className={inputClass} type="number" min={1} value={totalFloors} onChange={(e) => setTotalFloors(e.target.value)} />
            </Field>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={parking} onChange={(e) => setParking(e.target.checked)} /> 주차 가능
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={elevator} onChange={(e) => setElevator(e.target.checked)} /> 엘리베이터
            </label>
            <div className="md:col-span-2">
              <Field label="건물 소개">
                <textarea className={inputClass} rows={5} value={introduction} onChange={(e) => setIntroduction(e.target.value)} />
              </Field>
            </div>
          </div>
        </Card>

        <Card
          title="공용시설"
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

        <Card title="언어별 계약서 양식">
          <p className="mb-3 text-xs text-text/60">
            문서 양식에 등록된 양식 중에서 고릅니다. 선택 안 함으로 두면 해당 언어는 설정하지 않습니다.
          </p>
          {formsError && (
            <p className="mb-3 text-xs text-red-600">문서 양식 목록을 불러오지 못했습니다. 문서 서비스가 실행 중인지 확인하세요.</p>
          )}
          <div className="grid gap-4 md:grid-cols-2">
            {LANGUAGES.map((l) => {
              const options = documentForms.filter((f) => f.language === l.code)
              const current = formIds[l.code]
              // 저장된 양식이 목록에 없을 때(삭제됐거나 목록을 못 불러온 경우)도 값이 사라지지 않게 표시
              const missing = current !== '' && !options.some((f) => String(f.id) === current)
              return (
                <Field key={l.code} label={`${l.label} (${l.code}) 계약서 양식`}>
                  <select
                    className={inputClass}
                    value={current}
                    onChange={(e) => setFormIds((m) => ({ ...m, [l.code]: e.target.value }))}
                  >
                    <option value="">선택 안 함</option>
                    {missing && <option value={current}>{`양식 ID ${current} (목록에 없음)`}</option>}
                    {options.map((f) => (
                      <option key={f.id} value={String(f.id)}>
                        {f.title}
                      </option>
                    ))}
                  </select>
                </Field>
              )
            })}
          </div>
        </Card>

        {error && <p className="mt-4 text-sm text-red-600">{error}</p>}
        <div className="mt-6 flex justify-end gap-2">
          <button type="button" className={outlineButton} onClick={() => navigate('/property/manage')}>
            취소
          </button>
          <button type="submit" className={primaryButton} disabled={saving}>
            {saving ? '저장 중…' : '저장'}
          </button>
        </div>
      </form>

      {isEdit && (
        <Card
          title={`호실 (${rooms.length})`}
          actions={
            <button className={primaryButton} onClick={() => navigate(`/property/buildings/${id}/rooms/new`)}>
              호실 등록
            </button>
          }
        >
          {rooms.length === 0 ? (
            <p className="text-sm text-text/60">등록된 호실이 없습니다.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-text/60">
                <tr>
                  <th className="py-2">호실</th>
                  <th>방 유형</th>
                  <th className="text-right">월 이용료</th>
                  <th className="text-right">보증금</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {rooms.map((r) => (
                  <tr key={r.id} className="border-t border-background">
                    <td className="py-2 font-semibold">{r.roomName}</td>
                    <td>{r.roomType}</td>
                    <td className="text-right">{r.monthlyFee.toLocaleString('ko-KR')}원</td>
                    <td className="text-right">{r.deposit.toLocaleString('ko-KR')}원</td>
                    <td className="space-x-2 text-right whitespace-nowrap">
                      <button className={outlineButton} onClick={() => navigate(`/property/rooms/${r.id}/edit`)}>
                        수정
                      </button>
                      <button className={dangerButton} onClick={() => onDeleteRoom(r)}>
                        삭제
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      )}
    </PageShell>
  )
}
export { BuildingForm as default }