export default function Section({ title, children, className = '' }) {
  return (
    <section className={className}>
      <h2 className="mb-3 text-xl font-semibold text-main">{title}</h2>
      {children}
    </section>
  );
}
