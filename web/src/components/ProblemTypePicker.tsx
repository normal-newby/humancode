import { PROBLEM_TYPES, type ProblemType } from '../api/types'

interface Props {
  value: ProblemType
  onChange: (type: ProblemType) => void
  disabled?: boolean
}

/** Chooses whether the session starts from a scaffold or a broken app. */
export function ProblemTypePicker({ value, onChange, disabled = false }: Props) {
  return (
    <fieldset className="mt-8" disabled={disabled}>
      <legend className="text-xs lowercase text-faint">what kind of problem</legend>
      <div className="mt-2 flex flex-wrap gap-x-5 gap-y-2">
        {PROBLEM_TYPES.map((type) => {
          const selected = type.value === value
          return (
            <label
              key={type.value}
              title={type.description}
              className={`cursor-pointer text-sm lowercase underline-offset-4 transition-colors ${
                selected ? 'text-accent underline' : 'text-sub hover:text-ink'
              } ${disabled ? 'cursor-default opacity-40' : ''}`}
            >
              <input
                type="radio"
                name="problem-type"
                value={type.value}
                checked={selected}
                onChange={() => onChange(type.value)}
                className="sr-only"
              />
              {type.label}
            </label>
          )
        })}
      </div>
      <p className="mt-2 text-xs text-faint">
        {PROBLEM_TYPES.find((type) => type.value === value)?.description}
      </p>
    </fieldset>
  )
}
