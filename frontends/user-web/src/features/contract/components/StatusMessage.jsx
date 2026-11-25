import Card from './Card';

// 로딩·에러 안내 (화면 본문 자리에 표시)
export default function StatusMessage({ title, description, action }) {
  return (
    <div className="px-4 py-6 sm:px-8 sm:py-8 md:px-12 md:py-10">
      <Card className="flex flex-col items-center gap-3 px-6 py-16 text-center">
        <p className="text-lg font-semibold text-ink">{title}</p>
        {description && <p className="text-sm text-sub">{description}</p>}
        {action && <div className="mt-3">{action}</div>}
      </Card>
    </div>
  );
}
