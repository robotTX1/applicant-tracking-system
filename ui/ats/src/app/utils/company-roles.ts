import { CompanyRole } from '../api';

export interface RoleInfo {
  label: string;
  description: string;
}

export const ROLE_INFO: Record<CompanyRole, RoleInfo> = {
  [CompanyRole.Owner]: {
    label: 'Owner',
    description: 'Full access, including deleting the company. Every company has one owner.',
  },
  [CompanyRole.CoOwner]: {
    label: 'Co-owner',
    description:
      'Everything the owner can do except deleting the company, including managing the team.',
  },
  [CompanyRole.HrManager]: {
    label: 'HR manager',
    description:
      'Posts and edits jobs, manages email templates, moves candidates and gives HR feedback.',
  },
  [CompanyRole.TechnicalInterviewer]: {
    label: 'Technical interviewer',
    description: 'Sees candidates in the technical stages and gives technical feedback.',
  },
};

/** Roles that can be given to a member (the owner role can't be assigned). */
export const ASSIGNABLE_ROLES: readonly CompanyRole[] = [
  CompanyRole.CoOwner,
  CompanyRole.HrManager,
  CompanyRole.TechnicalInterviewer,
];

/** MANAGE_EMPLOYEES: owners and co-owners. */
export function canManageTeam(role: CompanyRole | null | undefined): boolean {
  return role === CompanyRole.Owner || role === CompanyRole.CoOwner;
}
