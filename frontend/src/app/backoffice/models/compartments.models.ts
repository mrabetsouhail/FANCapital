/** Architecture des Compartiments (La Matrice) */
export interface MatriceInfo {
  compartmentsRegistry: string | null;
  piscineA: string | null;
  piscineB: string | null;
  piscineC: string | null;
  piscineD: string | null;
  guaranteeFundBps: number;
  /** Soldes TND (1e8) par piscine */
  balancePiscineATnd?: string | null;
  balancePiscineBTnd?: string | null;
  balancePiscineCTnd?: string | null;
  balancePiscineDTnd?: string | null;
}

export const COMPARTMENT_LABELS: Record<string, { name: string; description: string; color: string }> = {
  A: { name: 'Réserve de Liquidité', description: 'Capital des investisseurs (somme Atlas + Didon + toutes les pools)', color: 'bg-blue-50 border-blue-200' },
  B: { name: 'Sas Partenaires', description: 'Zone de transit (D17, Flouci, virements)', color: 'bg-amber-50 border-amber-200' },
  C: { name: 'Compte de Revenus', description: 'Intérêts AST, frais, spread', color: 'bg-green-50 border-green-200' },
  D: { name: 'Fonds de Garantie', description: 'Bad debt, couverture des pertes AST', color: 'bg-red-50 border-red-200' },
};
