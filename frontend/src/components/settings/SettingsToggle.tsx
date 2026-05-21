import type { ReactNode } from 'react';

type SettingsToggleProps = {
    checked: boolean;
    onChange: (checked: boolean) => void;
    label: string;
    description?: string;
    icon?: ReactNode;
    disabled?: boolean;
};

export function SettingsToggle({
    checked,
    onChange,
    label,
    description,
    icon,
    disabled,
}: SettingsToggleProps) {
    return (
        <div className="settings-toggle-row">
            {icon ? <span className="settings-toggle-row__icon" aria-hidden>{icon}</span> : null}
            <div className="settings-toggle-row__text">
                <span className="settings-toggle-row__label">{label}</span>
                {description ? <span className="settings-toggle-row__desc">{description}</span> : null}
            </div>
            <button
                type="button"
                role="switch"
                aria-checked={checked}
                aria-label={label}
                disabled={disabled}
                className={`settings-toggle${checked ? ' is-on' : ''}`}
                onClick={() => onChange(!checked)}
            >
                <span className="settings-toggle__knob" />
            </button>
        </div>
    );
}
