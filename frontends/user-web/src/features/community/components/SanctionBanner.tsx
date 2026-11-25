// 제재 안내 배너 — 이용 제한 사유와 기간 표시
import type { MySanction } from "../types"
import { useTranslation } from "react-i18next"
import { Button, Icon } from "."
export function SanctionBanner({ sanction }: { sanction: MySanction }) {
  const { t, i18n } = useTranslation("community");
  if (!sanction.active) return null;
  const release = sanction.sanctionType === "SUSPENSION" || !sanction.endsAt ? t("sanction.untilAdmin") : t("sanction.releaseAt", {
    date: new Date(sanction.endsAt).toLocaleString(i18n.resolvedLanguage ?? i18n.language, {
      month: "long",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false
    })
  });
  // 내용 영역 — 상위 조회 결과·선택 상태 표시
  return <section id="community-sanction" role="status" className="mb-5 flex scroll-mt-4 flex-col gap-3 rounded-lg border border-main/15 bg-accent/20 p-4 sm:flex-row sm:items-center">
    <span className="text-main">
      <Icon
        name="lock"
        size={20}
      />
    </span>
    <div className="flex-1">
      <strong className="text-sm text-text">
        {t("sanction.title", { release })}
      </strong>
      <p className="mt-1 text-sm text-text/70">
        {sanction.reason}
      </p>
      <small className="text-text/70">
        {t("sanction.available")}
      </small>
    </div>
    {/* sanction.contact 버튼 — window.dispatchEvent (화면 상태·상위 콜백 반영) */}
    <Button
      variant="secondary"
      onClick={() => window.dispatchEvent(new CustomEvent("roomlink:open-chat", { detail: { tab: "notes" } }))}
    >
      {t("sanction.contact")}
    </Button>
  </section>
}
