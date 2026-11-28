import {
  forwardRef,
  useId,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'link';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
}

export function Button({ variant = 'primary', loading = false, disabled, className, children, type, ...rest }: ButtonProps) {
  const classes = ['m-btn', 'm-btn--' + variant, className].filter(Boolean).join(' ');
  return (
    <button
      {...rest}
      type={type ?? 'button'}
      className={classes}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading ? <span className="m-spinner m-spinner--inline" aria-hidden="true" /> : null}
      {children}
    </button>
  );
}

interface FieldFrameProps {
  id: string;
  label: ReactNode;
  error?: string;
  hint?: ReactNode;
  required?: boolean;
  children: ReactNode;
}

function FieldFrame({ id, label, error, hint, required, children }: FieldFrameProps) {
  return (
    <div className={'m-field' + (error ? ' m-field--error' : '')}>
      <label className="m-field__label" htmlFor={id}>
        {label}
        {required ? (
          <span className="m-field__required" aria-hidden="true">
            {' *'}
          </span>
        ) : null}
      </label>
      {children}
      {hint ? (
        <p className="m-field__hint" id={id + '-hint'}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="m-field__error" id={id + '-error'} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function describedBy(id: string, error?: string, hint?: ReactNode): string | undefined {
  const ids = [hint ? id + '-hint' : '', error ? id + '-error' : ''].filter(Boolean).join(' ');
  return ids || undefined;
}

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: ReactNode;
  error?: string;
  hint?: ReactNode;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, required, className, ...rest },
  ref,
) {
  const id = useId();
  return (
    <FieldFrame id={id} label={label} error={error} hint={hint} required={required}>
      <input
        {...rest}
        ref={ref}
        id={id}
        required={required}
        className={['m-input', className].filter(Boolean).join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
      />
    </FieldFrame>
  );
});

export interface TextAreaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: ReactNode;
  error?: string;
  hint?: ReactNode;
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(function TextAreaField(
  { label, error, hint, required, className, ...rest },
  ref,
) {
  const id = useId();
  return (
    <FieldFrame id={id} label={label} error={error} hint={hint} required={required}>
      <textarea
        {...rest}
        ref={ref}
        id={id}
        required={required}
        className={['m-input', 'm-textarea', className].filter(Boolean).join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
      />
    </FieldFrame>
  );
});

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id'> {
  label: ReactNode;
  options: SelectOption[];
  placeholder?: string;
  error?: string;
  hint?: ReactNode;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, options, placeholder, error, hint, required, className, ...rest },
  ref,
) {
  const id = useId();
  return (
    <FieldFrame id={id} label={label} error={error} hint={hint} required={required}>
      <select
        {...rest}
        ref={ref}
        id={id}
        required={required}
        className={['m-input', 'm-select', className].filter(Boolean).join(' ')}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
      >
        {placeholder !== undefined ? <option value="">{placeholder}</option> : null}
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </FieldFrame>
  );
});

export interface CheckboxFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  label: ReactNode;
  error?: string;
}

export const CheckboxField = forwardRef<HTMLInputElement, CheckboxFieldProps>(function CheckboxField(
  { label, error, className, ...rest },
  ref,
) {
  const id = useId();
  return (
    <div className={['m-check', className].filter(Boolean).join(' ')}>
      <input
        {...rest}
        ref={ref}
        id={id}
        type="checkbox"
        className="m-check__input"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? id + '-error' : undefined}
      />
      <label htmlFor={id} className="m-check__label">
        {label}
      </label>
      {error ? (
        <p className="m-field__error" id={id + '-error'} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
});

export interface FileFieldProps {
  label: ReactNode;
  accept: string;
  hint?: ReactNode;
  disabled?: boolean;
  capture?: 'user' | 'environment';
  onFile: (file: File) => void;
}

/** Selector de archivo accesible; el valor se limpia para permitir elegir el mismo archivo otra vez. */
export function FileField({ label, accept, hint, disabled, capture, onFile }: FileFieldProps) {
  const id = useId();
  return (
    <FieldFrame id={id} label={label} hint={hint}>
      <input
        id={id}
        type="file"
        className="m-file"
        accept={accept}
        capture={capture}
        disabled={disabled}
        aria-describedby={hint ? id + '-hint' : undefined}
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file) onFile(file);
        }}
      />
    </FieldFrame>
  );
}
