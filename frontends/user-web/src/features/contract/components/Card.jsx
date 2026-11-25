export default function Card({ className = '', children }) {
  return <div className={`rounded-lg border border-line bg-surface ${className}`}>{children}</div>;
}
