
import { useEffect, useState } from 'react'
import { getAdminBuildings } from '../property/propertyApi'

const API_BASE_URL = import.meta.env.VITE_BACKEND_API_BASE_URL
const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const INITIAL_RESOURCE = {
    resourceName: '',
    resourceType: 'SPACE',
    description: '',
    resourceStatus: 'AVAILABLE',
    maxUsageHours: 2,
    imageUrl: '',
    location: '',
    capacity: '',
    openingTime: '',
    closingTime: '',
}

function toResourceForm(resource) {
    return {
        resourceName: resource.resourceName ?? '',
        resourceType: resource.resourceType ?? 'SPACE',
        description: resource.description ?? '',
        resourceStatus: resource.resourceStatus ?? 'AVAILABLE',
        maxUsageHours: resource.maxUsageHours ?? 2,
        imageUrl: resource.imageUrl ?? '',
        location: resource.location ?? '',
        capacity: resource.capacity ?? '',
        openingTime: resource.openingTime?.slice(0, 5) ?? '',
        closingTime: resource.closingTime?.slice(0, 5) ?? '',
    }
}

function createResourcePayload(resource, buildingId) {
    return {
        buildingId,
        resourceName: resource.resourceName.trim(),
        resourceType: resource.resourceType,
        description: resource.description.trim(),
        resourceStatus: resource.resourceStatus,
        maxUsageHours: Number(resource.maxUsageHours),
        imageUrl: resource.imageUrl.trim() || null,
        location: resource.location.trim() || null,
        capacity: resource.capacity === '' ? null : Number(resource.capacity),
        openingTime: resource.openingTime || null,
        closingTime: resource.closingTime || null,
    }
}

function validateResource(resource) {
    if (!resource.resourceName.trim()) {
        return '공용 자원명을 입력해 주세요.'
    }

    if (
        resource.openingTime &&
        resource.closingTime &&
        resource.openingTime >= resource.closingTime
    ) {
        return '운영 종료 시간은 시작 시간보다 늦어야 합니다.'
    }

    if (
        resource.capacity !== '' &&
        (!Number.isInteger(Number(resource.capacity)) ||
            Number(resource.capacity) < 1)
    ) {
        return '최대 이용 인원은 1명 이상인 정수여야 합니다.'
    }

    return ''
}

// 이미지 주소를 브라우저에서 사용할 수 있는 주소로 변환
function getImageSrc(imageUrl) {
    if (!imageUrl) return ''

    if (/^https?:\/\//i.test(imageUrl) || imageUrl.startsWith('blob:')) {
        return imageUrl
    }

    if (imageUrl.startsWith('/reservation-service/')) {
        return `${API_BASE_URL || ''}${imageUrl}`
    }

    return imageUrl
}

// 이미지 파일 형식 및 크기 검사
function validateImageFile(file) {
    if (!file) return ''

    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
        return 'JPG, PNG, WebP 이미지 파일만 첨부할 수 있습니다.'
    }

    if (file.size > MAX_IMAGE_SIZE) {
        return '이미지 파일은 5MB 이하여야 합니다.'
    }

    return ''
}

// 이미지 파일을 서버에 업로드
async function uploadResourceImage(file) {
    const formData = new FormData()
    formData.append('file', file)

    const response = await fetch(
        `${API_BASE_URL}/reservation-service/admin/resources/upload-image`,
        {
            method: 'POST',
            credentials: 'include',
            body: formData,
        }
    )

    if (!response.ok) {
        if (response.status === 401 || response.status === 403) {
            throw new Error('이미지 업로드 권한이 없습니다. 관리자 로그인을 확인해 주세요.')
        }

        throw new Error(`이미지 업로드 실패 (${response.status})`)
    }

    const data = await response.json()

    if (!data.imageUrl) {
        throw new Error('서버에서 이미지 URL을 반환하지 않았습니다.')
    }

    return data.imageUrl
}

