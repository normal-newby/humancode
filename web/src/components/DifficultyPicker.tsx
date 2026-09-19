import { DIFFICULTIES, type Difficulty } from '../api/types'

interface Props {
  value: Difficulty
  onChange: (difficulty: Difficulty) => void
  disabled?: boolean
}

/**
 * What to ask them for (UI-DESIGN.md §4.8).
 *
 * <p>Three lowercase words, not pills and not a dropdown: §2 bans the coloured
 * difficulty badge specifically because it is the single most LeetCode thing a
 * screen can wear. The chosen one is `--color-accent` and underlined; the rest
 * are `--color-sub`. Radios under the hood, so arrow keys work and a screen
 * reader gets a labelled group rather than three unrelated buttons.
 */
export function DifficultyPicker({ value, onChange, disabled = false }: Props) {
  return (
    <fieldset className="mt-8" disabled={disabled}>
      <legend className="text-xs lowercase text-faint">how hard should this be</legend>
      <div className="mt-2 flex gap-5">
        {DIFFICULTIES.map((difficulty) => {
          const selected = difficulty === value
          return (
            <label
              key={difficulty}
              className={`cursor-pointer text-sm lowercase underline-offset-4 transition-colors ${
                selected ? 'text-accent underline' : 'text-sub hover:text-ink'
              } ${disabled ? 'cursor-default opacity-40' : ''}`}
            >
              <input
                type="radio"
                name="difficulty"
                value={difficulty}
                checked={selected}
                onChange={() => onChange(difficulty)}
                className="sr-only"
              />
              {difficulty}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
