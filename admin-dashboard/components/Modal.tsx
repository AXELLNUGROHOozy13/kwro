'use client';

import { useEffect } from 'react';

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="glass" role="dialog" aria-modal="true" aria-label={title}>
        <div className="flex items-center justify-between gap-3 mb-4">
          <h2 className="h-sec">{title}</h2>
          <button className="btn btn-sm" onClick={onClose} aria-label="Tutup">Tutup</button>
        </div>
        {children}
      </div>
    </div>
  );
}
