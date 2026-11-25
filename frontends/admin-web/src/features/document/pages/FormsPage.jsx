import { useCallback, useEffect, useMemo, useState } from 'react'
import { useLocation } from 'react-router'
import { ChevronRight } from 'lucide-react'
import { fetchForms } from '../api/formApi'
import FormEditor from '../components/FormEditor'
import { PRESET_FORMS } from '../utils/presetForms'

const LANGUAGE_LABEL = { ko: '한글', en: '영어' }

function formatDate(iso) {
    const date = new Date(iso)
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${date.getFullYear()}.${month}.${day}`
}

// 계약 서비스가 언어별로 실제 쓰는 양식의 id 집합.
// 계약 서비스(DocumentQueryService.getLatestFormForContract)와 같은 규칙: 같은 언어, 본문이 비어 있지 않은 양식 중 id가 가장 큰 것.
function findActiveFormIds(forms) {
    const active = new Set()
    for (const language of Object.keys(LANGUAGE_LABEL)) {
        let latest = null
        for (const form of forms) {
            if (form.language !== language) continue
            if (!form.content || !form.content.trim()) continue
            if (latest == null || form.id > latest.id) latest = form
        }
        if (latest != null) active.add(latest.id)
    }
    return active
}

function FormsView({ title }) {
    const [forms, setForms] = useState([])
    const [keyword, setKeyword] = useState('')
    const [status, setStatus] = useState('loading') // loading | done | error
    const [editing, setEditing] = useState(null) // null: 목록, 'new': 새 양식, 양식 객체: 수정
    const [preset, setPreset] = useState(null) // 사전 양식으로 생성할 때 채울 값

    const load = useCallback(async () => {
        try {
            setForms(await fetchForms())
            setStatus('done')
        } catch {
            setStatus('error')
        }
    }, [])

    useEffect(() => {
        document.title = title
        load()
    }, [title, load])

    // 검색으로 걸러지기 전의 전체 목록으로 계산한다. (검색어에 따라 배지가 바뀌면 안 된다)
    const activeFormIds = useMemo(() => findActiveFormIds(forms), [forms])

    // 새 양식 화면을 연다. nextPreset이 null이면 빈 양식, 값이 있으면 그 문안으로 채운다.
    function startCreate(nextPreset) {
        setPreset(nextPreset)
        setEditing('new')
    }

    if (editing) {
        return (
            <FormEditor
                form={editing === 'new' ? null : editing}
                preset={preset}
                onClose={() => setEditing(null)}
                onSaved={() => {
                    setEditing(null)
                    load()
                }}
            />
        )
    }

    const shown = forms.filter((form) => form.title.toLowerCase().includes(keyword.trim().toLowerCase()))

    return (
        <div className="max-w-5xl">
            <div className="flex items-start justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-semibold text-main sm:text-3xl">{title}</h1>
                    <p className="mt-1 text-sm text-text/70">계약서 양식을 관리해요.</p>
                </div>
                <button
                    type="button"
                    onClick={() => startCreate(null)}
                    className="cursor-pointer rounded-lg bg-main px-4 py-2 text-sm font-medium text-background shadow-sm transition duration-200 hover:bg-main/90 hover:shadow-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-main active:scale-95"
                >
                    + 생성
                </button>
            </div>

            <section className="mt-6 rounded-lg bg-white p-4 shadow-sm">
                <p className="text-sm font-medium">사전 양식으로 생성</p>
                <div className="mt-3 grid grid-cols-2 gap-2">
                    {PRESET_FORMS.map((item) => (
                        <button
                            key={item.language}
                            type="button"
                            onClick={() => startCreate(item)}
                            className="cursor-pointer rounded-lg border border-main/20 bg-background px-4 py-2.5 text-sm transition duration-200 hover:border-main hover:bg-main/10 hover:text-main focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-main active:scale-[0.98]"
                        >
                            {item.label}
                        </button>
                    ))}
                </div>
            </section>

            <input
                type="search"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="양식 이름 검색"
                className="mt-4 w-full rounded-lg border border-main/20 bg-white px-4 py-2.5 text-sm outline-none transition-colors hover:border-main/50 focus:border-main"
            />

            <p className="mt-3 text-xs text-text/60">
                '사용 중' 표시는 새 계약서에 실제로 쓰이는 양식이에요. 언어별로 가장 나중에 등록한 양식이 쓰이고, 예전 양식을 고쳐도 바뀌지 않아요.
            </p>

            <section className="mt-3 space-y-3">
                {status === 'loading' && <p className="py-10 text-center text-sm text-text/70">불러오는 중이에요.</p>}
                {status === 'error' && (
                    <p className="py-10 text-center text-sm text-red-700">양식을 불러오지 못했어요.</p>
                )}
                {status === 'done' && shown.length === 0 && (
                    <p className="py-10 text-center text-sm text-text/70">표시할 양식이 없어요.</p>
                )}
                {shown.map((form) => (
                    <button
                        key={form.id}
                        type="button"
                        onClick={() => setEditing(form)}
                        className="group flex w-full cursor-pointer items-center justify-between rounded-lg border border-transparent bg-white p-4 text-left shadow-sm transition duration-200 ease-out hover:-translate-y-0.5 hover:border-main/40 hover:shadow-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-main active:translate-y-0 active:shadow-sm motion-reduce:transition-none motion-reduce:hover:translate-y-0"
                    >
                        <div>
                            <div className="flex items-center gap-2">
                                <span className="font-medium transition-colors group-hover:text-main">
                                    {form.title}
                                </span>
                                <span className="rounded bg-main/10 px-2 py-0.5 text-xs text-main">
                                    {LANGUAGE_LABEL[form.language]}
                                </span>
                                {activeFormIds.has(form.id) && (
                                    <span className="rounded bg-main px-2 py-0.5 text-xs text-background">
                                        사용 중
                                    </span>
                                )}
                            </div>
                            <p className="mt-1 text-xs text-text/60">최종 수정 {formatDate(form.updatedAt)}</p>
                        </div>
                        <ChevronRight
                            size={18}
                            className="text-text/40 transition duration-200 group-hover:translate-x-1 group-hover:text-main"
                        />
                    </button>
                ))}
            </section>
        </div>
    )
}

// 사이드바에서 같은 메뉴를 다시 누르면 location.key가 바뀐다. 그 값을 key로 줘서 화면을 새로 만들고, 목록으로 돌아가게 한다.
function FormsPage(props) {
    const location = useLocation()
    return <FormsView key={location.key} {...props} />
}

export default FormsPage