export function handleEnterKey(
  e: React.KeyboardEvent,
  callback: () => void,
) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    callback();
  }
}