// 등록 및 수정 공통 입력창
function ResourceFields({
                            resource,
                            onChange,
                            prefix,
                            imageFile,
                            onImageFileChange,
                            disabled,
                        }) {
    const [previewUrl, setPreviewUrl] = useState('')

    useEffect(() => {
        if (!imageFile) {
            setPreviewUrl('')
            return
        }

        const objectUrl = URL.createObjectURL(imageFile)
        setPreviewUrl(objectUrl)

        return () => URL.revokeObjectURL(objectUrl)
    }, [imageFile])

    const imageSrc = previewUrl || getImageSrc(resource.imageUrl)

    return (
        <>
            <label
                htmlFor={`${prefix}-resourceName`}
                className="mb-2 block text-sm font-medium"
            >
                공용 자원명 *
            </label>
            <input
                id={`${prefix}-resourceName`}
                name="resourceName"
                type="text"
                required
                maxLength={100}
                placeholder="예: 회의실 A"
                value={resource.resourceName}
                onChange={onChange}
                disabled={disabled}
                className="mb-4 w-full rounded-lg border p-3"
            />

            <label
                htmlFor={`${prefix}-resourceType`}
                className="mb-2 block text-sm font-medium"
            >
                자원 유형 *
            </label>
            <select
                id={`${prefix}-resourceType`}
                name="resourceType"
                value={resource.resourceType}
                onChange={onChange}
                disabled={disabled}
                className="mb-4 w-full rounded-lg border bg-white p-3"
            >
                <option value="SPACE">공간</option>
                <option value="ITEM">물품</option>
            </select>

            <label
                htmlFor={`${prefix}-description`}
                className="mb-2 block text-sm font-medium"
            >
                자원 설명
            </label>
            <textarea
                id={`${prefix}-description`}
                name="description"
                rows={3}
                placeholder="공용 자원에 대한 설명"
                value={resource.description}
                onChange={onChange}
                disabled={disabled}
                className="mb-4 w-full rounded-lg border p-3"
            />

            <label
                htmlFor={`${prefix}-resourceStatus`}
                className="mb-2 block text-sm font-medium"
            >
                자원 상태 *
            </label>
            <select
                id={`${prefix}-resourceStatus`}
                name="resourceStatus"
                value={resource.resourceStatus}
                onChange={onChange}
                disabled={disabled}
                className="mb-4 w-full rounded-lg border bg-white p-3"
            >
                <option value="AVAILABLE">이용 가능</option>
                <option value="UNAVAILABLE">이용 불가</option>
            </select>

            <label
                htmlFor={`${prefix}-maxUsageHours`}
                className="mb-2 block text-sm font-medium"
            >
                최초 예약 최대 이용 시간 *
            </label>
            <select
                id={`${prefix}-maxUsageHours`}
                name="maxUsageHours"
                value={resource.maxUsageHours}
                onChange={onChange}
                disabled={disabled}
                className="mb-4 w-full rounded-lg border bg-white p-3"
            >
                <option value="1">1시간</option>
                <option value="2">2시간</option>
            </select>

            {/* 이미지 URL 입력 */}
            <label
                htmlFor={`${prefix}-imageUrl`}
                className="mb-2 block text-sm font-medium"
            >
                이미지 URL
            </label>
            <input
                id={`${prefix}-imageUrl`}
                name="imageUrl"
                type="text"
                maxLength={500}
                placeholder="https://example.com/image.jpg"
                value={resource.imageUrl}
                onChange={onChange}
                disabled={disabled}
                className="mb-2 w-full rounded-lg border p-3"
            />

            <p className="mb-3 text-xs text-gray-500">
                이미지 주소를 직접 입력하거나 아래에서 파일을 첨부할 수 있습니다.
            </p>

            {/* 이미지 파일 첨부 */}
            <label
                htmlFor={`${prefix}-imageFile`}
                className="mb-2 block text-sm font-medium"
            >
                또는 이미지 파일 첨부
            </label>
            <input
                id={`${prefix}-imageFile`}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={(e) => onImageFileChange(e.target.files?.[0] ?? null)}
                disabled={disabled}
                className="mb-2 block w-full rounded-lg border bg-white p-3 text-sm"
            />

            <p className="mb-3 text-xs text-gray-500">
                JPG, PNG, WebP · 최대 5MB
            </p>

            {imageFile && (
                <div className="mb-3 flex items-center justify-between gap-3">
                    <p className="min-w-0 truncate text-sm text-[#52796F]">
                        선택한 파일: {imageFile.name}
                    </p>
                    <button
                        type="button"
                        disabled={disabled}
                        onClick={() => {
                            onImageFileChange(null)
                            const input = document.getElementById(`${prefix}-imageFile`)
                            if (input) input.value = ''
                        }}
                        className="shrink-0 rounded-lg border px-3 py-1 text-sm"
                    >
                        파일 선택 취소
                    </button>
                </div>
            )}

            {/* 이미지 미리보기 */}
            {imageSrc && (
                <div className="mb-5 rounded-lg border bg-white p-3">
                    <p className="mb-2 text-sm font-medium">이미지 미리보기</p>
                    <img
                        src={imageSrc}
                        alt="공용 자원 이미지 미리보기"
                        className="h-48 w-full rounded-lg bg-gray-100 object-contain"
                        onError={(e) => {
                            e.currentTarget.style.display = 'none'
                        }}
                    />
                    {imageFile && (
                        <p className="mt-2 text-xs text-gray-500">
                            등록 또는 수정 저장 시 서버에 업로드됩니다.
                        </p>
                    )}
                </div>
            )}

            <label
                htmlFor={`${prefix}-location`}
                className="mb-2 block text-sm font-medium"
            >
                건물 내부 위치
            </label>
            <input
                id={`${prefix}-location`}
                name="location"
                type="text"
                maxLength={100}
                placeholder="예: 2층 공용 공간"
                value={resource.location}
                onChange={onChange}
                disabled={disabled}
                className="mb-4 w-full rounded-lg border p-3"
            />

            <label
                htmlFor={`${prefix}-capacity`}
                className="mb-2 block text-sm font-medium"
            >
                최대 이용 인원
            </label>
            <input
                id={`${prefix}-capacity`}
                name="capacity"
                type="number"
                min="1"
                step="1"
                placeholder="예: 6"
                value={resource.capacity}
                onChange={onChange}
                disabled={disabled}
                className="mb-4 w-full rounded-lg border p-3"
            />

            <div className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                    <label
                        htmlFor={`${prefix}-openingTime`}
                        className="mb-2 block text-sm font-medium"
                    >
                        운영 시작 시간
                    </label>
                    <input
                        id={`${prefix}-openingTime`}
                        name="openingTime"
                        type="time"
                        value={resource.openingTime}
                        onChange={onChange}
                        disabled={disabled}
                        className="w-full rounded-lg border p-3"
                    />
                </div>

                <div>
                    <label
                        htmlFor={`${prefix}-closingTime`}
                        className="mb-2 block text-sm font-medium"
                    >
                        운영 종료 시간
                    </label>
                    <input
                        id={`${prefix}-closingTime`}
                        name="closingTime"
                        type="time"
                        value={resource.closingTime}
                        onChange={onChange}
                        disabled={disabled}
                        className="w-full rounded-lg border p-3"
                    />
                </div>
            </div>
        </>
    )
}

