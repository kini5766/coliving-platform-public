// 버튼 (user-web 버튼과 같은 모양: rounded-lg · font-semibold · 포커스 시 accent 테두리)
// primary = 채운 버튼, outline = 흰 바탕 테두리 버튼
const VARIANTS = {
  primary: 'bg-primary text-background hover:bg-primary/90 disabled:opacity-60',
  outline: 'bg-surface text-ink border border-primary/25 hover:bg-primary/10 disabled:opacity-60',
  soft: 'bg-primary-soft text-ink hover:bg-soft',
};
const SIZES = {
  md: 'h-11 px-4 text-sm sm:text-base',
  sm: 'h-9 px-4 text-sm',
};

export default function Button({ variant = 'primary', size = 'md', className = '', ...props }) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-1 rounded-lg font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed ${VARIANTS[variant]} ${SIZES[size]} ${className}`}
      {...props}
    />
  );
}
