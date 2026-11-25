import { useState } from 'react'
import { createForm, deleteForm, updateForm } from '../api/formApi'
import RichTextEditor from './RichTextEditor'
import { placeholderProblems } from '../utils/formVariables'

const ERROR_MESSAGE = {
    400: '입력값을 확인해 주세요.',
    404: '양식을 찾을 수 없어요. 이미 삭제됐을 수 있어요.',
}

const LANGUAGES = [
    { value: 'ko', label: '한글' },
    { value: 'en', label: '영어' },
]

// form이 null이면 새 양식 생성, 값이 있으면 그 양식 수정.
// preset은 새 양식을 만들 때 미리 채울 문안(사전 양식). 없으면 빈 양식.
function FormEditor({ form, preset, onClose, onSaved }) {
    const isNew = form === null
    const initialContent = form?.content ?? preset?.content ?? ''
    const [language, setLanguage] = useState(form?.language ?? preset?.language ?? 'ko')
    const [title, setTitle] = useState(form?.title ?? preset?.title ?? '')
    const [content, setContent] = useState(initialContent) // 에디터가 내는 HTML
    const [contentEmpty, setContentEmpty] = useState(!initialContent.trim())
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')

    async function run(action) {
        setSaving(true)
        setError('')
        try {
            await action()
            onSaved()
        } catch (e) {
            // 400은 서버가 알려준 이유(틀린 빈칸 이름 등)를 우선 보여준다
            const serverMessage = e.status === 400 ? e.serverMessage : ''
            setError(serverMessage || ERROR_MESSAGE[e.status] || '요청에 실패했어요. 잠시 후 다시 시도해 주세요.')
            setSaving(false)
        }
    }

    function handleSave() {
        // 빈칸이 잘못된 양식이 저장되면 그때부터 모든 신청서 제출이 실패하므로 먼저 막는다 (서버도 한 번 더 검사)
        const problems = placeholderProblems(content)
        if (problems.length > 0) {
            setError(problems.join(' '))
            return
        }
        run(() => (isNew ? createForm({ language, title, content }) : updateForm(form.id, { title, content })))
    }

    function handleDelete() {
        if (window.confirm('이 양식을 삭제할까요?')) {
            run(() => deleteForm(form.id))
        }
    }

    const canSave = !saving && title.trim() && !contentEmpty
    const inputClass =
        'mt-2 w-full rounded-lg border border-main/20 bg-white px-4 py-2.5 text-sm outline-none focus:border-main'

    return (
        <>
            <button type="button" onClick={onClose} className="text-sm text-main hover:underline">
                ← 목록으로
            </button>
            <h1 className="mt-2 text-2xl font-semibold text-main sm:text-3xl">
                {isNew ? '양식 생성' : '양식 편집'}
            </h1>

            <section className="mt-6 max-w-5xl space-y-6 rounded-lg bg-white p-6 shadow-sm">
                <div>
                    <span className="text-sm font-medium">양식 언어</span>
                    <div className="mt-2 grid grid-cols-2 gap-2">
                        {LANGUAGES.map((item) => (
                            <button
                                key={item.value}
                                type="button"
                                disabled={!isNew}
                                onClick={() => setLanguage(item.value)}
                                className={`rounded-lg border px-4 py-2.5 text-sm ${
                                    language === item.value
                                        ? 'border-main bg-main text-background'
                                        : 'border-main/20 bg-white text-text'
                                } disabled:cursor-not-allowed ${!isNew && language !== item.value ? 'opacity-40' : ''}`}
                            >
                                {item.label}
                            </button>
                        ))}
                    </div>
                    {!isNew && <p className="mt-1 text-xs text-text/60">언어는 만든 뒤에 바꿀 수 없어요.</p>}
                </div>

                <div>
                    <label className="text-sm font-medium">제목</label>
                    <input
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        maxLength={200}
                        className={inputClass}
                    />
                </div>

                <div>
                    <span className="text-sm font-medium">내용</span>
                    <RichTextEditor
                        initialContent={initialContent}
                        onChange={(html, empty) => {
                            setContent(html)
                            setContentEmpty(empty)
                        }}
                    />
                    <p className="mt-1 text-xs text-text/60">
                        빈칸은 오른쪽 목록에서 눌러 넣어요. 채울 값은 계약 서비스의 신청 정보(contract_application)를 따라요.
                    </p>
                </div>

                {error && <p className="rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700">{error}</p>}

                <div className="grid grid-cols-2 gap-3">
                    <button
                        type="button"
                        onClick={onClose}
                        disabled={saving}
                        className="rounded-lg border border-main/30 px-4 py-2.5 text-sm hover:bg-background"
                    >
                        취소
                    </button>
                    <button
                        type="button"
                        onClick={handleSave}
                        disabled={!canSave}
                        className="rounded-lg bg-main px-4 py-2.5 text-sm font-medium text-background hover:bg-main/90 disabled:opacity-40"
                    >
                        저장
                    </button>
                </div>

                {!isNew && (
                    <button
                        type="button"
                        onClick={handleDelete}
                        disabled={saving}
                        className="w-full rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700 hover:bg-red-100"
                    >
                        삭제
                    </button>
                )}
            </section>
        </>
    )
}

export default FormEditor