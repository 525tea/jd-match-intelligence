import React from 'react';
import { Bookmark, Check, ChevronRight, Eye, GitBranch, LockKeyhole, Pencil, Search, X } from 'lucide-react';
import { careerLabel, companyLogo, dday, jobIdOf, roleLabel, scoreOf } from '../utils/format.js';
import { tagLabels } from '../data/mock.js';

export const icons = { Bookmark, Check, ChevronRight, Eye, GitBranch, LockKeyhole, Pencil, Search, X };

export function Button({ children, variant = 'dark', className = '', ...props }) {
  return <button className={`btn btn-${variant} ${className}`} {...props}>{children}</button>;
}

export function Badge({ children, tone = 'neutral', icon, className = '' }) {
  return <span className={`badge badge-${tone} ${className}`}>{icon}{children}</span>;
}

export function Section({ eyebrow, title, desc, action, children, className = '' }) {
  return <section className={`section ${className}`}>
    <div className="section-head">
      <div>{eyebrow && <p className="eyebrow">{eyebrow}</p>}<h2>{title}</h2>{desc && <p>{desc}</p>}</div>
      {action}
    </div>
    {children}
  </section>;
}

export function ApiState({ loading, error, fromMock }) {
  if (loading) return <span className="api-state">불러오는 중</span>;
  if (fromMock || error) return <span className="api-state api-state-soft">mock fallback</span>;
  return <span className="api-state api-state-live">live API</span>;
}

export function EmptyState({ title, desc, action }) {
  return <div className="empty-state"><strong>{title}</strong><p>{desc}</p>{action}</div>;
}

export function JobCard({ job, onOpen, onSave, compact = false, showScore = true }) {
  const id = jobIdOf(job);
  const matched = [...(job.matchedRequiredSkills || []), ...(job.matchedPreferredSkills || [])];
  const missing = [...(job.missingRequiredSkills || []), ...(job.missingPreferredSkills || [])];
  const rawSkills = (job.skills || []).map((s) => s.skillName || s.name || s).filter(Boolean);
  const skillNames = [...new Set([...matched, ...rawSkills, ...missing])].slice(0, 4);
  const hiddenSkillCount = Math.max(0, [...new Set([...matched, ...rawSkills, ...missing])].length - skillNames.length);
  const matchedTags = (job.matchedExperienceTags || []).map((t) => t.tagName || tagLabels[t.tagCode] || t.tagCode);
  const missingTags = (job.missingExperienceTags || []).map((t) => t.tagName || tagLabels[t.tagCode] || t.tagCode);
  const rawTags = (job.experienceTags || []).map((t) => t.tagName || tagLabels[t.tagCode] || t.tagCode).filter(Boolean);
  const tagNames = [...new Set([...matchedTags, ...rawTags, ...missingTags])].slice(0, 2);
  const score = scoreOf(job);

  return <article className={`job-card ${compact ? 'job-card-compact' : ''}`} onClick={() => onOpen?.(id)}>
    <div className="job-card-top">
      <div className="company-mark">{job.logo || companyLogo(job.companyName)}</div>
      <div className="job-title-block">
        <div className="company-name">{job.companyName}</div>
        <h3>{job.title}</h3>
        <p>{roleLabel(job.role)} · {careerLabel(job.careerLevel, job.minExperienceYears, job.maxExperienceYears)}</p>
      </div>
      <div className="card-actions">
        <Badge tone="coral">{dday(job.deadlineAt)}</Badge>
        <button className="icon-btn" onClick={(e) => { e.stopPropagation(); onSave?.(id); }} aria-label="저장"><Bookmark size={17} /></button>
      </div>
    </div>
    <div className="skill-row">
      {skillNames.map((skill) => <Badge key={skill} tone={matched.includes(skill) || !missing.includes(skill) ? 'skill' : 'muted'} icon={matched.includes(skill) ? <Check size={13} /> : null}>{skill}</Badge>)}
      {hiddenSkillCount > 0 && <Badge tone="muted">+{hiddenSkillCount}</Badge>}
    </div>
    <div className="tag-row">
      {tagNames.map((tag) => <Badge key={tag} tone={missingTags.includes(tag) ? 'tag-muted' : 'tag'}>#{tag}</Badge>)}
    </div>
    <div className="job-card-bottom">
      {showScore && <div className="score"><b>{score || '-'}</b><span>%</span><small>레포 매칭</small></div>}
      <div className="views"><Eye size={15} />{job.views || Math.max(80, Number(id || 1) * 7 % 540)}</div>
    </div>
  </article>;
}

export function JobListRow({ job, onOpen, onSave }) {
  return <div className="job-row" onClick={() => onOpen?.(jobIdOf(job))}>
    <JobCard job={job} onOpen={onOpen} onSave={onSave} compact />
  </div>;
}

export function GithubConnectedIcon({ size = 28 }) {
  return <span className="github-connected" style={{ width: size, height: size }}><GitBranch size={Math.round(size * 0.62)} /><span><Check size={9} strokeWidth={4} /></span></span>;
}

export function ProgressBar({ value = 0, tone = 'lime' }) {
  return <div className={`progress progress-${tone}`}><span style={{ width: `${Math.max(0, Math.min(100, Number(value) || 0))}%` }} /></div>;
}

export function Modal({ title, children, onClose }) {
  return <div className="modal-backdrop" onClick={onClose}><div className="modal" onClick={(e) => e.stopPropagation()}><button className="modal-close" onClick={onClose}><X size={18} /></button><h3>{title}</h3>{children}</div></div>;
}
