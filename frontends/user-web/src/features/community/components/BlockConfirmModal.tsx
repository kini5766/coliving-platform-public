// 차단 확인 창 — 사용자 차단 후 상위 화면 갱신
import { useState } from "react"
import { createPortal } from "react-dom"
import { useTranslation } from "react-i18next"
import { createBlock } from "../api"
import { Button, Icon } from "."

export function BlockConfirmModal({ name, blockedUserId, onClose, onBlocked }: {
  name: string;
  blockedUserId: number;
  onClose: () => void;
  onBlocked: () => void
}) {
  const { t } = useTranslation("community")
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false)
  // handleBlock — POST /blocks
  const handleBlock = async () => {
    setSubmitting(true);
    setError("");
    try {
      await createBlock({ blockedUserId });
      onBlocked()
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t("blockModal.failed"))
    } finally {
      setSubmitting(false)
    }
  }
  return createPortal(<div
    className="fixed inset-0 z-50 flex items-center justify-center bg-text/40 p-4"
    onMouseDown={onClose}
  >
    {/* 내용 영역 — 상위 조회 결과·선택 상태 표시 */}
    <section
      role="dialog"
      aria-modal="true"
      onMouseDown={(event) => event.stopPropagation()}
      className="w-full max-w-md rounded-lg border border-main/15 bg-white p-6 text-center"
    >
      <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-accent/20 text-main">
        <Icon
          name="lock"
          size={22}
        />
      </span>
      <h2 className="mt-4 text-xl font-semibold text-main">
        {t("blockModal.title", { name })}
      </h2>
      <p className="mt-3 text-sm leading-6 text-text/70">
        {t("blockModal.description")}
      </p>
      {error && <p className="mt-3 text-sm font-semibold text-accent">
        {error}
      </p>}
      <footer className="mt-6 flex justify-center gap-2">
        {/* 취소 버튼 — onClose (화면 상태·상위 콜백 반영) */}
        <Button
          variant="secondary"
          onClick={onClose}
        >
          {t("common.cancel")}
        </Button>
        {/* 버튼 — handleBlock → POST /blocks */}
        <Button
          variant="danger"
          disabled={submitting}
          onClick={() => void handleBlock()}
        >
          {submitting ? t("common.processing") : t("blockModal.block")}
        </Button>
      </footer>
    </section>
  </div>, document.body)
}
