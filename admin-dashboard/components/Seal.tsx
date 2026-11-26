/** The KAWREXAM seal: a monogram with a brass ring. Decorative, so hidden from screen readers. */
export function Seal({ large = false }: { large?: boolean }) {
  return <span className={`seal${large ? ' seal-lg' : ''}`} aria-hidden="true">KRX</span>;
}
