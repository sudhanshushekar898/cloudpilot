/**
 * Renders a search snippet where the server marked matches as [[word]]. Text is rendered as
 * React text nodes, never as HTML, so runbook content can't inject markup.
 */
export default function Snippet({ text }: { text: string }) {
  const parts = text.split(/(\[\[.*?\]\])/g)
  return (
    <>
      {parts.map((part, index) =>
        part.startsWith('[[') && part.endsWith(']]') ? (
          <mark key={index} className="rounded bg-amber-100 px-0.5 text-inherit">{part.slice(2, -2)}</mark>
        ) : (
          <span key={index}>{part}</span>
        ),
      )}
    </>
  )
}
