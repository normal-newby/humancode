export type SessionLanguage = 'javascript' | 'python'

const LANGUAGES: { value: SessionLanguage; label: string; description: string }[] = [
  { value: 'javascript', label: 'web', description: 'html, css, and javascript' },
  { value: 'python', label: 'python', description: 'python 3 and the standard library' },
]

interface Props {
  value: SessionLanguage
  onChange: (language: SessionLanguage) => void
  disabled?: boolean
}

/** Selects the runtime before a session is generated or drawn from the bank. */
export function LanguagePicker({ value, onChange, disabled = false }: Props) {
  return (
    <fieldset className="mt-8" disabled={disabled}>
      <legend className="text-xs lowercase text-faint">what are you writing</legend>
      <div className="mt-2 flex flex-wrap gap-x-5 gap-y-2">
        {LANGUAGES.map((language) => {
          const selected = language.value === value
          return (
            <label
              key={language.value}
              className={`cursor-pointer text-sm lowercase underline-offset-4 transition-colors ${
                selected ? 'text-accent underline' : 'text-sub hover:text-ink'
              } ${disabled ? 'cursor-default opacity-40' : ''}`}
            >
              <input
                type="radio"
                name="language"
                value={language.value}
                checked={selected}
                onChange={() => onChange(language.value)}
                className="sr-only"
              />
              {language.label}
            </label>
          )
        })}
      </div>
      <p className="mt-2 text-xs text-faint">
        {LANGUAGES.find((language) => language.value === value)?.description}
      </p>
    </fieldset>
  )
}
