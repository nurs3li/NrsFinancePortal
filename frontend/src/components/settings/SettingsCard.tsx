import type { ReactNode } from 'react';

type SettingsCardProps = {
    title: string;
    subtitle?: string;
    action?: ReactNode;
    children: ReactNode;
    className?: string;
};

export function SettingsCard({ title, subtitle, action, children, className = '' }: SettingsCardProps) {
    return (
        <section className={`settings-card ${className}`.trim()}>
            <header className="settings-card__head">
                <div className="settings-card__head-text">
                    <h2 className="settings-card__title">{title}</h2>
                    {subtitle ? <p className="settings-card__subtitle">{subtitle}</p> : null}
                </div>
                {action ? <div className="settings-card__action">{action}</div> : null}
            </header>
            <div className="settings-card__body">{children}</div>
        </section>
    );
}