function ResourceManage() {
    // 건물
    const [buildings, setBuildings] = useState([])
    const [search, setSearch] = useState('')
    const [selectedBuilding, setSelectedBuilding] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    // 공용 자원 목록
    const [resources, setResources] = useState([])
    const [resourceLoading, setResourceLoading] = useState(false)
    const [resourceError, setResourceError] = useState('')

    // 등록
    const [showCreateForm, setShowCreateForm] = useState(false)
    const [newResource, setNewResource] = useState({ ...INITIAL_RESOURCE })
    const [newImageFile, setNewImageFile] = useState(null)
    const [saving, setSaving] = useState(false)
    const [createError, setCreateError] = useState('')

    // 수정
    const [editingResourceId, setEditingResourceId] = useState(null)
    const [editResource, setEditResource] = useState({ ...INITIAL_RESOURCE })
    const [editImageFile, setEditImageFile] = useState(null)
    const [editError, setEditError] = useState('')

    // 삭제
    const [deletingResourceId, setDeletingResourceId] = useState(null)
    const [deleteError, setDeleteError] = useState('')

    // 공통
    const busy = saving || deletingResourceId !== null
    const [successMessage, setSuccessMessage] = useState('')
    const [refreshKey, setRefreshKey] = useState(0)

    // 1. 건물 전체 조회
    useEffect(() => {
        let cancelled = false

        async function loadBuildings() {
            try {
                setLoading(true)
                setError('')

                const firstPage = await getAdminBuildings(0, 20)
                const allBuildings = [...(firstPage.content ?? [])]

                for (let page = 1; page < (firstPage.totalPages ?? 1); page++) {
                    const result = await getAdminBuildings(page, 20)
                    allBuildings.push(...(result.content ?? []))
                }

                if (!cancelled) setBuildings(allBuildings)
            } catch (err) {
                if (!cancelled) setError(err.message)
            } finally {
                if (!cancelled) setLoading(false)
            }
        }

        loadBuildings()

        return () => {
            cancelled = true
        }
    }, [])

    // 2. 선택한 건물의 공용 자원 조회
    useEffect(() => {
        if (!selectedBuilding) return

        let cancelled = false

        async function loadResources() {
            try {
                setResourceLoading(true)
                setResourceError('')
                setResources([])

                const response = await fetch(
                    `${API_BASE_URL}/reservation-service/resources?buildingId=${selectedBuilding.id}`,
                    {
                        credentials: 'include',
                        headers: { Accept: 'application/json' },
                    }
                )

                if (!response.ok) {
                    throw new Error(`공용 자원 조회 실패 (${response.status})`)
                }

                const data = await response.json()

                if (!Array.isArray(data)) {
                    throw new Error('공용 자원 응답 형식이 올바르지 않습니다.')
                }

                if (!cancelled) setResources(data)
            } catch (err) {
                if (!cancelled) setResourceError(err.message)
            } finally {
                if (!cancelled) setResourceLoading(false)
            }
        }

        loadResources()

        return () => {
            cancelled = true
        }
    }, [selectedBuilding, refreshKey])

    // 3. 건물 검색
    const filteredBuildings = buildings.filter((building) =>
        String(building.name ?? '')
            .toLowerCase()
            .includes(search.toLowerCase())
    )

    // 4. 건물 선택
    function handleSelectBuilding(building) {
        if (busy) return

        setSelectedBuilding(building)
        setShowCreateForm(false)
        setNewResource({ ...INITIAL_RESOURCE })
        setNewImageFile(null)
        setCreateError('')
        setEditingResourceId(null)
        setEditResource({ ...INITIAL_RESOURCE })
        setEditImageFile(null)
        setEditError('')
        setSuccessMessage('')
        setDeleteError('')
    }

    // 5. 등록 입력값 변경
    function handleResourceChange(e) {
        const { name, value } = e.target

        setNewResource((prev) => ({
            ...prev,
            [name]: value,
        }))

        if (name === 'imageUrl') setNewImageFile(null)
    }

    // 6. 등록 이미지 파일 선택
    function handleNewImageFileChange(file) {
        const validationError = validateImageFile(file)

        if (validationError) {
            setCreateError(validationError)
            setNewImageFile(null)
            return
        }

        setCreateError('')
        setNewImageFile(file)
    }

    // 7. 등록 입력창 열기 / 닫기
    function handleToggleCreateForm() {
        if (busy) return

        setShowCreateForm((prev) => !prev)
        setNewResource({ ...INITIAL_RESOURCE })
        setNewImageFile(null)
        setCreateError('')
        setSuccessMessage('')
        setEditingResourceId(null)
        setEditResource({ ...INITIAL_RESOURCE })
        setEditImageFile(null)
        setEditError('')
    }

    // 8. 공용 자원 등록
    async function handleCreateResource(e) {
        e.preventDefault()

        if (!selectedBuilding || busy) return

        setCreateError('')
        setSuccessMessage('')

        const validationError = validateResource(newResource)

        if (validationError) {
            setCreateError(validationError)
            return
        }

        try {
            setSaving(true)

            const payload = createResourcePayload(
                newResource,
                selectedBuilding.id
            )

            // 파일이 있으면 먼저 업로드하고 URL 저장
            if (newImageFile) {
                payload.imageUrl = await uploadResourceImage(newImageFile)
            }

            const response = await fetch(
                `${API_BASE_URL}/reservation-service/admin/resources`,
                {
                    method: 'POST',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json',
                        Accept: 'application/json',
                    },
                    body: JSON.stringify(payload),
                }
            )

            if (!response.ok) {
                throw new Error(`공용 자원 등록 실패 (${response.status})`)
            }

            setNewResource({ ...INITIAL_RESOURCE })
            setNewImageFile(null)
            setShowCreateForm(false)
            setSuccessMessage('공용 자원이 등록되었습니다.')
            setRefreshKey((prev) => prev + 1)
        } catch (err) {
            setCreateError(err.message || '공용 자원 등록 중 오류가 발생했습니다.')
        } finally {
            setSaving(false)
        }
    }

    // 9. 공용 자원 수정 시작
    function handleEditResource(resource) {
        if (busy) return

        setEditingResourceId(resource.id)
        setEditResource(toResourceForm(resource))
        setEditImageFile(null)
        setEditError('')
        setSuccessMessage('')
        setShowCreateForm(false)
        setNewImageFile(null)
        setCreateError('')
    }

    // 10. 수정 입력값 변경
    function handleEditChange(e) {
        const { name, value } = e.target

        setEditResource((prev) => ({
            ...prev,
            [name]: value,
        }))

        if (name === 'imageUrl') setEditImageFile(null)
    }

    // 11. 수정 이미지 파일 선택
    function handleEditImageFileChange(file) {
        const validationError = validateImageFile(file)

        if (validationError) {
            setEditError(validationError)
            setEditImageFile(null)
            return
        }

        setEditError('')
        setEditImageFile(file)
    }

    // 12. 수정 취소
    function handleCancelEdit() {
        if (busy) return

        setEditingResourceId(null)
        setEditResource({ ...INITIAL_RESOURCE })
        setEditImageFile(null)
        setEditError('')
    }

    // 13. 공용 자원 수정 저장
    async function handleUpdateResource(e) {
        e.preventDefault()

        if (!selectedBuilding || editingResourceId === null || busy) {
            return
        }

        setEditError('')
        setSuccessMessage('')

        const validationError = validateResource(editResource)

        if (validationError) {
            setEditError(validationError)
            return
        }

        try {
            setSaving(true)

            const payload = createResourcePayload(
                editResource,
                selectedBuilding.id
            )

            // 파일이 있으면 먼저 업로드
            if (editImageFile) {
                payload.imageUrl = await uploadResourceImage(editImageFile)
            }

            const response = await fetch(
                `${API_BASE_URL}/reservation-service/admin/resources/${editingResourceId}`,
                {
                    method: 'PATCH',
                    credentials: 'include',
                    headers: {
                        'Content-Type': 'application/json',
                        Accept: 'application/json',
                    },
                    body: JSON.stringify(payload),
                }
            )

            if (!response.ok) {
                throw new Error(`공용 자원 수정 실패 (${response.status})`)
            }

            setEditingResourceId(null)
            setEditResource({ ...INITIAL_RESOURCE })
            setEditImageFile(null)
            setEditError('')
            setSuccessMessage('공용 자원 정보가 수정되었습니다.')
            setRefreshKey((prev) => prev + 1)
        } catch (err) {
            setEditError(err.message || '공용 자원 수정 중 오류가 발생했습니다.')
        } finally {
            setSaving(false)
        }
    }

    // 14. 관리자 공용 자원 삭제
    async function handleDeleteResource(resource) {
        if (busy) return

        const confirmed = window.confirm(
            `"${resource.resourceName}" 공용 자원을 삭제하시겠습니까?\n예약 기록이 있는 자원은 삭제할 수 없습니다.`
        )

        if (!confirmed) return

        setDeleteError('')
        setSuccessMessage('')

        try {
            setDeletingResourceId(resource.id)

            const response = await fetch(
                `${API_BASE_URL}/reservation-service/admin/resources/${resource.id}`,
                {
                    method: 'DELETE',
                    credentials: 'include',
                    headers: { Accept: 'application/json' },
                }
            )

            if (response.status === 409) {
                throw new Error(
                    '예약 기록이 있는 공용 자원은 삭제할 수 없습니다. 이용 불가 상태로 변경해 주세요.'
                )
            }

            if (response.status === 404) {
                throw new Error('삭제할 공용 자원을 찾을 수 없습니다.')
            }

            if (response.status === 401 || response.status === 403) {
                throw new Error(
                    '공용 자원을 삭제할 권한이 없습니다. 관리자 로그인을 확인해 주세요.'
                )
            }

            if (!response.ok) {
                throw new Error(`공용 자원 삭제 실패 (${response.status})`)
            }

            if (editingResourceId === resource.id) {
                setEditingResourceId(null)
                setEditResource({ ...INITIAL_RESOURCE })
                setEditImageFile(null)
                setEditError('')
            }

            setSuccessMessage(
                `"${resource.resourceName}" 공용 자원이 삭제되었습니다.`
            )
            setRefreshKey((prev) => prev + 1)
        } catch (err) {
            setDeleteError(err.message || '공용 자원을 삭제하지 못했습니다.')
        } finally {
            setDeletingResourceId(null)
        }
    }

    return (
        <div className="p-6">
            <h1 className="mb-6 text-2xl font-bold">
                공용 자원 관리
            </h1>

            {/* 건물 검색 */}
            <div className="rounded-xl bg-white p-6 shadow-sm">
                <h2 className="mb-4 text-lg font-semibold">
                    건물 검색
                </h2>

                <input
                    type="text"
                    placeholder="건물명을 입력하세요"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="w-full rounded-lg border p-3"
                />

                {loading && (
                    <p className="mt-4">
                        건물 목록을 불러오는 중입니다...
                    </p>
                )}

                {error && (
                    <p className="mt-4 text-red-600">
                        {error}
                    </p>
                )}

                {!loading && !error && filteredBuildings.length === 0 && (
                    <p className="mt-4 text-gray-500">
                        검색 결과가 없습니다.
                    </p>
                )}

                {!loading && !error && filteredBuildings.length > 0 && (
                    <div className="mt-4 max-h-72 overflow-y-auto">
                        {filteredBuildings.map((building) => (
                            <button
                                key={building.id}
                                type="button"
                                disabled={busy}
                                onClick={() => handleSelectBuilding(building)}
                                className={`mb-2 w-full rounded-lg border p-3 text-left ${
                                    selectedBuilding?.id === building.id
                                        ? 'border-[#52796F] bg-[#F3F5F1]'
                                        : 'hover:bg-gray-50'
                                }`}
                            >
                                <p className="font-semibold">
                                    {building.name}
                                </p>
                                <p className="text-sm text-gray-500">
                                    {building.address}
                                </p>
                            </button>
                        ))}
                    </div>
                )}
            </div>

            {/* 선택한 건물 */}
            {selectedBuilding && (
                <div className="mt-6 rounded-xl bg-white p-6 shadow-sm">
                    <h2 className="text-lg font-semibold">
                        선택한 건물
                    </h2>

                    <p className="mt-3">
                        건물명: {selectedBuilding.name}
                    </p>
                    <p>건물 ID: {selectedBuilding.id}</p>
                    <p>주소: {selectedBuilding.address}</p>

                    {/* 목록 제목 및 등록 버튼 */}
                    <div className="mb-4 mt-6 flex items-center justify-between gap-3">
                        <h3 className="text-lg font-semibold">
                            공용 자원 목록
                        </h3>

                        <button
                            type="button"
                            disabled={busy}
                            onClick={handleToggleCreateForm}
                            className="rounded-lg bg-[#52796F] px-4 py-2 text-white hover:opacity-90 disabled:opacity-50"
                        >
                            {showCreateForm ? '등록 취소' : '+ 공용 자원 등록'}
                        </button>
                    </div>

                    {successMessage && (
                        <p
                            role="status"
                            className="mb-4 rounded-lg bg-green-50 p-3 text-green-700"
                        >
                            {successMessage}
                        </p>
                    )}

                    {deleteError && (
                        <p
                            role="alert"
                            className="mb-4 rounded-lg bg-red-50 p-3 text-red-700"
                        >
                            {deleteError}
                        </p>
                    )}

                    {/* 공용 자원 등록 */}
                    {showCreateForm && (
                        <form
                            onSubmit={handleCreateResource}
                            className="mb-6 rounded-lg border bg-[#F3F5F1] p-5"
                        >
                            <h3 className="mb-4 text-lg font-semibold text-[#26332E]">
                                새 공용 자원 등록
                            </h3>

                            <p className="mb-4 text-sm text-gray-600">
                                등록 건물: {selectedBuilding.name}
                            </p>

                            <ResourceFields
                                resource={newResource}
                                onChange={handleResourceChange}
                                prefix="create"
                                imageFile={newImageFile}
                                onImageFileChange={handleNewImageFileChange}
                                disabled={busy}
                            />

                            {createError && (
                                <p
                                    role="alert"
                                    className="mb-4 text-sm text-red-600"
                                >
                                    {createError}
                                </p>
                            )}

                            <button
                                type="submit"
                                disabled={busy}
                                className="w-full rounded-lg bg-[#52796F] px-4 py-3 font-semibold text-white hover:opacity-90 disabled:opacity-50"
                            >
                                {saving ? '등록 중...' : '공용 자원 등록하기'}
                            </button>
                        </form>
                    )}

                    {resourceLoading && (
                        <p>공용 자원을 불러오는 중입니다...</p>
                    )}

                    {resourceError && (
                        <p className="text-red-600">
                            {resourceError}
                        </p>
                    )}

                    {!resourceLoading &&
                        !resourceError &&
                        resources.length === 0 && (
                            <p className="text-gray-500">
                                등록된 공용 자원이 없습니다.
                            </p>
                        )}

                    {/* 공용 자원 목록 */}
                    {!resourceLoading &&
                        !resourceError &&
                        resources.map((resource) => (
                            <div
                                key={resource.id}
                                className="mb-4 flex flex-col gap-4 rounded-lg border p-4 sm:flex-row"
                            >
                                {/* 공용 자원 이미지 */}
                                <div className="h-44 w-full shrink-0 overflow-hidden rounded-lg bg-[#F3F5F1] sm:w-56">
                                    {resource.imageUrl ? (
                                        <img
                                            src={getImageSrc(resource.imageUrl)}
                                            alt={resource.resourceName}
                                            className="h-full w-full object-cover"
                                            onError={(e) => {
                                                e.currentTarget.style.display = 'none'
                                            }}
                                        />
                                    ) : (
                                        <div className="flex h-full items-center justify-center text-sm text-gray-400">
                                            등록된 사진 없음
                                        </div>
                                    )}
                                </div>

                                {/* 공용 자원 정보 */}
                                <div className="min-w-0 flex-1">
                                    <p className="mb-2 text-lg font-semibold text-[#26332E]">
                                        {resource.resourceName}
                                    </p>

                                    <p className="mb-1 text-sm text-gray-500">
                                        자원 ID: {resource.id}
                                    </p>

                                    <p className="mb-1 text-sm text-gray-500">
                                        유형:{' '}
                                        {resource.resourceType === 'SPACE'
                                            ? '공간'
                                            : resource.resourceType === 'ITEM'
                                                ? '물품'
                                                : resource.resourceType}
                                    </p>

                                    <p className="mb-1 text-sm text-gray-500">
                                        상태:{' '}
                                        {resource.resourceStatus === 'AVAILABLE'
                                            ? '이용 가능'
                                            : resource.resourceStatus === 'UNAVAILABLE'
                                                ? '이용 불가'
                                                : resource.resourceStatus}
                                    </p>

                                    {resource.description && (
                                        <p className="mt-3 text-sm text-gray-600">
                                            {resource.description}
                                        </p>
                                    )}

                                    {/* 수정 / 삭제 버튼 */}
                                    <div className="mt-4 flex flex-wrap gap-2">
                                        <button
                                            type="button"
                                            disabled={busy}
                                            onClick={() => handleEditResource(resource)}
                                            className="rounded-lg border border-[#52796F] px-4 py-2 text-sm font-medium text-[#52796F] hover:bg-[#F3F5F1] disabled:opacity-50"
                                        >
                                            수정
                                        </button>

                                        <button
                                            type="button"
                                            disabled={busy}
                                            onClick={() => handleDeleteResource(resource)}
                                            className="rounded-lg border border-red-500 px-4 py-2 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-50"
                                        >
                                            {deletingResourceId === resource.id
                                                ? '삭제 중...'
                                                : '삭제'}
                                        </button>
                                    </div>

                                    {/* 공용 자원 수정 */}
                                    {editingResourceId === resource.id && (
                                        <form
                                            onSubmit={handleUpdateResource}
                                            className="mt-5 rounded-lg border border-[#52796F] bg-[#F3F5F1] p-5"
                                        >
                                            <h4 className="mb-3 text-lg font-semibold text-[#26332E]">
                                                공용 자원 정보 수정
                                            </h4>

                                            <p className="mb-4 text-sm text-gray-600">
                                                수정 대상: {resource.resourceName}
                                            </p>

                                            <ResourceFields
                                                resource={editResource}
                                                onChange={handleEditChange}
                                                prefix={`edit-${resource.id}`}
                                                imageFile={editImageFile}
                                                onImageFileChange={handleEditImageFileChange}
                                                disabled={busy}
                                            />

                                            {editError && (
                                                <p
                                                    role="alert"
                                                    className="mb-4 text-sm text-red-600"
                                                >
                                                    {editError}
                                                </p>
                                            )}

                                            <div className="flex gap-3">
                                                <button
                                                    type="submit"
                                                    disabled={busy}
                                                    className="flex-1 rounded-lg bg-[#52796F] px-4 py-3 font-semibold text-white hover:opacity-90 disabled:opacity-50"
                                                >
                                                    {saving ? '수정 중...' : '수정 내용 저장'}
                                                </button>

                                                <button
                                                    type="button"
                                                    disabled={busy}
                                                    onClick={handleCancelEdit}
                                                    className="rounded-lg border border-gray-300 bg-white px-4 py-3 font-medium text-[#26332E] hover:bg-gray-50 disabled:opacity-50"
                                                >
                                                    취소
                                                </button>
                                            </div>
                                        </form>
                                    )}
                                </div>
                            </div>
                        ))}
                </div>
            )}
        </div>
    )
}

export default ResourceManage
