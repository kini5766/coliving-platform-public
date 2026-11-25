import { useEffect } from 'react';
import Button from './Button';

// 약관 "전문보기" 팝업. 하단 버튼을 누르면 해당 약관에 동의 처리하고 닫힌다 (onAgree)
function Block({ block }) {
  switch (block.type) {
    case 'h':
      return <h3 className="mt-5 text-sm font-semibold text-ink first:mt-0">{block.text}</h3>;
    case 'p':
      return <p className="mt-2 text-sm leading-relaxed text-sub">{block.text}</p>;
    case 'ol':
      return (
        <ol className="mt-2 list-decimal space-y-1.5 pl-5 text-sm leading-relaxed text-sub">
          {block.items.map((it, i) =>
            typeof it === 'string' ? (
              <li key={i}>{it}</li>
            ) : (
              <li key={i}>
                {it.text}
                <ol className="mt-1 list-[lower-alpha] space-y-1 pl-5">
                  {it.items.map((sub, j) => (
                    <li key={j}>{sub}</li>
                  ))}
                </ol>
              </li>
            ),
          )}
        </ol>
      );
    case 'table':
      return (
        <div className="mt-2 overflow-x-auto">
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr>
                {block.head.map((h) => (
                  <th key={h} className="border border-line bg-soft px-3 py-2 font-medium text-ink">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, i) => (
                <tr key={i}>
                  {row.map((cell, j) => (
                    <td key={j} className="border border-line px-3 py-2 text-sub">
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    default:
      return null;
  }
}

export default function TermsModal({ doc, onClose, onAgree }) {
  // Esc로 닫기 + 뒤 화면 스크롤 막기
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center sm:p-6"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="terms-modal-title"
        className="flex max-h-[85vh] w-full max-w-2xl flex-col rounded-t-2xl bg-surface shadow-xl sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-line px-5 py-4">
          <h2 id="terms-modal-title" className="text-base font-semibold text-ink">
            {doc.title}
          </h2>
          <button type="button" onClick={onClose} aria-label="닫기" className="text-xl leading-none text-sub hover:text-ink">
            ×
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">
          {doc.blocks.map((b, i) => (
            <Block key={i} block={b} />
          ))}
        </div>
        <div className="border-t border-line px-5 py-4">
          <Button className="w-full" onClick={onAgree}>
            확인하고 동의했습니다
          </Button>
        </div>
      </div>
    </div>
  );
}
