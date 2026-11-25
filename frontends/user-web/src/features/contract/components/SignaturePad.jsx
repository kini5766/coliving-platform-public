import { useEffect, useImperativeHandle, useRef } from 'react';

// 전자 서명 캔버스. 서명이 생기면 onChange(true), 지우면 onChange(false)
// 부모는 ref로 toBase64()를 불러 서명 PNG를 받는다 ("data:image/png;base64," 앞부분 제외)
export default function SignaturePad({ ref: handle, onChange, clearSignal }) {
  const ref = useRef(null);
  const drawing = useRef(false);

  useImperativeHandle(handle, () => ({
    toBase64: () => ref.current.toDataURL('image/png').split(',')[1],
  }));

  useEffect(() => {
    const c = ref.current;
    const ratio = window.devicePixelRatio || 1;
    c.width = c.offsetWidth * ratio;
    c.height = c.offsetHeight * ratio;
    const ctx = c.getContext('2d');
    ctx.scale(ratio, ratio);
    ctx.lineWidth = 2.2;
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#26332E'; // user-web --color-text
  }, []);

  useEffect(() => {
    const c = ref.current;
    c.getContext('2d').clearRect(0, 0, c.width, c.height);
    onChange(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clearSignal]);

  const pos = (e) => {
    const r = ref.current.getBoundingClientRect();
    return [e.clientX - r.left, e.clientY - r.top];
  };
  const down = (e) => {
    drawing.current = true;
    const ctx = ref.current.getContext('2d');
    ctx.beginPath();
    ctx.moveTo(...pos(e));
  };
  const move = (e) => {
    if (!drawing.current) return;
    const ctx = ref.current.getContext('2d');
    ctx.lineTo(...pos(e));
    ctx.stroke();
    onChange(true);
  };
  const up = () => (drawing.current = false);

  return (
    <canvas
      ref={ref}
      onPointerDown={down}
      onPointerMove={move}
      onPointerUp={up}
      onPointerLeave={up}
      className="h-48 w-full touch-none rounded-lg border border-dashed border-line bg-surface"
    />
  );
}
