// ---------------------------------------------------------------
// 계약서 본문 보기 (서명 화면 · 나의 신청)
//
// 계약서는 문서 서비스 관리자 편집기로 만든 HTML 양식에 신청 정보를 채운 것이다.
// 신청자가 입력한 값이 들어 있으므로 화면에 직접 넣지 않고, 격리된 iframe 안에서만 그린다 (관리자 화면과 같은 방식).
//   sandbox=""  : 스크립트·폼·링크 이동 차단, 이 화면과 다른 출처로 취급
//   CSP 메타    : 외부 이미지·글꼴·스타일 파일도 불러오지 못하게 막는다 (본문 안 <style>만 허용)
// 예전 일반 텍스트 계약서(태그 없음)는 줄바꿈을 살려서 글자 그대로 보여준다.
// ---------------------------------------------------------------
const CSP = `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:">`;

// 관리자 화면(admin-web ContractReviewPage PREVIEW_STYLE)과 같은 모양
const STYLE = `<style>
body{margin:0;padding:16px 20px;font-family:'Pretendard','Noto Sans KR',system-ui,sans-serif;font-size:14px;line-height:1.7;color:#1f2a27;word-break:keep-all;overflow-wrap:anywhere}
h1{font-size:22px;margin:0 0 12px}h2{font-size:18px;margin:16px 0 8px}h3{font-size:16px;margin:12px 0 6px}
p{margin:0 0 8px}ul{list-style:disc;padding-left:24px}ol{list-style:decimal;padding-left:24px}
table{width:100%;border-collapse:collapse}th,td{border:1px solid #cbd5d0;padding:6px 10px;text-align:left}th{background:#f3f5f1}
blockquote{margin:8px 0;padding-left:12px;border-left:3px solid #52796f;color:#5b6b65}hr{border:0;border-top:1px solid #cbd5d0}
pre{white-space:pre-wrap;font-family:inherit;margin:0}
</style>`;

const looksLikeHtml = (s) => /<[a-zA-Z/!][^>]*>/.test(s);
const escapeText = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

export default function ContractDocumentView({ content, title = '계약서 본문', className = 'h-80' }) {
  const text = content ?? '';
  const body = looksLikeHtml(text) ? text : `<pre>${escapeText(text)}</pre>`;
  return (
    <iframe
      title={title}
      sandbox=""
      srcDoc={`<!doctype html><html><head><meta charset="utf-8">${CSP}${STYLE}</head><body>${body}</body></html>`}
      className={`block w-full rounded-lg border border-line bg-white ${className}`}
    />
  );
}
