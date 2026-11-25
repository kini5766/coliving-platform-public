import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { deleteBuilding, getAdminBuildings } from './propertyApi'
import { dangerButton, Message, outlineButton, PageShell, primaryButton } from './PropertyUi'
const PAGE_SIZE = 20
function PropertyManage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const load = useCallback(() => {
    setError(null)
    getAdminBuildings(page, PAGE_SIZE)
      .then(setData)
      .catch((e) => setError(e.message))
  }, [page])
  useEffect(load, [load])
  const onDelete = async (b) => {
    if (!window.confirm(`'${b.name}' 건물을 삭제할까요? 소속 호실도 함께 숨겨집니다.`)) return
    try {
      await deleteBuilding(b.id)
      load()
    } catch (e) {
      window.alert(e.message)
    }
  }
  return (
    <PageShell
      title="매물 관리"
      actions={
        <button className={primaryButton} onClick={() => navigate('/property/buildings/new')}>
          건물 등록
        </button>
      }
    >
      {error && <Message>{error}</Message>}
      {!error && !data && <Message>불러오는 중입니다…</Message>}
      {data && data.content.length === 0 && <Message>등록된 건물이 없습니다.</Message>}

      {data && data.content.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-xl bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead className="bg-main text-left text-white">
              <tr>
                <th className="px-4 py-3">건물명</th>
                <th className="px-4 py-3">주소</th>
                <th className="px-4 py-3 text-right">전체 호실</th>
                <th className="px-4 py-3 text-right">입주 가능</th>
                <th className="px-4 py-3">최종 수정</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody>
              {data.content.map((b) => (
                <tr key={b.id} className="border-b border-background last:border-0">
                  <td className="px-4 py-3 font-semibold">
                    <Link to={`/property/buildings/${b.id}/edit`} className="hover:underline">
                      {b.name}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{b.address}</td>
                  <td className="px-4 py-3 text-right">{b.totalRooms}</td>
                  <td className="px-4 py-3 text-right">{b.availableRooms ?? '-'}</td>
                  <td className="px-4 py-3">{b.updatedAt.slice(0, 16).replace('T', ' ')}</td>
                  <td className="space-x-2 px-4 py-3 text-right whitespace-nowrap">
                    <button className={outlineButton} onClick={() => navigate(`/property/buildings/${b.id}/edit`)}>
                      수정
                    </button>
                    <button className={dangerButton} onClick={() => onDelete(b)}>
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && (
        <nav className="mt-6 flex items-center justify-center gap-3 text-sm">
          <button className={outlineButton} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            이전
          </button>
          <span>
            {page + 1} / {Math.max(data.totalPages, 1)}
          </span>
          <button className={outlineButton} disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
            다음
          </button>
        </nav>
      )}
    </PageShell>
  )
}
export { PropertyManage as default }
