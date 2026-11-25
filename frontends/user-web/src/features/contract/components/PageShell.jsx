// 좌측 본문 + 우측 신청 정보(sticky) 2단 레이아웃
export default function PageShell({ main, aside, title = '신청 정보' }) {
  return (
    <div className="grid gap-8 px-4 py-6 sm:px-8 sm:py-8 md:px-12 md:py-10 lg:grid-cols-[1fr_360px]">
      <div className="space-y-8">{main}</div>
      <aside className="space-y-3 self-start lg:sticky lg:top-6">
        <h2 className="text-xl font-semibold text-main">{title}</h2>
        {aside}
      </aside>
    </div>
  );
}
